import { Injectable, computed, inject, signal } from '@angular/core';
import { Alerta as AlertaDto, AlmacenApi, Rotacion as RotacionDto } from './almacen-api';
import { SolicitudesAlmacen, mesActual } from './solicitudes-almacen';

// Meses de abasto que definen el stock mínimo y máximo (HU17 CA1). El backend
// usa los mismos valores para calcular stockMinimo/stockMaximo.
export const MESES_STOCK_MINIMO = 3;
export const MESES_STOCK_MAXIMO = 6;
const MESES_VENTANA_CPM = 6;

export type EstadoStock = 'DESABASTO' | 'SOBREABASTO' | 'NORMAL' | 'SIN_CONSUMO';
export type TipoAlerta = 'DESABASTO' | 'SOBREABASTO';

export const ESTADO_STOCK_TEXTO: Record<EstadoStock, string> = {
  DESABASTO: 'Riesgo de desabasto',
  SOBREABASTO: 'Riesgo de sobreabasto',
  NORMAL: 'En rango',
  SIN_CONSUMO: 'Sin consumo',
};

export interface ConsumoMes {
  mes: string;
  cajas: number;
}

export interface RotacionClave {
  clave: string;
  medicamento: string;
  cpm: number;
  // SISTEMA = salidas registradas; HOJA = columna CONSUMO PROM. del inventario cargado.
  origenCpm: 'SISTEMA' | 'HOJA';
  meses: ConsumoMes[];
  mesesHistorial: number;
  consumoMesActual: number;
  cajasExcluidas: number;
  existencia: number;
  minimo: number;
  maximo: number;
  mesesAbasto: number | null;
  estado: EstadoStock;
}

export interface AlertaStock {
  clave: string;
  tipo: TipoAlerta;
  inicio: string;
  fin?: string;
  existencia: number;
  limite: number;
}

/**
 * Rotación y alertas (HU16, HU17). El CPM y las alertas los calcula el
 * backend (ServicioRotacion): es la única fuente de verdad, para que Almacén
 * y Supervisión vean siempre el mismo número sin importar desde qué equipo
 * consulten. Aquí solo se traduce la respuesta al formato que usan las
 * pantallas y se agregan un par de datos de contexto (consumo del mes en
 * curso, cajas excluidas) a partir de los movimientos que ya están cargados
 * para otras pantallas — son solo informativos, no afectan el CPM ni las alertas.
 */
@Injectable({ providedIn: 'root' })
export class RotacionAlmacen {
  private readonly api = inject(AlmacenApi);
  private readonly solicitudes = inject(SolicitudesAlmacen);

  private readonly _rotacion = signal<RotacionDto[]>([]);
  private readonly _alertasActivas = signal<AlertaDto[]>([]);
  private readonly _alertasCerradas = signal<AlertaDto[]>([]);

  constructor() {
    this.recargar();
  }

  /** Vuelve a leer el CPM y las alertas del backend. Llamar tras cualquier entrada, despacho o ajuste. */
  recargar(): void {
    this.api.listarRotacion().subscribe({ next: (lista) => this._rotacion.set(lista), error: () => {} });
    this.api.listarAlertas(true).subscribe({ next: (lista) => this._alertasActivas.set(lista), error: () => {} });
    this.api.listarAlertas(false).subscribe({ next: (lista) => this._alertasCerradas.set(lista), error: () => {} });
  }

  // Meses cerrados de la ventana, del más antiguo al más reciente (AAAA-MM). Solo
  // para mostrar las etiquetas de la gráfica; los datos de cada mes vienen del backend.
  readonly ventana = computed(() => {
    const [anio, mes] = mesActual().split('-').map(Number);
    return Array.from({ length: MESES_VENTANA_CPM }, (_, indice) => {
      const fecha = new Date(anio, mes - 1 - (MESES_VENTANA_CPM - indice), 1);
      return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}`;
    });
  });

  readonly rotacion = computed<RotacionClave[]>(() => {
    const actual = mesActual();
    const ventana = this.ventana();
    const salidas = this.solicitudes.salidas();

    return this._rotacion().map((item) => {
      const deLaClave = salidas.filter((salida) => salida.clave === item.clave);
      const aFarmacia = deLaClave.filter((salida) => salida.tipo === 'FARMACIA');

      return {
        clave: item.clave,
        medicamento: item.nombreGenerico,
        cpm: item.cpm,
        origenCpm: item.origenCpm,
        meses: item.meses,
        mesesHistorial: item.mesesHistorial,
        consumoMesActual: this.sumar(aFarmacia.filter((salida) => salida.fecha.startsWith(actual))),
        cajasExcluidas: this.sumar(deLaClave.filter((salida) => salida.tipo !== 'FARMACIA' && ventana.includes(salida.fecha.slice(0, 7)))),
        existencia: item.existenciaActual,
        minimo: item.stockMinimo,
        maximo: item.stockMaximo,
        mesesAbasto: item.cpm ? Math.round((item.existenciaActual / item.cpm) * 10) / 10 : null,
        estado: item.estado,
      };
    });
  });

  readonly alertasActivas = computed<AlertaStock[]>(() =>
    this._alertasActivas().map(aAlertaStock).sort((a, b) => a.inicio.localeCompare(b.inicio)),
  );

  readonly alertasCerradas = computed<AlertaStock[]>(() =>
    this._alertasCerradas().map(aAlertaStock).sort((a, b) => (b.fin ?? '').localeCompare(a.fin ?? '')),
  );

  buscar(clave: string): RotacionClave | undefined {
    return this.rotacion().find((item) => item.clave === clave);
  }

  private sumar(salidas: { cajas: number }[]): number {
    return salidas.reduce((suma, salida) => suma + salida.cajas, 0);
  }
}

function aAlertaStock(alerta: AlertaDto): AlertaStock {
  return {
    clave: alerta.clave,
    tipo: alerta.tipo,
    inicio: alerta.fechaInicio,
    fin: alerta.fechaFin ?? undefined,
    existencia: alerta.existencia,
    limite: alerta.limite,
  };
}

// AAAA-MM → "abr 2026".
export function nombreMes(mes: string): string {
  const [anio, numero] = mes.split('-').map(Number);
  return new Date(anio, numero - 1, 1).toLocaleDateString('es-MX', { month: 'short', year: 'numeric' }).replace('.', '');
}
