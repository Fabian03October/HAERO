import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { AlmacenApi, Caducado, Canje, DevolucionPrestamoRequest, ExpedienteExterno, Movimiento, mensajeDeError, ultimoDiaDelMes } from './almacen-api';
import { InventarioAlmacen, LoteInventario } from './inventario-almacen';
import { SolicitudesAlmacen, estaVencido, formatoCaducidad, mesActual } from './solicitudes-almacen';
import { RotacionAlmacen } from './rotacion-almacen';

// Un lote se puede canjear cuando le quedan menos de estos meses de vida (HU18).
export const MESES_PARA_CANJE = 9;

export type TipoExterno = 'PRESTAMO' | 'TRANSFERENCIA';
export type SentidoExterno = 'SALIDA' | 'ENTRADA';

// Lote agrupado con todas sus ubicaciones.
export interface LoteAgrupado {
  loteId: number;
  clave: string;
  lote: string;
  caducidad: string;
  proveedor: string;
  cajas: number;
  ubicaciones: { ubicacion: string; cajas: number }[];
  mesesDeVida: number;
  vencido: boolean;
}

// Enlace lote origen → lote nuevo (HU18 CA3), tal como lo devuelve el backend.
export type { Canje } from './almacen-api';

// Préstamo o transferencia con otra institución (HU20).
export interface MovimientoExterno {
  fecha: string;
  usuario: string;
  tipo: TipoExterno;
  sentido: SentidoExterno;
  institucion: string;
  clave: string;
  lote: string;
  caducidad: string;
  ubicacion: string;
  cajas: number;
}

export interface DatosCanje {
  loteOrigenId: number;
  loteNuevo: string;
  caducidadNueva: string;
  cajasNuevas: number;
  ubicacion: string;
}

export interface DatosExterno {
  tipo: TipoExterno;
  sentido: SentidoExterno;
  institucion: string;
  clave: string;
  // Salida: existencia (lote y ubicación) de donde sale.
  existenciaId?: number;
  // Entrada: lote que se recibe.
  lote: string;
  caducidad: string;
  ubicacion: string;
  cajas: number;
  // Préstamo: fecha comprometida de devolución (AAAA-MM-DD).
  fechaLimite: string;
  observaciones: string;
}

// Devolución de un préstamo, en sentido contrario al préstamo.
export interface DatosDevolucion {
  cajas: number;
  // Si nos prestaron: existencia de donde sale lo que devolvemos.
  existenciaId?: number;
  // Si prestamos: lote que nos regresan.
  lote: string;
  caducidad: string;
  ubicacion: string;
}

export type { ExpedienteExterno } from './almacen-api';

// Tamaño máximo del PDF; el backend aplica el mismo límite.
export const TAMANO_MAXIMO_PDF = 10 * 1024 * 1024;

// Movimientos especiales (HU18, HU19, HU20): lo que no es una entrada por pedido
// ni una salida a Farmacia. Se guardan en el backend; cada uno deja su movimiento
// con su tipo, así el CPM no los cuenta (HU16 CA4). Los métodos devuelven null si
// todo salió bien o el texto del error para mostrarlo en pantalla.
@Injectable({ providedIn: 'root' })
export class MovimientosEspecialesAlmacen {
  private readonly api = inject(AlmacenApi);
  private readonly inventario = inject(InventarioAlmacen);
  private readonly solicitudes = inject(SolicitudesAlmacen);
  private readonly rotacion = inject(RotacionAlmacen);

  private readonly _canjes = signal<Canje[]>([]);
  private readonly _caducados = signal<Caducado[]>([]);
  private readonly _porApartar = signal<Caducado[]>([]);
  private readonly _externos = signal<MovimientoExterno[]>([]);
  private readonly _expedientes = signal<ExpedienteExterno[]>([]);

  readonly canjes = this._canjes.asReadonly();
  readonly caducados = this._caducados.asReadonly();
  readonly externos = this._externos.asReadonly();
  // Préstamos y transferencias con su estatus y documentos, los más recientes primero.
  readonly expedientes = this._expedientes.asReadonly();

