import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { InventarioAlmacen } from '../../inventario-almacen';
import {
  DatosDevolucion,
  ExpedienteExterno,
  MovimientosEspecialesAlmacen,
  SentidoExterno,
  TipoExterno,
} from '../../movimientos-especiales-almacen';
import { EstatusExpediente, TipoDocumentoExterno } from '../../almacen-api';
import { SolicitudesAlmacen, estaVencido, formatoCaducidad } from '../../solicitudes-almacen';
import { Notificaciones } from '../../../shared/notificaciones/notificaciones';

type FiltroEstatus = '' | 'ABIERTOS' | 'VENCIDO' | 'POR_CERRAR' | 'TERMINADOS';

export const ESTATUS_EXPEDIENTE_TEXTO: Record<EstatusExpediente, string> = {
  ACTIVO: 'Activo',
  DEVUELTO_PARCIAL: 'Devuelto parcial',
  POR_CERRAR: 'Por cerrar',
  VENCIDO: 'Vencido',
  CERRADO: 'Cerrado',
  REGISTRADA: 'Registrada',
  CONFIRMADA: 'Confirmada',
};

const DOCUMENTO_TEXTO: Record<TipoDocumentoExterno, string> = {
  SOLICITUD: 'Oficio de solicitud',
  CIERRE: 'Documento de cierre',
  ENVIO: 'Documento de envío',
  RECEPCION: 'Documento de recepción',
};

const CUMPLE_ESTATUS: Record<Exclude<FiltroEstatus, ''>, (estatus: EstatusExpediente) => boolean> = {
  ABIERTOS: (estatus) => estatus === 'ACTIVO' || estatus === 'DEVUELTO_PARCIAL' || estatus === 'REGISTRADA',
  VENCIDO: (estatus) => estatus === 'VENCIDO',
  POR_CERRAR: (estatus) => estatus === 'POR_CERRAR',
  TERMINADOS: (estatus) => estatus === 'CERRADO' || estatus === 'CONFIRMADA',
};

