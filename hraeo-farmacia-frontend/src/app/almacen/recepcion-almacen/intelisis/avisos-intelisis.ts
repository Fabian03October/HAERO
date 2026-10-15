import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AlmacenApi, AvisoIntelisis, mensajeDeError } from '../../almacen-api';
import { Notificaciones } from '../../../shared/notificaciones/notificaciones';
import { Session } from '../../../auth/session';

type Vista = 'REVISAR' | 'INFO' | 'REVISADOS';

const TIPO_TEXTO: Record<AvisoIntelisis['tipo'], string> = {
  PEDIDO_CANCELADO: 'Pedido cancelado',
  PARTIDA_CANCELADA: 'Partida cancelada',
  LOTE_CAMBIO: 'Cambio en lote',
  LOTE_POR_DAR_DE_ALTA: 'Lote por recibir',
  CLAVE_NUEVA: 'Clave nueva',
  PEDIDO_NUEVO: 'Pedido nuevo',
  CARGA_INICIAL: 'Carga inicial',
};

// Avisos de cambios en Intelisis (diseño aprobado). "Por revisar": pedido o partida
// cancelados (no recibir) o un cambio de Intelisis sobre un lote que Almacén ya tiene;
// HAERO no lo modificó. "Informativos": lo nuevo que llegó solo (claves, pedidos,
// lotes por recibir).
@Component({
  selector: 'app-avisos-intelisis',
  imports: [DatePipe],
  templateUrl: './avisos-intelisis.html',
  styleUrls: ['../recepcion-comun.css', '../pedidos/pedidos-recepcion.css', './avisos-intelisis.css'],
})
export class AvisosIntelisis {
  private readonly api = inject(AlmacenApi);
  private readonly notificaciones = inject(Notificaciones);
  protected readonly tipoTexto = TIPO_TEXTO;
  // Supervisión ve los avisos pero no los marca (eso lo hace Almacén).
  protected readonly soloLectura = inject(Session).usuarioActual()?.rol === 'SUPERVISION';

  readonly vista = signal<Vista>('REVISAR');
  readonly pendientes = signal<AvisoIntelisis[]>([]);
  readonly revisados = signal<AvisoIntelisis[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly trabajando = signal(false);

  readonly porRevisar = computed(() => this.pendientes().filter((aviso) => aviso.nivel === 'REVISAR'));
  readonly informativos = computed(() => this.pendientes().filter((aviso) => aviso.nivel === 'INFO'));
  readonly visibles = computed(() => {
    switch (this.vista()) {
      case 'REVISAR': return this.porRevisar();
      case 'INFO': return this.informativos();
      default: return this.revisados();
    }
  });

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set('');
    this.api.avisosIntelisis(true).subscribe({
      next: (avisos) => {
        this.pendientes.set(avisos);
        this.cargando.set(false);
        // Si no hay nada por revisar, se abre directo en los informativos.
        if (this.vista() === 'REVISAR' && !this.porRevisar().length && this.informativos().length) this.vista.set('INFO');
      },
      error: (e) => {
        this.error.set(mensajeDeError(e, 'No se pudieron cargar los avisos de Intelisis.'));
        this.cargando.set(false);
      },
    });
  }

  elegir(vista: Vista): void {
    this.vista.set(vista);
    if (vista === 'REVISADOS') {
      this.api.avisosIntelisis(false).subscribe({
        next: (avisos) => this.revisados.set(avisos),
        error: (e) => this.error.set(mensajeDeError(e, 'No se pudieron cargar los avisos revisados.')),
      });
    }
  }

  marcarRevisado(aviso: AvisoIntelisis): void {
    this.trabajando.set(true);
    this.api.marcarAvisoRevisado(aviso.id).subscribe({
      next: () => {
        this.pendientes.update((lista) => lista.filter((item) => item.id !== aviso.id));
        this.trabajando.set(false);
      },
      error: (e) => {
        this.notificaciones.error(mensajeDeError(e, 'No se pudo marcar el aviso.'));
        this.trabajando.set(false);
      },
    });
  }

  async marcarInformativos(): Promise<void> {
    const aceptado = await this.notificaciones.confirmar({
      titulo: 'Marcar informativos como revisados',
      mensaje: `Se marcarán ${this.informativos().length} avisos informativos. Los avisos por revisar no se tocan.`,
      textoAceptar: 'Marcar revisados',
    });
    if (!aceptado) return;
    this.trabajando.set(true);
    this.api.marcarInformativosRevisados().subscribe({
      next: (respuesta) => {
        this.pendientes.update((lista) => lista.filter((item) => item.nivel !== 'INFO'));
        this.notificaciones.exito('Listo', respuesta.mensaje);
        this.trabajando.set(false);
      },
      error: (e) => {
        this.notificaciones.error(mensajeDeError(e, 'No se pudieron marcar los avisos.'));
        this.trabajando.set(false);
      },
    });
  }
}