  readonly lotes = computed(() => agruparLotes(this.inventario.lotes()));
  readonly lotesParaCanje = computed(() => this.lotes().filter((lote) => !lote.vencido && lote.mesesDeVida < MESES_PARA_CANJE));
  // Sugerencia del backend para el despachador; nada se mueve sin su confirmación (HU19 CA4).
  readonly vencidosPorApartar = computed<LoteAgrupado[]>(() =>
    this._porApartar().map((lote) => ({
      loteId: lote.loteId,
      clave: lote.clave,
      lote: lote.numeroLote,
      caducidad: lote.caducidad,
      proveedor: lote.proveedor,
      cajas: lote.cajas,
      ubicaciones: lote.ubicaciones,
      mesesDeVida: mesesDeVida(lote.caducidad),
      vencido: true,
    })),
  );
  readonly cajasCaducadas = computed(() => this._caducados().reduce((total, lote) => total + lote.cajas, 0));

  constructor() {
    this.recargar();
  }

  /** Vuelve a leer del backend canjes, caducados (apartado y por apartar) y movimientos externos. */
  recargar(): void {
    this.api.listarCanjes().subscribe({ next: (lista) => this._canjes.set(lista), error: () => {} });
    this.api.listarCaducados(false).subscribe({ next: (lista) => this._caducados.set(lista), error: () => {} });
    this.api.listarCaducados(true).subscribe({ next: (lista) => this._porApartar.set(lista), error: () => {} });
    this.api.listarMovimientosExternos().subscribe({ next: (lista) => this._externos.set(lista.map(aExterno)), error: () => {} });
    this.api.listarExpedientesExternos().subscribe({ next: (lista) => this._expedientes.set(lista), error: () => {} });
  }

  /** HU18: el lote origen sale del inventario y el nuevo entra ligado a él. */
  async registrarCanje(datos: DatosCanje): Promise<string | null> {
    const origen = this.lotes().find((lote) => lote.loteId === datos.loteOrigenId);
    if (!origen) return 'El lote de origen ya no está en el inventario disponible.';
    if (origen.vencido) return 'El lote ya caducó. Muévelo al apartado de caducados en lugar de canjearlo.';
    if (origen.mesesDeVida >= MESES_PARA_CANJE) return `Solo se canjean lotes con menos de ${MESES_PARA_CANJE} meses de vida. Este tiene ${origen.mesesDeVida}.`;

    const loteNuevo = datos.loteNuevo.trim();
    const ubicacion = datos.ubicacion.trim();
    if (!loteNuevo || !datos.caducidadNueva || !ubicacion) return 'Completa número de lote, caducidad y ubicación del lote nuevo.';
    if (!Number.isInteger(datos.cajasNuevas) || datos.cajasNuevas <= 0) return 'La cantidad del lote nuevo debe ser un número entero mayor a cero.';
    if (loteNuevo.toLowerCase() === origen.lote.toLowerCase()) return 'El lote nuevo debe tener un número distinto al de origen.';
    const caducidadNueva = ultimoDiaDelMes(datos.caducidadNueva);
    if (caducidadNueva <= origen.caducidad) return `La caducidad del lote nuevo debe ser posterior a ${formatoCaducidad(origen.caducidad)}.`;
    if (this.inventario.lotes().some((lote) => lote.clave === origen.clave && lote.lote.toLowerCase() === loteNuevo.toLowerCase())) {
      return `El lote ${loteNuevo} ya existe en el inventario de esta clave.`;
    }

    return this.guardar(
      this.api.registrarCanje({ loteOrigenId: origen.loteId, numeroLote: loteNuevo, caducidad: caducidadNueva, ubicaciones: [{ ubicacion, cajas: datos.cajasNuevas }] }),
      'No se pudo registrar el canje.',
    );
  }

  /** HU19: aparta un lote vencido completo; se llama solo después de confirmar. */
  async moverACaducados(loteId: number): Promise<string | null> {
    if (!this.vencidosPorApartar().some((item) => item.loteId === loteId)) return 'El lote no está vencido o ya no está en el inventario disponible.';
    return this.guardar(this.api.moverACaducados(loteId), 'No se pudo mover el lote a caducados.');
  }

