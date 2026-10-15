import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { AlmacenApi, ComparacionClave, EstadoComparacion, EstadoIntelisis, mensajeDeError } from '../../almacen-api';
import { AvisosIntelisis } from './avisos-intelisis';
import { Session } from '../../../auth/session';

// Cada cuánto se revisa el avance mientras corre una sincronización.
const MS_REVISAR_AVANCE = 3000;

const ESTADO_TEXTO: Record<EstadoComparacion, string> = {
  EN_AMBOS: 'En ambos',
  SOLO_HAERO: 'Solo en HAERO',
  SOLO_INTELISIS: 'Solo en Intelisis',
};

// Intelisis: avisos y comparación (paso 4 del diseño aprobado). Pone lado a lado, en
// cajas, lo que Almacén dio de alta y recibió en HAERO y lo que dice Intelisis (por
// ejemplo 20 contra 50), para que Almacén y Supervisión vean qué falta dar de alta o
// qué hay que ajustar en Intelisis. HAERO lleva el control. Lee la copia de consulta
// en HAERO, así que abre rápido aunque Intelisis no responda.
@Component({
  selector: 'app-comparacion-intelisis',
  imports: [DatePipe, DecimalPipe, AvisosIntelisis],
  templateUrl: './comparacion-intelisis.html',
  styleUrls: ['../recepcion-comun.css', '../pedidos/pedidos-recepcion.css', './comparacion-intelisis.css'],
})
export class ComparacionIntelisis {
  private readonly api = inject(AlmacenApi);
  protected readonly estadoTexto = ESTADO_TEXTO;
  // Supervisión la ve en solo consulta: sin "Sincronizar ahora".
  protected readonly soloLectura = inject(Session).usuarioActual()?.rol === 'SUPERVISION';

  readonly estado = signal<EstadoIntelisis | null>(null);
  readonly filas = signal<ComparacionClave[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly aviso = signal('');

  readonly filtroEstado = signal<EstadoComparacion | 'TODOS' | 'DIFERENCIA'>('DIFERENCIA');
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
      { valor: 'DIFERENCIA' as const, etiqueta: 'Con diferencia', total: filas.filter((fila) => this.conDiferencia(fila)).length },
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
      .filter((fila) => estado === 'TODOS' || (estado === 'DIFERENCIA' ? this.conDiferencia(fila) : fila.estado === estado))
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
   * Cajas de más en Intelisis (positivo) o en HAERO (negativo); 0 si cuadran y null si
   * Intelisis tiene la clave pero no se sabe cuántas piezas trae la caja.
   */
  diferencia(fila: ComparacionClave): number | null {
    if (fila.intelisisPiezas != null && fila.intelisisCajas == null) return null;
    return Math.round(((fila.intelisisCajas ?? 0) - (fila.haeroCajas ?? 0)) * 100) / 100;
  }

  conDiferencia(fila: ComparacionClave): boolean {
    const diferencia = this.diferencia(fila);
    return diferencia === null || diferencia !== 0;
  }

  segundos(ms: number | null): string {
    return ms == null ? '' : `${(ms / 1000).toFixed(1)} s`;
  }
}
