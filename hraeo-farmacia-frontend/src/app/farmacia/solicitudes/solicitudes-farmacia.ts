import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { FARMACIA_MENU } from '../farmacia-nav';
import { AlmacenApi, ESTATUS_SOLICITUD_TEXTO, EstatusSolicitud, Medicamento, Solicitud, folioPedido, folioSolicitud, mensajeDeError, solicitudAbierta } from '../../almacen/almacen-api';
import { Notificaciones } from '../../shared/notificaciones/notificaciones';

// Farmacia · Solicitudes a Almacén (HU13). Farmacia pide medicamento con clave y
// cantidad y consulta el estatus; Almacén las atiende en su Bandeja de despacho.
// Las solicitudes son del área: todo el personal de Farmacia ve las de todos, con
// quién hizo cada una, para que en cambio de turno se vea lo pendiente y no se pida
// dos veces lo mismo.
// La clave se elige del catálogo de medicamentos; el backend la vuelve a validar al guardar.
@Component({
  selector: 'app-solicitudes-farmacia',
  imports: [FormsModule, DatePipe, Topbar, Sidebar],
  templateUrl: './solicitudes-farmacia.html',
  styleUrls: [
    '../../almacen/recepcion-almacen/recepcion-almacen.css',
    '../../almacen/recepcion-almacen/recepcion-comun.css',
    '../../almacen/despacho/despacho-comun.css',
    './solicitudes-farmacia.css',
  ],
})
export class SolicitudesFarmacia {
  protected readonly session = inject(Session);
  private readonly notificaciones = inject(Notificaciones);
  private readonly api = inject(AlmacenApi);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  protected readonly estadoTexto = ESTATUS_SOLICITUD_TEXTO;
  protected readonly folio = folioSolicitud;
  readonly items = FARMACIA_MENU;

  readonly solicitudes = signal<Solicitud[]>([]);
  readonly cargando = signal(true);
  readonly enviando = signal(false);
  readonly enEspera = computed(() => this.solicitudes().filter((item) => solicitudAbierta(item.estatus)).length);

  // Filtros de la lista: de quién (todas / mías), estatus y búsqueda.
  readonly soloMias = signal(false);
  readonly filtroEstatus = signal<EstatusSolicitud | ''>('');
  readonly busqueda = signal('');
  private readonly miUsuario = computed(() => this.session.usuarioActual()?.nombreUsuario ?? '');

  esMia(solicitud: Solicitud): boolean {
    return solicitud.solicitadoPorUsuario === this.miUsuario();
  }

  readonly filtrosEstatus = computed(() => {
    const base = this.solicitudes().filter((item) => !this.soloMias() || this.esMia(item));
    const contar = (estatus: EstatusSolicitud) => base.filter((item) => item.estatus === estatus).length;
    return [
      { valor: '' as const, etiqueta: 'Todas', total: base.length },
      { valor: 'PENDIENTE' as const, etiqueta: 'Pendientes', total: contar('PENDIENTE') },
      { valor: 'PARCIAL' as const, etiqueta: 'Parciales', total: contar('PARCIAL') },
      { valor: 'ATENDIDA' as const, etiqueta: 'Atendidas', total: contar('ATENDIDA') },
      { valor: 'CANCELADA' as const, etiqueta: 'Canceladas', total: contar('CANCELADA') },
      { valor: 'RECHAZADA' as const, etiqueta: 'No se pueden surtir', total: contar('RECHAZADA') },
    ];
  });