  /**
   * HU20: préstamo o transferencia con su PDF (oficio de solicitud o documento de
   * envío); sin el documento no se registra. Afecta la existencia del lote y la ubicación (CA2).
   */
  async registrarExterno(datos: DatosExterno, documento: File | null): Promise<string | null> {
    const institucion = datos.institucion.trim();
    const lote = datos.lote.trim();
    const ubicacion = datos.ubicacion.trim();
    const esPrestamo = datos.tipo === 'PRESTAMO';
    if (!institucion) return 'Indica la institución con la que se hizo el movimiento.';
    if (!datos.clave.trim()) return 'Indica la clave del medicamento.';
    if (!Number.isInteger(datos.cajas) || datos.cajas <= 0) return 'La cantidad debe ser un número entero mayor a cero.';
    if (esPrestamo && !datos.fechaLimite) return 'Indica la fecha límite de devolución del préstamo.';
    if (esPrestamo && datos.fechaLimite < hoy()) return 'La fecha límite de devolución no puede ser anterior a hoy.';
    const errorDocumento = validarPdf(documento, esPrestamo ? 'el oficio de solicitud del préstamo' : 'el documento de la transferencia');
    if (errorDocumento) return errorDocumento;

    const comunes = {
      tipo: datos.tipo,
      institucion,
      cajas: datos.cajas,
      fechaLimite: esPrestamo ? datos.fechaLimite : undefined,
      observaciones: datos.observaciones.trim() || undefined,
    };

    if (datos.sentido === 'SALIDA') {
      const fila = this.inventario.lotes().find((item) => item.existenciaId === datos.existenciaId);
      if (!fila) return 'Elige el lote y la ubicación de donde sale el medicamento.';
      if (estaVencido(fila.caducidad)) return 'No se presta ni se transfiere un lote vencido.';
      if (datos.cajas > fila.cajas) return `Existencia insuficiente: el lote ${fila.lote} en ${fila.ubicacion} solo tiene ${fila.cajas} cajas.`;
      return this.guardar(
        this.api.registrarExpedienteExterno({ ...comunes, sentido: 'SALIDA', existenciaId: fila.existenciaId }, documento!),
        'No se pudo registrar el movimiento.',
      );
    }

    const errorEntrada = this.validarEntrada(datos.clave.trim(), lote, datos.caducidad, ubicacion);
    if (errorEntrada) return errorEntrada;
    return this.guardar(
      this.api.registrarExpedienteExterno(
        { ...comunes, sentido: 'ENTRADA', clave: datos.clave.trim(), numeroLote: lote, caducidad: ultimoDiaDelMes(datos.caducidad), ubicacion },
        documento!,
      ),
      'No se pudo registrar el movimiento.',
    );
  }

  /** Devolución (total o parcial) de un préstamo; va en sentido contrario al préstamo. */
  async registrarDevolucion(expediente: ExpedienteExterno, datos: DatosDevolucion): Promise<string | null> {
    const pendientes = expediente.cajas - expediente.cajasDevueltas;
    if (!Number.isInteger(datos.cajas) || datos.cajas <= 0) return 'La cantidad devuelta debe ser un número entero mayor a cero.';
    if (datos.cajas > pendientes) return `Solo faltan ${pendientes} cajas por devolver en este préstamo.`;

    let request: DevolucionPrestamoRequest;
    if (expediente.sentido === 'ENTRADA') {
      // Nos prestaron: lo que devolvemos sale de una existencia nuestra.
      const fila = this.inventario.lotes().find((item) => item.existenciaId === datos.existenciaId);
      if (!fila) return 'Elige el lote y la ubicación de donde sale lo que se devuelve.';
      if (datos.cajas > fila.cajas) return `Existencia insuficiente: el lote ${fila.lote} en ${fila.ubicacion} solo tiene ${fila.cajas} cajas.`;
      request = { cajas: datos.cajas, existenciaId: fila.existenciaId };
    } else {
      // Prestamos: lo que nos regresan entra con su lote.
      const lote = datos.lote.trim();
      const ubicacion = datos.ubicacion.trim();
      const errorEntrada = this.validarEntrada(expediente.clave, lote, datos.caducidad, ubicacion);
      if (errorEntrada) return errorEntrada;
      request = { cajas: datos.cajas, numeroLote: lote, caducidad: ultimoDiaDelMes(datos.caducidad), ubicacion };
    }
    return this.guardar(this.api.registrarDevolucionPrestamo(expediente.id, request), 'No se pudo registrar la devolución.');
  }

  /** Préstamo: lo cierra con el PDF de cierre (ya devuelto todo). Transferencia: la confirma con el PDF de recepción. */
  async cerrarExpediente(expediente: ExpedienteExterno, documento: File | null): Promise<string | null> {
    const esPrestamo = expediente.tipo === 'PRESTAMO';
    if (esPrestamo && expediente.cajasDevueltas < expediente.cajas) {
      return `No se puede cerrar: faltan ${expediente.cajas - expediente.cajasDevueltas} cajas por devolver.`;
    }
    const errorDocumento = validarPdf(documento, esPrestamo ? 'el documento de cierre del préstamo' : 'el documento de recepción');
    if (errorDocumento) return errorDocumento;
    return this.guardar(this.api.cerrarExpedienteExterno(expediente.id, documento!), 'No se pudo guardar el documento.');
  }

