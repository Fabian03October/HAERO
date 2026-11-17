import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { EscanerCamara } from '../../../shared/escaner-camara/escaner-camara';
import { leerGs1 } from '../../../shared/gs1';
import { pitido } from '../../../shared/pitido';
import { Medicamento, mensajeDeError, ultimoDiaDelMes } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { BuscadorMedicamento } from '../../buscador-medicamento/buscador-medicamento';
import { EstadoConteo, FarmaciaApi, NuevoRenglonConteo, RenglonConteo } from '../../farmacia-api';
import { InventarioInicialEstado } from './inventario-inicial-estado';

// Cada cuánto se vuelve a pedir el conteo para ver lo que capturan los demás.
const RECARGA_MS = 10_000;

interface Estante {
  // '' = renglones registrados sin estante.
  nombre: string;
  total: number;
  contados: number;
}

type EstadoEstante = 'completo' | 'parcial' | 'sin-contar' | 'vacio';

// Caja escaneada a la que le falta el lote o la caducidad (el código no es GS1 o viene incompleto).
interface CajaPendiente {
  codigo: string;
  loteDelCodigo: boolean;
}

// Farmacia · Inventario inicial (HU25), por estantes. Tablero con el avance de cada estante;
// al entrar a uno se escanean sus cajas y se capturan las piezas ahí mismo. Todo vive en el
// servidor (varias personas a la vez) y al final se guarda junto, una sola vez.
@Component({
  selector: 'app-alta-inicial-farmacia',
  imports: [FormsModule, RouterLink, Topbar, Sidebar, EscanerCamara, BuscadorMedicamento],
  templateUrl: './alta-inicial-farmacia.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css', './alta-inicial-farmacia.css'],
})
export class AltaInicialFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;
  private readonly api = inject(FarmaciaApi);
  private readonly inventario = inject(InventarioInicialEstado);

  readonly estado = signal<EstadoConteo | null>(null);
  readonly errorCarga = signal('');
  readonly mensaje = signal('');

  // Vista: tablero (null), un estante ('' = sin estante) o revisar y guardar.
  readonly estante = signal<string | null>(null);
  readonly revisando = signal(false);

  // Estantes creados aquí que todavía no tienen cajas (solo en esta pantalla).
  private readonly estantesNuevos = signal<string[]>([]);
  readonly creandoEstante = signal(false);
  nombreNuevo = '';
  private readonly campoEstante = viewChild<ElementRef<HTMLInputElement>>('campoEstante');

  // Escaneo continuo.
  codigoEscaneado = '';
  readonly camaraAbierta = signal(false);
  readonly ultimo = signal<{ texto: string; tipo: 'ok' | 'aviso' | 'error' } | null>(null);
  readonly ultimoId = signal<number | null>(null);
  readonly pendiente = signal<CajaPendiente | null>(null);
  loteManual = '';
  mesManual = '';
  // Código nuevo: se elige la clave y se vuelve a mandar la misma caja.
  readonly porClave = signal<NuevoRenglonConteo | null>(null);
  private readonly campoCodigo = viewChild<ElementRef<HTMLInputElement>>('campoCodigo');
  private readonly campoLote = viewChild<ElementRef<HTMLInputElement>>('campoLote');

  // Piezas que se están escribiendo (id → texto); la recarga automática no las pisa.
  readonly borradores = signal<Record<number, string>>({});
  private readonly enviandoCantidad = new Set<number>();
  readonly avisoTabla = signal('');

  readonly confirmando = signal(false);
  readonly guardando = signal(false);

  private readonly hoy = (() => {
    const ahora = new Date();
    return `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(ahora.getDate()).padStart(2, '0')}`;
  })();

  readonly renglones = computed(() => this.estado()?.renglones ?? []);

  readonly estantes = computed<Estante[]>(() => {
    const mapa = new Map<string, Estante>();
    for (const nombre of this.estantesNuevos()) mapa.set(nombre, { nombre, total: 0, contados: 0 });
    for (const r of this.renglones()) {
      const nombre = r.ubicacion ?? '';
      const estante = mapa.get(nombre) ?? { nombre, total: 0, contados: 0 };
      estante.total++;
      if (r.cantidad !== null) estante.contados++;
      mapa.set(nombre, estante);
    }
    return [...mapa.values()].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es', { numeric: true }));
  });

  readonly avance = computed(() => {
    const total = this.renglones().length;
    const contados = this.renglones().filter((r) => r.cantidad !== null).length;
    return { total, contados, porcentaje: total ? Math.floor((contados / total) * 100) : 0 };
  });

  // Lo más nuevo arriba.
  readonly renglonesDelEstante = computed(() => {
    const estante = this.estante();
    return this.renglones()
      .filter((r) => (r.ubicacion ?? '') === estante)
      .sort((a, b) => b.id - a.id);
  });

  readonly contadosEstante = computed(() => this.renglonesDelEstante().filter((r) => r.cantidad !== null).length);

  readonly resumen = computed(() => {
    const renglones = this.renglones();
    const llave = (r: RenglonConteo) => `${r.clave}|${r.lote.toUpperCase()}`;
    const porLote = new Map<string, RenglonConteo[]>();
    for (const r of renglones) porLote.set(llave(r), [...(porLote.get(llave(r)) ?? []), r]);
    const repartidos = [...porLote.values()]
      .filter((grupo) => new Set(grupo.map((r) => r.ubicacion ?? '')).size > 1)
      .map((grupo) => ({
        llave: llave(grupo[0]),
        medicamento: grupo[0].medicamento,
        lote: grupo[0].lote,
        partes: grupo.map((r) => `${r.ubicacion ?? 'Sin estante'} (${r.cantidad ?? '?'})`).join(' + '),
        total: grupo.reduce((suma, r) => suma + (r.cantidad ?? 0), 0),
      }));
    const conPiezas = renglones.filter((r) => (r.cantidad ?? 0) > 0);
    return {
      renglones: renglones.length,
      claves: new Set(renglones.map((r) => r.clave)).size,
      piezas: renglones.reduce((suma, r) => suma + (r.cantidad ?? 0), 0),
      faltan: renglones.filter((r) => r.cantidad === null).length,
      enCero: renglones.filter((r) => r.cantidad === 0).length,
      lotesAlta: new Set(conPiezas.map(llave)).size,
      estantesConFaltantes: this.estantes().filter((e) => e.contados < e.total),
      repartidos,
    };
  });

  constructor() {
    this.cargar();
    const reloj = setInterval(() => {
      if (!document.hidden && !this.guardando() && !this.estado()?.cerrado) this.cargar(true);
    }, RECARGA_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
  }

  cargar(silencioso = false): void {
    this.api.conteoInicial().subscribe({
      next: (estado) => {
        this.estado.set(estado);
        this.errorCarga.set('');
        if (estado.cerrado) this.inventario.marcarCerrado();
      },
      error: (error) => {
        if (!silencioso) this.errorCarga.set(mensajeDeError(error, 'No se pudo cargar el inventario inicial.'));
      },
    });
  }

  // ---------- Tablero ----------
  estadoDe(estante: Estante): EstadoEstante {
    if (!estante.total) return 'vacio';
    if (estante.contados === estante.total) return 'completo';
    return estante.contados ? 'parcial' : 'sin-contar';
  }

  textoEstado(estante: Estante): string {
    const estado = this.estadoDe(estante);
    if (estado === 'completo') return '✓ Completo';
    if (estado === 'parcial') return `Faltan ${estante.total - estante.contados}`;
    return estado === 'sin-contar' ? 'Sin contar' : 'Sin códigos';
  }

  nuevoEstante(): void {
    this.nombreNuevo = '';
    this.creandoEstante.set(true);
    setTimeout(() => this.campoEstante()?.nativeElement.focus());
  }

  crearEstante(): void {
    const nombre = this.nombreNuevo.trim().toUpperCase();
    if (!nombre) return;
    if (!this.estantes().some((e) => e.nombre === nombre)) this.estantesNuevos.update((lista) => [...lista, nombre]);
    this.creandoEstante.set(false);
    this.abrir(nombre);
  }

  abrir(nombre: string): void {
    this.estante.set(nombre);
    this.revisando.set(false);
    this.limpiarEscaneo();
    this.avisoTabla.set('');
    this.enfocarEscaner();
  }

  volver(): void {
    this.estante.set(null);
    this.revisando.set(false);
    this.confirmando.set(false);
    this.camaraAbierta.set(false);
    this.limpiarEscaneo();
  }

  revisar(): void {
    this.estante.set(null);
    this.camaraAbierta.set(false);
    this.revisando.set(true);
  }

  // ---------- Registrar cajas ----------
  escanear(): void {
    const texto = this.codigoEscaneado.trim();
    this.codigoEscaneado = '';
    if (!texto) return;
    this.pendiente.set(null);
    this.porClave.set(null);
    const gs1 = leerGs1(texto);
    const codigo = gs1?.gtin ?? texto;
    if (gs1?.lote && gs1.caducidad) {
      this.registrar({ codigo, lote: gs1.lote, caducidad: gs1.caducidad });
      return;
    }
    // Sin lote o sin caducidad en el código: se escriben a mano.
    this.loteManual = gs1?.lote ?? '';
    this.mesManual = '';
    this.pendiente.set({ codigo, loteDelCodigo: !!gs1?.lote });
    this.avisar('Este código no trae lote y caducidad: escríbelos y da Agregar.', 'aviso');
    setTimeout(() => this.campoLote()?.nativeElement.focus());
  }

  codigoDesdeCamara(codigo: string): void {
    this.codigoEscaneado = codigo;
    this.escanear();
  }

  agregarManual(): void {
    const caja = this.pendiente();
    if (!caja) return;
    const lote = this.loteManual.trim();
    if (!lote || !this.mesManual) {
      this.avisar('Falta el lote o la caducidad de la caja.', 'error');
      return;
    }
    this.registrar({ codigo: caja.codigo, lote, caducidad: ultimoDiaDelMes(this.mesManual) });
  }

  elegirClave(medicamento: Medicamento): void {
    const caja = this.porClave();
    if (caja) this.registrar({ ...caja, clave: medicamento.clave });
  }

  cancelarCaja(): void {
    this.limpiarEscaneo();
    this.enfocarEscaner();
  }

  private registrar(datos: NuevoRenglonConteo): void {
    const estante = this.estante();
    const nuevo: NuevoRenglonConteo = { ...datos, ubicacion: estante || undefined };
    const yaEstaba = new Set(this.renglones().map((r) => r.id));
    this.api.registrarEnConteo(nuevo).subscribe({
      next: (renglon) => {
        this.pendiente.set(null);
        this.porClave.set(null);
        this.ponerRenglon(renglon);
        this.ultimoId.set(renglon.id);
        if (yaEstaba.has(renglon.id)) {
          this.avisar(`${renglon.medicamento} · lote ${renglon.lote} ya estaba registrado en este estante.`, 'aviso');
        } else {
          this.avisar(`✓ ${renglon.medicamento} · lote ${renglon.lote}`, 'ok');
        }
        this.enfocarEscaner();
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 409) return this.yaGuardado(error);
        if (error.status === 404 && !nuevo.clave) {
          // Código nuevo: se elige la clave y se vuelve a mandar la misma caja.
          this.pendiente.set(null);
          this.porClave.set(nuevo);
          this.avisar(mensajeDeError(error, 'Código nuevo: elige la clave de esta caja.'), 'aviso');
          return;
        }
        this.avisar(mensajeDeError(error, 'No se pudo registrar la caja.'), 'error');
      },
    });
  }

  quitar(renglon: RenglonConteo): void {
    if (renglon.cantidad !== null && !confirm(`¿Quitar ${renglon.medicamento} lote ${renglon.lote}? Ya tiene ${renglon.cantidad} piezas contadas.`)) return;
    this.api.quitarDelConteo(renglon.id).subscribe({
      next: () => this.estado.update((e) => e && { ...e, renglones: e.renglones.filter((r) => r.id !== renglon.id) }),
      error: (error: HttpErrorResponse) => {
        if (error.status === 409) return this.yaGuardado(error);
        this.avisoTabla.set(mensajeDeError(error, 'No se pudo quitar el renglón.'));
      },
    });
  }

  // ---------- Capturar piezas ----------
  valor(renglon: RenglonConteo): string {
    return this.borradores()[renglon.id] ?? (renglon.cantidad === null ? '' : String(renglon.cantidad));
  }

  escribir(renglon: RenglonConteo, texto: string): void {
    this.borradores.update((b) => ({ ...b, [renglon.id]: texto }));
  }

  // Enter guarda y pasa al siguiente renglón, para capturar sin mouse.
  enterCantidad(evento: Event, renglon: RenglonConteo): void {
    evento.preventDefault();
    const campo = evento.target as HTMLInputElement;
    this.guardarCantidad(renglon);
    const campos = [...(campo.closest('tbody')?.querySelectorAll<HTMLInputElement>('input[data-cantidad]') ?? [])];
    const siguiente = campos[campos.indexOf(campo) + 1];
    if (siguiente) {
      siguiente.focus();
      siguiente.select();
    }
  }

  guardarCantidad(renglon: RenglonConteo): void {
    const texto = this.borradores()[renglon.id];
    if (texto === undefined || this.enviandoCantidad.has(renglon.id)) return;
    if (texto.trim() === '') return this.quitarBorrador(renglon.id);
    const cantidad = Number(texto);
    if (!Number.isInteger(cantidad) || cantidad < 0) {
      this.avisoTabla.set('Escribe las piezas con un número entero (0 si no había).');
      return;
    }
    if (cantidad === renglon.cantidad) return this.quitarBorrador(renglon.id);
    this.avisoTabla.set('');
    this.enviandoCantidad.add(renglon.id);
    this.api.contarRenglon(renglon.id, cantidad).subscribe({
      next: (actualizado) => {
        this.enviandoCantidad.delete(renglon.id);
        this.ponerRenglon(actualizado);
        // Si mientras tanto escribió otra cosa, se respeta.
        if (this.borradores()[renglon.id] === texto) this.quitarBorrador(renglon.id);
      },
      error: (error: HttpErrorResponse) => {
        this.enviandoCantidad.delete(renglon.id);
        if (error.status === 409) return this.yaGuardado(error);
        this.avisoTabla.set(mensajeDeError(error, `No se guardaron las piezas de ${renglon.clave} lote ${renglon.lote}.`));
      },
    });
  }

  // ---------- Guardar ----------
  guardar(): void {
    if (this.guardando()) return;
    this.guardando.set(true);
    this.mensaje.set('');
    this.api.guardarInventarioInicial().subscribe({
      next: (estado) => {
        this.guardando.set(false);
        this.confirmando.set(false);
        this.estado.set(estado);
        this.inventario.marcarCerrado();
      },
      error: (error: HttpErrorResponse) => {
        this.guardando.set(false);
        this.confirmando.set(false);
        if (error.status === 409) return this.yaGuardado(error);
        // Todo o nada: no se guardó nada; se corrige y se vuelve a intentar.
        this.mensaje.set(mensajeDeError(error, 'No se pudo guardar el inventario inicial. No se guardó nada.'));
      },
    });
  }

  // ---------- Formato ----------
  mesAnio(fecha: string): string {
    return `${fecha.slice(5, 7)}/${fecha.slice(0, 4)}`;
  }

  fechaHora(valor: string | null): string {
    return valor ? new Date(valor).toLocaleString('es-MX', { dateStyle: 'long', timeStyle: 'short' }) : '';
  }

  esVencido(renglon: RenglonConteo): boolean {
    return renglon.caducidad < this.hoy;
  }

  numero(valor: number): string {
    return valor.toLocaleString('es-MX');
  }

  // ---------- Apoyo ----------
  private ponerRenglon(renglon: RenglonConteo): void {
    this.estado.update(
      (e) =>
        e && {
          ...e,
          renglones: e.renglones.some((r) => r.id === renglon.id)
            ? e.renglones.map((r) => (r.id === renglon.id ? renglon : r))
            : [...e.renglones, renglon],
        },
    );
  }

  private quitarBorrador(id: number): void {
    this.borradores.update(({ [id]: _, ...resto }) => resto);
  }

  // Otra persona ya guardó el inventario inicial: se muestra el aviso y la pantalla de cerrado.
  private yaGuardado(error: HttpErrorResponse): void {
    this.mensaje.set(mensajeDeError(error, 'El inventario inicial de Farmacia ya se guardó.'));
    this.inventario.marcarCerrado();
    this.cargar();
  }

  private avisar(texto: string, tipo: 'ok' | 'aviso' | 'error'): void {
    this.ultimo.set({ texto, tipo });
    pitido(tipo === 'ok' ? 'ok' : 'error');
  }

  private limpiarEscaneo(): void {
    this.codigoEscaneado = '';
    this.pendiente.set(null);
    this.porClave.set(null);
    this.ultimo.set(null);
  }

  private enfocarEscaner(): void {
    setTimeout(() => this.campoCodigo()?.nativeElement.focus());
  }
}
