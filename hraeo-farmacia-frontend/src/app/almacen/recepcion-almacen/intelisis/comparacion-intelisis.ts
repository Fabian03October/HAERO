import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { AlmacenApi, ComparacionClave, EstadoComparacion, EstadoIntelisis, mensajeDeError } from '../../almacen-api';

// Cada cuánto se revisa el avance mientras corre una sincronización.
const MS_REVISAR_AVANCE = 3000;

const ESTADO_TEXTO: Record<EstadoComparacion, string> = {
  EN_AMBOS: 'En ambos',
  SOLO_HAERO: 'Solo en HAERO',
  SOLO_INTELISIS: 'Solo en Intelisis',
};

// Recepción · Comparar con Intelisis (integración, paso 3). Pone lado a lado lo
// cargado por Excel y lo que hay en Intelisis, antes de reemplazar la carga
// (HU21). Lee el espejo de HAERO, así que abre rápido aunque Intelisis no responda.
// La cantidad de Intelisis va en su unidad (piezas, mg, ml...): la conversión a
// cajas espera la respuesta de Almacén y Farmacia.
@Component({
  selector: 'app-comparacion-intelisis',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './comparacion-intelisis.html',
  styleUrls: ['../recepcion-comun.css', '../pedidos/pedidos-recepcion.css', './comparacion-intelisis.css'],
})
export class ComparacionIntelisis {
  private readonly api = inject(AlmacenApi);
  protected readonly estadoTexto = ESTADO_TEXTO;

  readonly estado = signal<EstadoIntelisis | null>(null);
  readonly filas = signal<ComparacionClave[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly aviso = signal('');

  readonly filtroEstado = signal<EstadoComparacion | 'TODOS'>('TODOS');
  readonly buscar = signal('');

  private temporizador: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.detenerRevision());
    this.cargar();
  }

  readonly filtros = computed(() => {
    const filas = this.filas();
    const contar = (estado: EstadoComparacion) => filas.filter((fila) => fila.estado === estado).length;
    return [
      { valor: 'TODOS' as const, etiqueta: 'Todas', total: filas.length },
      { valor: 'EN_AMBOS' as const, etiqueta: 'En ambos', total: contar('EN_AMBOS') },
      { valor: 'SOLO_HAERO' as const, etiqueta: 'Solo en HAERO', total: contar('SOLO_HAERO') },
      { valor: 'SOLO_INTELISIS' as const, etiqueta: 'Solo en Intelisis', total: contar('SOLO_INTELISIS') },
    ];
  });

  readonly visibles = computed(() => {
    const estado = this.filtroEstado();
    const texto = this.buscar().trim().toLowerCase();
    return this.filas()
      .filter((fila) => estado === 'TODOS' || fila.estado === estado)
      .filter((fila) => !texto || `${fila.clave} ${fila.nombre ?? ''}`.toLowerCase().includes(texto));
  });

  cargar(): void {
    this.cargando.set(true);
    this.error.set('');
    this.api.estadoIntelisis().subscribe({
      next: (estado) => {
        this.estado.set(estado);
        if (estado.enCurso) this.revisarAvance();
      },
      error: (e) => this.error.set(mensajeDeError(e, 'No se pudo consultar el estado de la integración.')),
    });
    this.api.comparacionIntelisis().subscribe({
      next: (filas) => {
        this.filas.set(filas);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e, 'No se pudo cargar la comparación.'));
        this.cargando.set(false);
      },
    });
  }

  sincronizar(): void {
    this.aviso.set('');
    this.error.set('');
    this.api.sincronizarIntelisis().subscribe({
      next: (respuesta) => {
        this.aviso.set(respuesta.mensaje);
        this.estado.update((estado) => (estado ? { ...estado, enCurso: true } : estado));
        this.revisarAvance();
      },
      error: (e) => this.error.set(mensajeDeError(e, 'No se pudo iniciar la sincronización.')),
    });
  }

  /** Mientras corre la sincronización revisa el estado; al terminar recarga la tabla. */
  private revisarAvance(): void {
    this.detenerRevision();
    this.temporizador = setTimeout(() => {
      this.api.estadoIntelisis().subscribe({
        next: (estado) => {
          this.estado.set(estado);
          if (estado.enCurso) {
            this.revisarAvance();
          } else {
            this.aviso.set(estado.ultimoEstatus === 'OK' ? 'Sincronización terminada.' : '');
            this.cargar();
          }
        },
        error: () => this.revisarAvance(),
      });
    }, MS_REVISAR_AVANCE);
  }

  private detenerRevision(): void {
    if (this.temporizador) clearTimeout(this.temporizador);
    this.temporizador = null;
  }

  /**
   * Piezas estimadas cuando Intelisis guarda la clave en mg/ml/UI: parece que la
   * "Conversion" usa UnidadCantidad como factor (pendiente de confirmar).
   */
  piezasEstimadas(fila: ComparacionClave): number | null {
    if (fila.intelisisCantidad == null || !fila.intelisisUnidadCantidad || fila.intelisisUnidadCantidad <= 1) return null;
    return Math.round(fila.intelisisCantidad / fila.intelisisUnidadCantidad);
  }

  segundos(ms: number | null): string {
    return ms == null ? '' : `${(ms / 1000).toFixed(1)} s`;
  }
}
