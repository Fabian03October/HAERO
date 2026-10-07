import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { FARMACIA_MENU } from '../farmacia-nav';
import { AlmacenApi, ESTATUS_SOLICITUD_TEXTO, EstatusSolicitud, Medicamento, Solicitud, folioSolicitud, mensajeDeError } from '../../almacen/almacen-api';
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
  readonly enEspera = computed(() => this.solicitudes().filter((item) => item.estatus !== 'ATENDIDA').length);

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
    ];
  });

  readonly solicitudesFiltradas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.solicitudes().filter((item) => {
      if (this.soloMias() && !this.esMia(item)) return false;
      if (this.filtroEstatus() && item.estatus !== this.filtroEstatus()) return false;
      if (!texto) return true;
      return `${folioSolicitud(item.id)} ${item.clave} ${item.nombreGenerico} ${item.solicitadoPor ?? ''}`.toLowerCase().includes(texto);
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
    this.api.listarMedicamentos().subscribe({ next: (lista) => this.catalogo.set(lista), error: () => {} });
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

  async enviar(): Promise<void> {
    const clave = this.clave().trim();
    if (!clave) {
      this.mostrarMensaje('Escribe la clave del medicamento.', 'error');
      return;
    }
    if (this.catalogo().length && !this.medicamento()) {
      this.mostrarMensaje('La clave no existe en el catálogo. Revisa la clave del medicamento.', 'error');
      return;
    }
    if (!Number.isInteger(this.cantidad) || this.cantidad <= 0) {
      this.mostrarMensaje('La cantidad debe ser un número entero mayor a cero.', 'error');
      return;
    }

    // Si alguien del área ya pidió esa clave y Almacén no la ha surtido completa, se avisa antes.
    const abierta = this.solicitudes().find((item) => item.clave === clave && item.estatus !== 'ATENDIDA');
    if (abierta) {
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

    this.enviando.set(true);
    this.api.crearSolicitud(clave, this.cantidad).subscribe({
      next: (solicitud) => {
        this.enviando.set(false);
        this.mostrarMensaje(
          `Solicitud ${folioSolicitud(solicitud.id)} enviada a Almacén: ${solicitud.cantidadSolicitada} cajas de ${solicitud.nombreGenerico} (${solicitud.clave}). Queda pendiente hasta que Almacén la despache.`,
          'success', 'Solicitud enviada'
        );
        this.solicitudes.set([solicitud, ...this.solicitudes()]);
        this.clave.set('');
        this.cantidad = 0;
      },
      error: (error) => {
        this.enviando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo enviar la solicitud.'), 'error');
      },
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