// Movimientos especiales · Préstamos y transferencias (HU20, con expediente; en pruebas).
// Cada préstamo o transferencia se registra con su PDF y lleva un estatus:
// - Préstamo: oficio de solicitud al crearlo, devoluciones parciales o totales, y
//   documento de cierre cuando ya se devolvió todo. Se vence si pasa la fecha límite.
// - Transferencia (esqueleto): documento de envío al crearla y de recepción para confirmarla.
@Component({
  selector: 'app-movimientos-externos',
  imports: [FormsModule, DatePipe],
  templateUrl: './movimientos-externos.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../../despacho/despacho-comun.css', '../movimientos-comun.css', './movimientos-externos.css'],
  host: { '(document:keydown.escape)': 'cerrarDetalle()' },
})
export class MovimientosExternos {
  protected readonly movimientos = inject(MovimientosEspecialesAlmacen);
  protected readonly solicitudes = inject(SolicitudesAlmacen);
  private readonly inventario = inject(InventarioAlmacen);
  private readonly notificaciones = inject(Notificaciones);
  protected readonly formatoCaducidad = formatoCaducidad;
  protected readonly estatusTexto = ESTATUS_EXPEDIENTE_TEXTO;
  protected readonly documentoTexto = DOCUMENTO_TEXTO;
  protected readonly hoy = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);

  // ---------- Alta ----------

  readonly tipo = signal<TipoExterno>('PRESTAMO');
  readonly sentido = signal<SentidoExterno>('SALIDA');
  readonly clave = signal('');
  institucion = '';
  cajas = 0;
  fechaLimite = '';
  observaciones = '';
  // Salida: id de la existencia (lote y ubicación) elegida ('' = ninguna).
  existenciaElegida = '';
  // Entrada: datos de lo que se recibe.
  entrada = { lote: '', caducidad: '', ubicacion: '' };
  readonly documento = signal<File | null>(null);
  private readonly archivoAlta = viewChild<ElementRef<HTMLInputElement>>('archivoAlta');
  readonly guardando = signal(false);
  readonly mensaje = signal('');

  readonly existenciasDeClave = computed(() => this.existenciasDe(this.clave()));

  cambiarClave(valor: string): void {
    this.clave.set(valor);
    this.existenciaElegida = '';
  }

  elegirArchivo(evento: Event, destino: 'alta' | 'cierre'): void {
    const archivo = (evento.target as HTMLInputElement).files?.[0] ?? null;
    if (destino === 'alta') this.documento.set(archivo);
    else this.documentoCierre.set(archivo);
  }

  async registrar(): Promise<void> {
    const clave = this.clave().trim();
    if (clave && !this.solicitudes.clavesConocidas().includes(clave)) {
      this.mensaje.set('La clave no existe en el catálogo.');
      return;
    }
    this.guardando.set(true);
    const error = await this.movimientos.registrarExterno(
      {
        tipo: this.tipo(),
        sentido: this.sentido(),
        institucion: this.institucion,
        clave,
        existenciaId: this.existenciaElegida ? Number(this.existenciaElegida) : undefined,
        lote: this.entrada.lote,
        caducidad: this.entrada.caducidad,
        ubicacion: this.entrada.ubicacion,
        cajas: Number(this.cajas),
        fechaLimite: this.fechaLimite,
        observaciones: this.observaciones,
      },
      this.documento(),
    );
    this.guardando.set(false);
    if (error) {
      this.mensaje.set(error);
      return;
    }
    const esPrestamo = this.tipo() === 'PRESTAMO';
    const direccion = this.sentido() === 'SALIDA' ? 'a' : 'de';
    this.notificaciones.exito(
      esPrestamo ? 'Préstamo registrado' : 'Transferencia registrada',
      `${this.cajas} cajas ${this.sentido() === 'SALIDA' ? 'salieron' : 'entraron'} ${direccion} ${this.institucion.trim()}, con su documento guardado.`,
    );
    this.mensaje.set('');
    this.cajas = 0;
    this.fechaLimite = '';
    this.observaciones = '';
    this.existenciaElegida = '';
    this.entrada = { lote: '', caducidad: '', ubicacion: '' };
    this.documento.set(null);
    const input = this.archivoAlta()?.nativeElement;
    if (input) input.value = '';
  }

  // ---------- Lista ----------

  readonly filtroTipo = signal<'' | TipoExterno>('');
  readonly filtroEstatus = signal<FiltroEstatus>('');
  readonly busqueda = signal('');

  readonly filtrosEstatus = computed(() => {
    const lista = this.movimientos.expedientes().filter((item) => !this.filtroTipo() || item.tipo === this.filtroTipo());
    const contar = (filtro: Exclude<FiltroEstatus, ''>) => lista.filter((item) => CUMPLE_ESTATUS[filtro](item.estatusVigente)).length;
    return [
      { valor: '' as const, etiqueta: 'Todos', total: lista.length },
      { valor: 'ABIERTOS' as const, etiqueta: 'Abiertos', total: contar('ABIERTOS') },
      { valor: 'VENCIDO' as const, etiqueta: 'Vencidos', total: contar('VENCIDO') },
      { valor: 'POR_CERRAR' as const, etiqueta: 'Por cerrar', total: contar('POR_CERRAR') },
      { valor: 'TERMINADOS' as const, etiqueta: 'Cerrados', total: contar('TERMINADOS') },
    ];
  });

  readonly expedientes = computed(() => {
    const tipo = this.filtroTipo();
    const estatus = this.filtroEstatus();
    const texto = this.busqueda().trim().toLowerCase();
    return this.movimientos.expedientes().filter((item) => {
      if (tipo && item.tipo !== tipo) return false;
      if (estatus && !CUMPLE_ESTATUS[estatus](item.estatusVigente)) return false;
      if (!texto) return true;
      return `${this.folio(item)} ${item.institucion} ${item.clave} ${item.nombreGenerico} ${item.registradoPor}`.toLowerCase().includes(texto);
    });
  });

  folio(item: ExpedienteExterno): string {
    return `${item.tipo === 'PRESTAMO' ? 'PRE' : 'TRA'}-${String(item.id).padStart(4, '0')}`;
  }

  /** "Préstamo a Hospital X", "Préstamo de Hospital X" (nos prestaron), "Transferencia a/de ...". */
  direccion(item: ExpedienteExterno): string {
    const tipo = item.tipo === 'PRESTAMO' ? 'Préstamo' : 'Transferencia';
    return `${tipo} ${item.sentido === 'SALIDA' ? 'a' : 'de'}`;
  }

  // ---------- Detalle ----------

  readonly detalleId = signal<number | null>(null);
  readonly detalle = computed(() => this.movimientos.expedientes().find((item) => item.id === this.detalleId()) ?? null);
  readonly errorDetalle = signal('');
  readonly procesando = signal(false);
  devolucion: DatosDevolucion = { cajas: 0, lote: '', caducidad: '', ubicacion: '' };
  existenciaDevolucion = '';
  readonly documentoCierre = signal<File | null>(null);

  readonly existenciasParaDevolver = computed(() => {
    const item = this.detalle();
    return item ? this.existenciasDe(item.clave) : [];
  });

  verDetalle(item: ExpedienteExterno): void {
    this.detalleId.set(item.id);
    this.errorDetalle.set('');
    this.devolucion = { cajas: item.cajas - item.cajasDevueltas, lote: '', caducidad: '', ubicacion: '' };
    this.existenciaDevolucion = '';
    this.documentoCierre.set(null);
  }

  cerrarDetalle(): void {
    this.detalleId.set(null);
  }

  async registrarDevolucion(item: ExpedienteExterno): Promise<void> {
    this.procesando.set(true);
    const error = await this.movimientos.registrarDevolucion(item, {
      ...this.devolucion,
      cajas: Number(this.devolucion.cajas),
      existenciaId: this.existenciaDevolucion ? Number(this.existenciaDevolucion) : undefined,
    });
    this.procesando.set(false);
    if (error) {
      this.errorDetalle.set(error);
      return;
    }
    const cajas = Number(this.devolucion.cajas);
    const faltan = item.cajas - item.cajasDevueltas - cajas;
    this.notificaciones.exito('Devolución registrada', faltan > 0 ? `${cajas} cajas devueltas; faltan ${faltan}.` : `Se devolvieron todas las cajas. Falta subir el documento de cierre.`);
    this.errorDetalle.set('');
    this.devolucion = { cajas: Math.max(0, faltan), lote: '', caducidad: '', ubicacion: '' };
    this.existenciaDevolucion = '';
  }

  async cerrar(item: ExpedienteExterno): Promise<void> {
    this.procesando.set(true);
    const error = await this.movimientos.cerrarExpediente(item, this.documentoCierre());
    this.procesando.set(false);
    if (error) {
      this.errorDetalle.set(error);
      return;
    }
    this.notificaciones.exito(
      item.tipo === 'PRESTAMO' ? 'Préstamo cerrado' : 'Transferencia confirmada',
      `${this.folio(item)} quedó ${item.tipo === 'PRESTAMO' ? 'cerrado' : 'confirmada'} con su documento.`,
    );
    this.errorDetalle.set('');
    this.documentoCierre.set(null);
  }

  async abrirDocumento(expedienteId: number, documentoId: number): Promise<void> {
    const error = await this.movimientos.abrirDocumento(expedienteId, documentoId);
    if (error) this.notificaciones.error(error);
  }

  avance(item: ExpedienteExterno): number {
    return item.cajas ? Math.min(100, Math.round((item.cajasDevueltas / item.cajas) * 100)) : 0;
  }

  tamano(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  private existenciasDe(clave: string) {
    return this.inventario
      .lotes()
      .filter((lote) => lote.clave === clave.trim() && !estaVencido(lote.caducidad))
      .sort((a, b) => a.caducidad.localeCompare(b.caducidad) || a.ubicacion.localeCompare(b.ubicacion));
  }
}