  readonly solicitudesFiltradas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.solicitudes().filter((item) => {
      if (this.soloMias() && !this.esMia(item)) return false;
      if (this.filtroEstatus() && item.estatus !== this.filtroEstatus()) return false;
      if (!texto) return true;
      return `${folioSolicitud(item.id)} ${item.pedidoId ? folioPedido(item.pedidoId) : ''} ${item.clave} ${item.nombreGenerico} ${item.solicitadoPor ?? ''}`.toLowerCase().includes(texto);
    });
  });

  readonly catalogo = signal<Medicamento[]>([]);
  readonly clave = signal('');
  readonly medicamento = computed(() => this.catalogo().find((item) => item.clave === this.clave().trim()));
  readonly solicitante = computed(() => this.session.usuarioActual()?.nombreCompleto ?? '');
  cantidad = 0;

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'success' | 'error'>('success');

  constructor() {
    this.recargar();
    // Solo se pueden pedir claves dadas de alta en HAERO (con código de barras): las demás no tienen inventario aquí.
    this.api.listarMedicamentos().subscribe({ next: (lista) => this.catalogo.set(lista.filter((item) => item.dadoDeAlta)), error: () => {} });
  }

  protected readonly abierta = solicitudAbierta;

  /** HU40: Farmacia cancela lo que ya no necesita; lo ya despachado se queda. */
  async cancelar(solicitud: Solicitud): Promise<void> {
    const confirmado = await this.notificaciones.confirmar({
      titulo: `¿Cancelar ${folioSolicitud(solicitud.id)}?`,
      mensaje:
        `Almacén ya no surtirá ${solicitud.cantidadSolicitada - solicitud.cantidadAtendida} cajas de ${solicitud.nombreGenerico || solicitud.clave}. ` +
        'Lo que ya se despachó no cambia.',
      textoAceptar: 'Sí, cancelar',
      textoCancelar: 'No',
    });
    if (!confirmado) return;
    this.api.cancelarSolicitud(solicitud.id).subscribe({
      next: () => {
        this.notificaciones.exito('Solicitud cancelada', `${folioSolicitud(solicitud.id)} ya no aparece en la bandeja de Almacén.`);
        this.recargar();
      },
      error: (error) => this.notificaciones.error(mensajeDeError(error, 'No se pudo cancelar la solicitud.')),
    });
  }

  recargar(): void {
    this.cargando.set(true);
    this.api.solicitudesFarmacia().subscribe({
      next: (lista) => {
        // Las más recientes primero.
        this.solicitudes.set([...lista].sort((a, b) => b.fecha.localeCompare(a.fecha)));
        this.cargando.set(false);
      },
      error: (error) => {
        this.cargando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudieron cargar las solicitudes.'), 'error');
      },
    });
  }

  // HU39: el pedido que se está armando; se manda completo a Almacén.
  readonly renglones = signal<{ clave: string; nombre: string; cantidad: number }[]>([]);
  readonly totalCajasPedido = computed(() => this.renglones().reduce((total, r) => total + r.cantidad, 0));
  protected readonly folioPedido = folioPedido;

  /** Agrega la clave al pedido; si ya estaba, suma la cantidad. */
  async agregar(): Promise<void> {
    const clave = this.clave().trim();
    if (!clave) {
      this.mostrarMensaje('Escribe la clave del medicamento.', 'error');
      return;
    }
    if (this.catalogo().length && !this.medicamento()) {
      this.mostrarMensaje('Esa clave no está dada de alta en el Almacén. Revisa la clave del medicamento.', 'error');
      return;
    }
    if (!Number.isInteger(this.cantidad) || this.cantidad <= 0) {
      this.mostrarMensaje('La cantidad debe ser un número entero mayor a cero.', 'error');
      return;
    }

    // Si alguien del área ya pidió esa clave y Almacén no la ha surtido completa, se avisa antes.
    const abierta = this.solicitudes().find((item) => item.clave === clave && solicitudAbierta(item.estatus));
    if (abierta && !this.renglones().some((r) => r.clave === clave)) {
      const quien = this.esMia(abierta) ? 'por ti' : `por ${abierta.solicitadoPor ?? 'otro usuario de Farmacia'}`;
      const cuando = new Date(abierta.fecha).toLocaleString('es-MX', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false });
      const pedir = await this.notificaciones.confirmar({
        titulo: 'Ya hay una solicitud de esta clave',
        mensaje:
          `${folioSolicitud(abierta.id)} pidió ${abierta.cantidadSolicitada} cajas de ${abierta.nombreGenerico || clave}, hecha ${quien} el ${cuando}. ` +
          `Está ${ESTATUS_SOLICITUD_TEXTO[abierta.estatus].toLowerCase()} (${abierta.cantidadAtendida} de ${abierta.cantidadSolicitada} atendidas). ¿Pedir de todos modos?`,
        textoAceptar: 'Sí, pedir otra',
        textoCancelar: 'No, cancelar',
      });
      if (!pedir) return;
    }

    const cantidad = this.cantidad;
    const nombre = this.medicamento()?.nombreGenerico ?? clave;
    this.renglones.update((lista) =>
      lista.some((r) => r.clave === clave)
        ? lista.map((r) => (r.clave === clave ? { ...r, cantidad: r.cantidad + cantidad } : r))
        : [...lista, { clave, nombre, cantidad }],
    );
    this.mensaje.set('');
    this.clave.set('');
    this.cantidad = 0;
  }

  quitarRenglon(clave: string): void {
    this.renglones.update((lista) => lista.filter((r) => r.clave !== clave));
  }

  enviar(): void {
    const partidas = this.renglones().map((r) => ({ clave: r.clave, cantidad: r.cantidad }));
    if (!partidas.length || this.enviando()) return;
    this.enviando.set(true);
    this.api.crearPedido(partidas).subscribe({
      next: (creadas) => {
        this.enviando.set(false);
        const folio = creadas[0]?.pedidoId ? folioPedido(creadas[0].pedidoId) : '';
        this.mostrarMensaje(
          `Pedido ${folio} enviado a Almacén: ${creadas.length} ${creadas.length === 1 ? 'clave' : 'claves'}, ${this.totalCajasPedido()} cajas. Queda pendiente hasta que Almacén lo despache.`,
          'success', 'Pedido enviado',
        );
        this.solicitudes.set([...creadas, ...this.solicitudes()]);
        this.renglones.set([]);
      },
      error: (error) => {
        this.enviando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo enviar el pedido.'), 'error');
      },
    });
  }

  /** Claves todavía abiertas del mismo pedido (para ofrecer cancelarlo completo). */
  abiertasDelPedido(pedidoId: number | null): number {
    return pedidoId ? this.solicitudes().filter((s) => s.pedidoId === pedidoId && solicitudAbierta(s.estatus)).length : 0;
  }

  async cancelarPedido(pedidoId: number): Promise<void> {
    const confirmado = await this.notificaciones.confirmar({
      titulo: `¿Cancelar todo el pedido ${folioPedido(pedidoId)}?`,
      mensaje: `Se cancelan las ${this.abiertasDelPedido(pedidoId)} claves que siguen pendientes. Lo que ya se despachó no cambia.`,
      textoAceptar: 'Sí, cancelar el pedido',
      textoCancelar: 'No',
    });
    if (!confirmado) return;
    this.api.cancelarPedido(pedidoId).subscribe({
      next: () => {
        this.notificaciones.exito('Pedido cancelado', `${folioPedido(pedidoId)} ya no aparece en la bandeja de Almacén.`);
        this.recargar();
      },
      error: (error) => this.notificaciones.error(mensajeDeError(error, 'No se pudo cancelar el pedido.')),
    });
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error', titulo = 'Listo'): void {
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
