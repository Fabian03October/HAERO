import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MESES_STOCK_MAXIMO, MESES_STOCK_MINIMO, RotacionAlmacen, RotacionClave, nombreMes } from '../../rotacion-almacen';

type FiltroCpm = 'TODAS' | 'SISTEMA' | 'HOJA' | 'SIN_CONSUMO' | 'ABASTO_BAJO' | 'ABASTO_ALTO' | 'SIN_SALIDAS_MES' | 'CONSUMO_ALTO';
type OrdenCpm = 'clave' | 'cpm' | 'abasto' | 'mes';

// Qué claves entran en cada filtro del listado general.
const CUMPLE: Record<FiltroCpm, (fila: RotacionClave) => boolean> = {
  TODAS: () => true,
  SISTEMA: (fila) => fila.cpm > 0 && fila.origenCpm === 'SISTEMA',
  HOJA: (fila) => fila.origenCpm === 'HOJA',
  SIN_CONSUMO: (fila) => !fila.cpm,
  ABASTO_BAJO: (fila) => fila.mesesAbasto !== null && fila.mesesAbasto < MESES_STOCK_MINIMO,
  ABASTO_ALTO: (fila) => fila.mesesAbasto !== null && fila.mesesAbasto > MESES_STOCK_MAXIMO,
  SIN_SALIDAS_MES: (fila) => fila.cpm > 0 && fila.consumoMesActual === 0,
  CONSUMO_ALTO: (fila) => fila.cpm > 0 && fila.consumoMesActual > fila.cpm,
};

// Rotación · CPM por clave (HU16). Consulta individual con el consumo de cada mes
// y listado general de todas las claves (CA3).
@Component({
  selector: 'app-cpm-clave',
  imports: [FormsModule],
  templateUrl: './cpm-clave.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../../despacho/despacho-comun.css', './cpm-clave.css'],
})
export class CpmClave {
  protected readonly rotacion = inject(RotacionAlmacen);
  protected readonly nombreMes = nombreMes;

  readonly claveSeleccionada = signal('');
  readonly detalle = computed(() => this.rotacion.buscar(this.claveSeleccionada()));
  readonly mesMayor = computed(() => Math.max(1, ...(this.detalle()?.meses.map((mes) => mes.cajas) ?? [])));

  protected readonly mesesMinimo = MESES_STOCK_MINIMO;
  protected readonly mesesMaximo = MESES_STOCK_MAXIMO;

  readonly filtro = signal('');
  readonly tipoFiltro = signal<FiltroCpm>('TODAS');
  readonly orden = signal<OrdenCpm>('clave');

  readonly filtros = computed(() => {
    const filas = this.rotacion.rotacion();
    const opcion = (valor: FiltroCpm, etiqueta: string) => ({ valor, etiqueta, total: filas.filter(CUMPLE[valor]).length });
    return [
      opcion('TODAS', 'Todas'),
      opcion('SISTEMA', 'CPM del sistema'),
      opcion('HOJA', 'CPM de la hoja'),
      opcion('SIN_CONSUMO', 'Sin consumo'),
      opcion('ABASTO_BAJO', `Menos de ${MESES_STOCK_MINIMO} meses de abasto`),
      opcion('ABASTO_ALTO', `Más de ${MESES_STOCK_MAXIMO} meses de abasto`),
      opcion('SIN_SALIDAS_MES', 'Sin salidas este mes'),
      opcion('CONSUMO_ALTO', 'Mes arriba del CPM'),
    ];
  });

  readonly listado = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    const cumple = CUMPLE[this.tipoFiltro()];
    const filas = this.rotacion
      .rotacion()
      .filter(cumple)
      .filter((fila) => !texto || `${fila.clave} ${fila.medicamento}`.toLowerCase().includes(texto));
    const orden = this.orden();
    return [...filas].sort((a, b) => {
      if (orden === 'cpm') return b.cpm - a.cpm || a.clave.localeCompare(b.clave);
      if (orden === 'mes') return b.consumoMesActual - a.consumoMesActual || a.clave.localeCompare(b.clave);
      if (orden === 'abasto') return (a.mesesAbasto ?? Infinity) - (b.mesesAbasto ?? Infinity) || a.clave.localeCompare(b.clave);
      return a.clave.localeCompare(b.clave);
    });
  });

  readonly periodo = computed(() => {
    const ventana = this.rotacion.ventana();
    return `${nombreMes(ventana[0])} – ${nombreMes(ventana[ventana.length - 1])}`;
  });

  constructor() {
    // Se propone la primera clave con CPM en cuanto llegan los datos del backend.
    effect(() => {
      const primera = this.rotacion.rotacion().find((item) => item.cpm)?.clave;
      if (primera && !untracked(() => this.claveSeleccionada())) this.claveSeleccionada.set(primera);
    });
  }

  altura(cajas: number): number {
    return Math.round((cajas / this.mesMayor()) * 100);
  }
}
