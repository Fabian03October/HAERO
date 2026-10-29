import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import {
  AlmacenApi,
  ESTATUS_SOLICITUD_TEXTO,
  Existencia,
  Solicitud,
  folioPedido,
  folioSolicitud,
  mensajeDeError,
} from '../../almacen-api';
import { SolicitudesAlmacen, estaVencido, formatoCaducidad } from '../../solicitudes-almacen';
import { RotacionAlmacen } from '../../rotacion-almacen';
import { Notificaciones } from '../../../shared/notificaciones/notificaciones';

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
  private readonly rotacion = inject(RotacionAlmacen);
  private readonly notificaciones = inject(Notificaciones);
  protected readonly formatoCaducidad = formatoCaducidad;
  protected readonly estadoTexto = ESTATUS_SOLICITUD_TEXTO;
  protected readonly folio = folioSolicitud;

  readonly porAtender = signal<Solicitud[]>([]);
  readonly cargandoBandeja = signal(true);
  readonly cargandoDetalle = signal(false);
  readonly enviando = signal(false);

  readonly seleccionada = signal<number | null>(null);
  readonly solicitud = computed(() => this.porAtender().find((item) => item.id === this.seleccionada()));
  protected readonly folioPedido = folioPedido;
  // HU39: las claves del mismo pedido que siguen en la bandeja.
  readonly delPedido = computed(() => {
    const pedidoId = this.solicitud()?.pedidoId;
    return pedidoId ? this.porAtender().filter((item) => item.pedidoId === pedidoId) : [];
  });
  readonly pedidoParcial = signal(false);
  readonly porSurtir = signal(0);

  // Existencias de la clave seleccionada (sin vencidas ni vacías), en orden FEFO.
  readonly existencias = signal<Existencia[]>([]);
  readonly lotesVencidos = signal(0);
  // Cajas sugeridas por el backend, por existenciaId.
  private readonly sugeridas = signal<Record<number, number>>({});
  // Cajas que el despachador va a tomar de cada existencia, por existenciaId.
  readonly asignaciones = signal<Record<number, number>>({});
  // Ajuste HU15: el despachador puede elegir salirse del orden FEFO, pero
  // solo si lo confirma explícitamente. Se reinicia con cada solicitud.
  readonly fueraDeFefo = signal(false);
  // HU15 CA2: motivo obligatorio si de verdad se toma un lote fuera del orden FEFO.
  readonly motivoFueraDeFefo = signal('');
  // HU40: por qué no se puede surtir lo pendiente.
  readonly motivoRechazo = signal('');
  readonly rechazando = signal(false);

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

  // Salirse de FEFO solo tiene sentido si hay existencias con caducidad posterior a la
  // sugerida; si todas caducan igual, nada se bloquea y se explica por qué no hay opción.
  readonly sinOtroLote = computed(() => {
    const filas = this.existencias();
    if (!filas.length || filas.some((fila) => fila.caducidad !== filas[0].caducidad)) return '';
    if (filas.length === 1) {
      return `No hay otro lote para elegir: esta clave solo tiene el lote ${filas[0].numeroLote} en ${filas[0].ubicacion}.`;
    }
    return `No hay un lote con caducidad posterior para elegir: todas las existencias de esta clave caducan el ${formatoCaducidad(filas[0].caducidad)}, así que puedes tomar de cualquiera sin salirte del orden FEFO.`;
  });

  readonly disponibleTotal = computed(() => this.existencias().reduce((total, fila) => total + fila.cajas, 0));
  readonly totalADespachar = computed(() => this.renglones().reduce((total, fila) => total + fila.aDespachar, 0));
  readonly esParcial = computed(() => this.totalADespachar() < this.porSurtir());

  readonly saleFueraDeFefo = computed(() => this.fueraDeFefo() && this.renglones().some((fila) => fila.bloqueado && fila.aDespachar > 0));

  // Ayuda visual previa; el backend vuelve a validar todo al confirmar.
  readonly errorPartidas = computed(() => {
    const renglones = this.renglones().filter((fila) => fila.aDespachar !== 0);
    if (!renglones.length) return '';
    for (const fila of renglones) {
      if (!Number.isInteger(fila.aDespachar) || fila.aDespachar < 0) return 'Las cantidades deben ser números enteros mayores a cero.';
      if (fila.aDespachar > fila.cajas) return `Existencia insuficiente: el lote ${fila.numeroLote} en ${fila.ubicacion} solo tiene ${fila.cajas} cajas.`;
      if (fila.bloqueado && !this.fueraDeFefo()) {
        const anterior = this.renglones().find((otra) => otra.caducidad < fila.caducidad && otra.aDespachar < otra.cajas)!;
        return `Existe un lote con caducidad más próxima: ${anterior.numeroLote} (${formatoCaducidad(anterior.caducidad)}) en ${anterior.ubicacion}. Despáchalo primero.`;
      }
    }
    if (this.totalADespachar() > this.porSurtir()) return `Solo faltan ${this.porSurtir()} cajas por surtir; ajusta las cantidades.`;
    if (this.saleFueraDeFefo() && !this.motivoFueraDeFefo().trim()) return 'Escribe por qué despachas fuera del orden FEFO.';
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
    this.fueraDeFefo.set(false);
    this.motivoFueraDeFefo.set('');
    this.motivoRechazo.set('');
    this.rechazando.set(false);
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

  /** HU15 (ajuste): salirse del orden FEFO requiere confirmación explícita del despachador. */
  async alternarFueraDeFefo(marcado: boolean, casilla: HTMLInputElement): Promise<void> {
    if (!marcado) {
      this.fueraDeFefo.set(false);
      return;
    }
    const confirmado = await this.notificaciones.confirmar({
      titulo: '¿Despachar fuera del orden FEFO?',
      mensaje:
        'Vas a poder elegir medicamento de otro lote o ubicación que no es el más próximo a caducar. ' +
        'Los lotes ya vencidos seguirán bloqueados.',
      textoAceptar: 'Sí, elegir otro lote',
    });
    this.fueraDeFefo.set(confirmado);
    // Si cancela, el signal no cambia (ya era false) y ngModel no desmarca la casilla solo.
    casilla.checked = confirmado;
  }

  /** HU39: todas las claves del pedido con su sugerencia FEFO, en una sola operación. */
  async despacharPedido(): Promise<void> {
    const pedidoId = this.solicitud()?.pedidoId;
    if (!pedidoId || this.enviando()) return;
    const claves = this.delPedido().length;
    const confirmado = await this.notificaciones.confirmar({
      titulo: `¿Despachar todo el pedido ${folioPedido(pedidoId)}?`,
      mensaje:
        `Se despachan las ${claves} claves pendientes, cada una del lote que caduca primero. ` +
        (this.pedidoParcial()
          ? 'Lo que no alcance queda pendiente.'
          : 'Si a alguna no le alcanza la existencia, no se despacha ninguna.'),
      textoAceptar: 'Sí, despachar',
    });
    if (!confirmado) return;
    this.enviando.set(true);
    this.api.despacharPedido(pedidoId, this.pedidoParcial()).subscribe({
      next: (respuesta) => {
        this.enviando.set(false);
        const sin = respuesta.sinExistencia.length ? ` Sin existencia, quedan pendientes: ${respuesta.sinExistencia.join(', ')}.` : '';
        this.mostrarMensaje(
          `${folioPedido(pedidoId)}: se despacharon ${respuesta.despachadas.length} claves.${sin} Descarga su vale en Salidas registradas.`,
          'success',
          'Pedido despachado',
        );
        this.historial.recargarSalidas();
        this.rotacion.recargar();
        this.recargarBandeja(true);
      },
      error: (error) => {
        this.enviando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo despachar el pedido.'), 'error');
      },
    });
  }

  /** HU40: lo pendiente no se puede surtir; Farmacia ve el motivo. */
  rechazar(): void {
    const solicitud = this.solicitud();
    const motivo = this.motivoRechazo().trim();
    if (!solicitud || !motivo || this.enviando()) return;
    this.enviando.set(true);
    this.api.rechazarSolicitud(solicitud.id, motivo).subscribe({
      next: () => {
        this.enviando.set(false);
        this.mostrarMensaje(`${folioSolicitud(solicitud.id)} quedó como "no se puede surtir": ${motivo}.`, 'success', 'Solicitud cerrada');
        this.recargarBandeja(true);
      },
      error: (error) => {
        this.enviando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo cerrar la solicitud.'), 'error');
      },
    });
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
    this.api.despachar(solicitud.id, { partidas, parcial: this.esParcial(), confirmarFueraDeFefo: this.fueraDeFefo(),
      motivoFueraDeFefo: this.saleFueraDeFefo() ? this.motivoFueraDeFefo().trim() : undefined }).subscribe({
      next: (respuesta) => {
        this.enviando.set(false);
        this.historial.recargarSalidas();
        this.rotacion.recargar();
        if (respuesta.estatus === 'ATENDIDA') {
          this.mostrarMensaje(`Solicitud ${folioSolicitud(solicitud.id)} atendida: se despacharon ${cajas} cajas de ${nombre}.`, 'success', 'Solicitud atendida');
          this.recargarBandeja(true);
        } else {
          const faltan = respuesta.cantidadSolicitada - respuesta.cantidadAtendida;
          this.mostrarMensaje(`Entrega parcial de ${folioSolicitud(solicitud.id)}: se despacharon ${cajas} cajas; faltan ${faltan}.`, 'success', 'Entrega parcial registrada');
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

  private mostrarMensaje(texto: string, tipo: 'success' | 'error' | 'info', titulo = 'Listo'): void {
    // Lo que sí se hizo se avisa con un popup; errores e indicaciones se quedan junto al formulario.
    if (tipo === 'success') {
      this.mensaje.set('');
      this.notificaciones.exito(titulo, texto);
      return;
    }
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
