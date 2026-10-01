import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import {
  AlmacenApi,
  ESTATUS_SOLICITUD_TEXTO,
  Existencia,
  Solicitud,
  folioSolicitud,
  mensajeDeError,
} from '../../almacen-api';
import { SolicitudesAlmacen, estaVencido, formatoCaducidad } from '../../solicitudes-almacen';

interface RenglonDespacho extends Existencia {
  aDespachar: number;
  bloqueado: boolean;
}

// Despacho · Bandeja de solicitudes (HU14, HU15). A la izquierda las solicitudes
// pendientes; a la derecha el detalle con la sugerencia FEFO del backend. El bloqueo
// de lotes posteriores se muestra en vivo, pero quien valida al confirmar es el backend.
@Component({
  selector: 'app-bandeja-despacho',
  imports: [FormsModule, DatePipe],
  templateUrl: './bandeja-despacho.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../despacho-comun.css', './bandeja-despacho.css'],
})
export class BandejaDespacho {
  private readonly api = inject(AlmacenApi);
  // Salidas registradas y CPM se vuelven a leer después de cada despacho.
  private readonly historial = inject(SolicitudesAlmacen);
  protected readonly formatoCaducidad = formatoCaducidad;
  protected readonly estadoTexto = ESTATUS_SOLICITUD_TEXTO;
  protected readonly folio = folioSolicitud;

  readonly porAtender = signal<Solicitud[]>([]);
  readonly cargandoBandeja = signal(true);
  readonly cargandoDetalle = signal(false);
  readonly enviando = signal(false);

  readonly seleccionada = signal<number | null>(null);
  readonly solicitud = computed(() => this.porAtender().find((item) => item.id === this.seleccionada()));
  readonly porSurtir = signal(0);

  // Existencias de la clave seleccionada (sin vencidas ni vacías), en orden FEFO.
  readonly existencias = signal<Existencia[]>([]);
  readonly lotesVencidos = signal(0);
  // Cajas sugeridas por el backend, por existenciaId.
  private readonly sugeridas = signal<Record<number, number>>({});
  // Cajas que el despachador va a tomar de cada existencia, por existenciaId.
  readonly asignaciones = signal<Record<number, number>>({});

  readonly renglones = computed<RenglonDespacho[]>(() => {
    const filas = this.existencias();
    const asignaciones = this.asignaciones();
    const tomado = (fila: Existencia) => asignaciones[fila.existenciaId] ?? 0;
    return filas.map((fila) => ({
      ...fila,
      aDespachar: tomado(fila),
      bloqueado: filas.some((otra) => otra.caducidad < fila.caducidad && tomado(otra) < otra.cajas),
    }));
  });

  // Lote(s) con la caducidad más próxima disponible (HU15 CA4).
  readonly sugerenciaFefo = computed(() => {
    const filas = this.existencias();
    if (!filas.length) return null;
    const mismos = filas.filter((fila) => fila.caducidad === filas[0].caducidad);
    return {
      lotes: [...new Set(mismos.map((fila) => fila.numeroLote))].join(', '),
      caducidad: filas[0].caducidad,
      ubicaciones: mismos.map((fila) => fila.ubicacion).join(', '),
      cajas: mismos.reduce((total, fila) => total + fila.cajas, 0),
    };
  });

  readonly disponibleTotal = computed(() => this.existencias().reduce((total, fila) => total + fila.cajas, 0));
  readonly totalADespachar = computed(() => this.renglones().reduce((total, fila) => total + fila.aDespachar, 0));
  readonly esParcial = computed(() => this.totalADespachar() < this.porSurtir());

  // Ayuda visual previa; el backend vuelve a validar todo al confirmar.
  readonly errorPartidas = computed(() => {
    const renglones = this.renglones().filter((fila) => fila.aDespachar !== 0);
    if (!renglones.length) return '';
    for (const fila of renglones) {
      if (!Number.isInteger(fila.aDespachar) || fila.aDespachar < 0) return 'Las cantidades deben ser números enteros mayores a cero.';
      if (fila.aDespachar > fila.cajas) return `Existencia insuficiente: el lote ${fila.numeroLote} en ${fila.ubicacion} solo tiene ${fila.cajas} cajas.`;
      if (fila.bloqueado) {
        const anterior = this.renglones().find((otra) => otra.caducidad < fila.caducidad && otra.aDespachar < otra.cajas)!;
        return `Existe un lote con caducidad más próxima: ${anterior.numeroLote} (${formatoCaducidad(anterior.caducidad)}) en ${anterior.ubicacion}. Despáchalo primero.`;
      }
    }
    if (this.totalADespachar() > this.porSurtir()) return `Solo faltan ${this.porSurtir()} cajas por surtir; ajusta las cantidades.`;
    return '';
  });

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'success' | 'error' | 'info'>('success');

