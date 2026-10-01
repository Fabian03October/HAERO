import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { InventarioAlmacen } from './inventario-almacen';
import { MovimientoSalida, SolicitudesAlmacen, estaVencido, fechaLocal, mesActual } from './solicitudes-almacen';
import { sincronizarEntrePestanas } from './sincronizar-pestanas';

// Meses de abasto que definen el stock mínimo y máximo (HU17 CA1).
export const MESES_STOCK_MINIMO = 3;
export const MESES_STOCK_MAXIMO = 6;
// Ventana del CPM: los últimos 6 meses cerrados (ajuste sugerido a HU16).
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

// Una alerta queda abierta desde que la clave cruza su límite hasta que vuelve
// al rango (PU-12, PU-13). Así se sabe desde cuándo está en riesgo (HU17 CA5).
export interface AlertaStock {
  clave: string;
  tipo: TipoAlerta;
  inicio: string;
  fin?: string;
  existencia: number;
  limite: number;
}

const CLAVE_ALERTAS = 'hraeo.almacen.alertas.v2';

// Rotación y alertas (HU16, HU17). El CPM se calcula con las salidas reales a
// Farmacia; al registrar una salida o una entrada todo se recalcula solo (CA2).
@Injectable({ providedIn: 'root' })
export class RotacionAlmacen {
  private readonly inventario = inject(InventarioAlmacen);
  private readonly solicitudes = inject(SolicitudesAlmacen);

  private readonly _alertas = signal<AlertaStock[]>(this.cargar());

  readonly alertas = this._alertas.asReadonly();
  readonly alertasActivas = computed(() => this._alertas().filter((alerta) => !alerta.fin).sort((a, b) => a.inicio.localeCompare(b.inicio)));
  readonly alertasCerradas = computed(() => this._alertas().filter((alerta) => alerta.fin).sort((a, b) => b.fin!.localeCompare(a.fin!)));

  // Meses cerrados de la ventana, del más antiguo al más reciente (AAAA-MM).
  readonly ventana = computed(() => {
    const [anio, mes] = mesActual().split('-').map(Number);
    return Array.from({ length: MESES_VENTANA_CPM }, (_, indice) => {
      const fecha = new Date(anio, mes - 1 - (MESES_VENTANA_CPM - indice), 1);
      return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}`;
    });
  });

  readonly rotacion = computed<RotacionClave[]>(() => {
    const ventana = this.ventana();
    const actual = mesActual();
    const salidas = this.solicitudes.salidas();
    const lotes = this.inventario.lotes();
    const cpmHoja = this.inventario.cpmCarga();

    // Meses cerrados desde que hay registros en el sistema, para no dividir entre 6
    // cuando todavía no existen 6 meses de historial.
    const primerMes = salidas.reduce((minimo, salida) => (salida.fecha.slice(0, 7) < minimo ? salida.fecha.slice(0, 7) : minimo), actual);
    const mesesHistorial = ventana.filter((mes) => mes >= primerMes).length;

    return this.solicitudes.clavesConocidas().map((clave) => {
      const deLaClave = salidas.filter((salida) => salida.clave === clave);
      const aFarmacia = deLaClave.filter((salida) => this.esConsumo(salida));
      const meses = ventana.map((mes) => ({ mes, cajas: this.sumar(aFarmacia.filter((salida) => salida.fecha.startsWith(mes))) }));
      const total = meses.reduce((suma, mes) => suma + mes.cajas, 0);
      // Sin salidas propias todavía, se usa el consumo promedio que trae la hoja del Drive.
      const usarHoja = !total && cpmHoja[clave] > 0;
      const cpm = usarHoja ? cpmHoja[clave] : mesesHistorial ? Math.round((total / mesesHistorial) * 10) / 10 : 0;

      const existencia = lotes.filter((lote) => lote.clave === clave && !estaVencido(lote.caducidad)).reduce((suma, lote) => suma + lote.cajas, 0);
      const minimo = Math.ceil(cpm * MESES_STOCK_MINIMO);
      const maximo = Math.ceil(cpm * MESES_STOCK_MAXIMO);
      let estado: EstadoStock = 'NORMAL';
      if (!cpm) estado = 'SIN_CONSUMO';
      else if (existencia <= minimo) estado = 'DESABASTO';
      else if (existencia >= maximo) estado = 'SOBREABASTO';

      return {
        clave,
        medicamento: this.solicitudes.nombreMedicamento(clave),
        cpm,
        origenCpm: usarHoja ? 'HOJA' : 'SISTEMA',
        meses,
        mesesHistorial,
        consumoMesActual: this.sumar(aFarmacia.filter((salida) => salida.fecha.startsWith(actual))),
        cajasExcluidas: this.sumar(deLaClave.filter((salida) => !this.esConsumo(salida) && ventana.includes(salida.fecha.slice(0, 7)))),
        existencia,
        minimo,
        maximo,
        mesesAbasto: cpm ? Math.round((existencia / cpm) * 10) / 10 : null,
        estado,
      };
    });
  });

  constructor() {
    sincronizarEntrePestanas(CLAVE_ALERTAS, this._alertas);
    // Abre o cierra alertas cada vez que cambia la rotación (salidas o existencias).
    effect(() => {
      const rotacion = this.rotacion();
      untracked(() => this.sincronizarAlertas(rotacion));
    });
  }

  buscar(clave: string): RotacionClave | undefined {
    return this.rotacion().find((item) => item.clave === clave);
  }

  private sincronizarAlertas(rotacion: RotacionClave[]): void {
    const ahora = fechaLocal();
    let cambio = false;
    const alertas = this._alertas().map((alerta) => {
      if (alerta.fin) return alerta;
      const estado = rotacion.find((item) => item.clave === alerta.clave)?.estado;
      if (estado === alerta.tipo) return alerta;
      cambio = true;
      return { ...alerta, fin: ahora };
    });

    for (const item of rotacion) {
      if (item.estado !== 'DESABASTO' && item.estado !== 'SOBREABASTO') continue;
      if (alertas.some((alerta) => !alerta.fin && alerta.clave === item.clave && alerta.tipo === item.estado)) continue;
      alertas.push({
        clave: item.clave,
        tipo: item.estado,
        inicio: ahora,
        existencia: item.existencia,
        limite: item.estado === 'DESABASTO' ? item.minimo : item.maximo,
      });
      cambio = true;
    }

    if (!cambio) return;
    this._alertas.set(alertas);
    try {
      localStorage.setItem(CLAVE_ALERTAS, JSON.stringify(alertas));
    } catch {
      // Sin localStorage las alertas solo se conservan mientras la pestaña esté abierta.
    }
  }

  private esConsumo(salida: MovimientoSalida): boolean {
    return (salida.tipo ?? 'FARMACIA') === 'FARMACIA';
  }

  private sumar(salidas: MovimientoSalida[]): number {
    return salidas.reduce((suma, salida) => suma + salida.cajas, 0);
  }

  private cargar(): AlertaStock[] {
    try {
      const guardado = localStorage.getItem(CLAVE_ALERTAS);
      const alertas = guardado ? JSON.parse(guardado) : null;
      if (Array.isArray(alertas)) return alertas;
    } catch {
      // Dato dañado o localStorage bloqueado: se empieza sin alertas.
    }
    return [];
  }
}

// AAAA-MM → "abr 2026".
export function nombreMes(mes: string): string {
  const [anio, numero] = mes.split('-').map(Number);
  return new Date(anio, numero - 1, 1).toLocaleDateString('es-MX', { month: 'short', year: 'numeric' }).replace('.', '');
}