  /** Abre el PDF en otra pestaña (se descarga con el token de la sesión). */
  async abrirDocumento(expedienteId: number, documentoId: number): Promise<string | null> {
    // La pestaña se abre antes de esperar la descarga para que el navegador no la bloquee.
    const pestana = window.open('', '_blank');
    try {
      const archivo = await firstValueFrom(this.api.descargarDocumentoExterno(expedienteId, documentoId));
      const url = URL.createObjectURL(archivo);
      if (pestana) pestana.location.href = url;
      else window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
      return null;
    } catch (error) {
      pestana?.close();
      return mensajeDeError(error, 'No se pudo abrir el documento.');
    }
  }

  private validarEntrada(clave: string, lote: string, caducidadMes: string, ubicacion: string): string | null {
    if (!lote || !caducidadMes || !ubicacion) return 'Completa lote, caducidad y ubicación de lo que se recibe.';
    const caducidad = ultimoDiaDelMes(caducidadMes);
    if (estaVencido(caducidad)) return 'No se puede recibir un lote que ya caducó.';
    const existente = this.inventario.lotes().find((item) => item.clave === clave && item.lote.toLowerCase() === lote.toLowerCase());
    if (existente && existente.caducidad !== caducidad) return `El lote ${lote} ya existe con caducidad ${formatoCaducidad(existente.caducidad)}. Revisa la caducidad.`;
    return null;
  }

  /** Envía al backend y, si sale bien, vuelve a leer todo lo que el movimiento cambió. */
  private async guardar(peticion: Observable<unknown>, porDefecto: string): Promise<string | null> {
    try {
      await firstValueFrom(peticion);
    } catch (error) {
      return mensajeDeError(error, porDefecto);
    }
    this.recargar();
    this.inventario.recargar();
    this.solicitudes.recargarSalidas();
    this.rotacion.recargar();
    return null;
  }
}

// Hoy como AAAA-MM-DD en hora local.
function hoy(): string {
  const fecha = new Date();
  return new Date(fecha.getTime() - fecha.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

/** null si el archivo es un PDF válido; si no, el texto del error. */
export function validarPdf(archivo: File | null, descripcion: string): string | null {
  if (!archivo) return `Adjunta ${descripcion} en PDF.`;
  const esPdf = archivo.type === 'application/pdf' || archivo.name.toLowerCase().endsWith('.pdf');
  if (!esPdf) return 'El archivo debe ser un PDF.';
  if (archivo.size > TAMANO_MAXIMO_PDF) return 'El PDF no puede pesar más de 10 MB.';
  return null;
}

function aExterno(movimiento: Movimiento): MovimientoExterno {
  return {
    fecha: movimiento.fecha,
    usuario: movimiento.usuario,
    tipo: movimiento.tipo as TipoExterno,
    sentido: (movimiento.sentido ?? 'SALIDA') as SentidoExterno,
    institucion: movimiento.institucion ?? '',
    clave: movimiento.clave,
    lote: movimiento.numeroLote,
    caducidad: movimiento.caducidad,
    ubicacion: movimiento.ubicacion,
    cajas: movimiento.cajas,
  };
}

// Meses completos entre el mes actual y la caducidad (negativo si ya venció).
export function mesesDeVida(caducidad: string): number {
  const [anio, mes] = caducidad.split('-').map(Number);
  const [anioHoy, mesHoy] = mesActual().split('-').map(Number);
  return (anio - anioHoy) * 12 + (mes - mesHoy);
}

function agruparLotes(filas: LoteInventario[]): LoteAgrupado[] {
  const grupos = new Map<string, LoteAgrupado>();
  for (const fila of filas) {
    const llave = String(fila.loteId ?? `${fila.clave}|${fila.lote}`);
    let grupo = grupos.get(llave);
    if (!grupo) {
      grupo = { loteId: fila.loteId ?? 0, clave: fila.clave, lote: fila.lote, caducidad: fila.caducidad, proveedor: fila.proveedor, cajas: 0, ubicaciones: [], mesesDeVida: mesesDeVida(fila.caducidad), vencido: estaVencido(fila.caducidad) };
      grupos.set(llave, grupo);
    }
    grupo.cajas += fila.cajas;
    grupo.ubicaciones.push({ ubicacion: fila.ubicacion, cajas: fila.cajas });
  }
  return [...grupos.values()].sort((a, b) => a.caducidad.localeCompare(b.caducidad) || a.clave.localeCompare(b.clave));
}