  constructor() {
    this.recargarBandeja(true);
  }

  recargarBandeja(seleccionarPrimera = false): void {
    this.cargandoBandeja.set(true);
    this.api.bandejaSolicitudes().subscribe({
      next: (lista) => {
        // Las más antiguas primero.
        const ordenadas = [...lista].sort((a, b) => a.fecha.localeCompare(b.fecha));
        this.porAtender.set(ordenadas);
        this.cargandoBandeja.set(false);
        const sigueSeleccionada = ordenadas.some((item) => item.id === this.seleccionada());
        if (seleccionarPrimera || !sigueSeleccionada) {
          if (ordenadas.length) this.seleccionar(ordenadas[0].id);
          else this.limpiarDetalle();
        } else {
          // Se vuelve a leer el detalle para ver las existencias ya descontadas.
          this.seleccionar(this.seleccionada()!);
        }
      },
      error: (error) => {
        this.cargandoBandeja.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo cargar la bandeja de solicitudes.'), 'error');
      },
    });
  }

  seleccionar(id: number): void {
    const solicitud = this.porAtender().find((item) => item.id === id);
    if (!solicitud) return;
    this.seleccionada.set(id);
    this.porSurtir.set(solicitud.cantidadSolicitada - solicitud.cantidadAtendida);
    this.existencias.set([]);
    this.asignaciones.set({});
    this.cargandoDetalle.set(true);

    forkJoin({
      existencias: this.api.listarExistencias({ clave: solicitud.clave }),
      sugerencia: this.api.sugerenciaDespacho(id),
    }).subscribe({
      next: ({ existencias, sugerencia }) => {
        if (this.seleccionada() !== id) return;
        const conCajas = existencias.filter((fila) => fila.cajas > 0);
        this.existencias.set(
          conCajas
            .filter((fila) => !estaVencido(fila.caducidad))
            .sort((a, b) => a.caducidad.localeCompare(b.caducidad) || a.ubicacion.localeCompare(b.ubicacion)),
        );
        this.lotesVencidos.set(conCajas.filter((fila) => estaVencido(fila.caducidad)).length);
        this.porSurtir.set(sugerencia.cantidadPendiente);
        this.sugeridas.set(Object.fromEntries(sugerencia.partidas.map((partida) => [partida.existenciaId, partida.cajasSugeridas])));
        this.usarSugerencia();
        this.cargandoDetalle.set(false);
      },
      error: (error) => {
        this.cargandoDetalle.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo cargar el detalle de la solicitud.'), 'error');
      },
    });
  }

  /** HU14 CA1: precarga las cajas que sugiere el backend en orden FEFO. */
  usarSugerencia(): void {
    this.asignaciones.set({ ...this.sugeridas() });
  }

  asignar(renglon: RenglonDespacho, valor: number | string | null): void {
    const cajas = Number(valor) || 0;
    this.asignaciones.update((actual) => ({ ...actual, [renglon.existenciaId]: cajas }));
  }

  confirmar(): void {
    const solicitud = this.solicitud();
    if (!solicitud || this.errorPartidas() || this.enviando()) return;

    const partidas = this.renglones()
      .filter((fila) => fila.aDespachar > 0)
      .map((fila) => ({ existenciaId: fila.existenciaId, cajas: fila.aDespachar }));
    const cajas = partidas.reduce((total, partida) => total + partida.cajas, 0);
    const nombre = solicitud.nombreGenerico || solicitud.clave;

    this.enviando.set(true);
    this.api.despachar(solicitud.id, { partidas, parcial: this.esParcial() }).subscribe({
      next: (respuesta) => {
        this.enviando.set(false);
        this.historial.recargarSalidas();
        if (respuesta.estatus === 'ATENDIDA') {
          this.mostrarMensaje(`Solicitud ${folioSolicitud(solicitud.id)} atendida: se despacharon ${cajas} cajas de ${nombre}.`, 'success');
          this.recargarBandeja(true);
        } else {
          const faltan = respuesta.cantidadSolicitada - respuesta.cantidadAtendida;
          this.mostrarMensaje(`Entrega parcial de ${folioSolicitud(solicitud.id)}: se despacharon ${cajas} cajas; faltan ${faltan}.`, 'success');
          this.recargarBandeja();
        }
      },
      error: (error) => {
        this.enviando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo registrar el despacho.'), 'error');
        // El inventario pudo cambiar (otro despachador); se vuelve a leer.
        this.seleccionar(solicitud.id);
      },
    });
  }

  private limpiarDetalle(): void {
    this.seleccionada.set(null);
    this.existencias.set([]);
    this.asignaciones.set({});
    this.porSurtir.set(0);
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error' | 'info'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
