import { Injectable, computed, inject, signal } from '@angular/core';
import { AlmacenApi, Medicamento, Movimiento, Solicitud, folioSolicitud } from './almacen-api';
import { InventarioAlmacen } from './inventario-almacen';

// Solo FARMACIA cuenta como consumo para el CPM (HU16 CA4). Los demás tipos los
// registrarán los movimientos especiales (HU18 a HU20).
export type TipoSalida = 'FARMACIA' | 'PRESTAMO' | 'TRANSFERENCIA' | 'BAJA_CADUCIDAD' | 'CANJE';

export const TIPO_SALIDA_TEXTO: Record<TipoSalida, string> = {
  FARMACIA: 'Salida a Farmacia',
  PRESTAMO: 'Préstamo',
  TRANSFERENCIA: 'Transferencia',
  BAJA_CADUCIDAD: 'A caducados',
  CANJE: 'Canje de lote',
};

// Movimiento de salida de Almacén (HU14 CA5).
export interface MovimientoSalida {
  tipo: TipoSalida;
  fecha: string;
  usuario: string;
  folio: string;
  clave: string;
  lote: string;
  caducidad: string;
  ubicacion: string;
  cajas: number;
  // Solo préstamos y transferencias (HU20 CA4).
  institucion?: string;
}

// Datos de Almacén compartidos por varias pantallas, todos leídos del backend:
// bandeja de solicitudes pendientes, catálogo de medicamentos e historial de
// salidas a Farmacia (Salidas registradas y CPM).
@Injectable({ providedIn: 'root' })
export class SolicitudesAlmacen {
  private readonly api = inject(AlmacenApi);
  private readonly inventario = inject(InventarioAlmacen);

  private readonly _porAtender = signal<Solicitud[]>([]);
  private readonly _catalogo = signal<Medicamento[]>([]);
  private readonly _salidas = signal<MovimientoSalida[]>([]);

  readonly porAtender = this._porAtender.asReadonly();
  readonly catalogo = this._catalogo.asReadonly();
  readonly salidas = this._salidas.asReadonly();

  constructor() {
    this.recargar();
  }

  /** Vuelve a leer la bandeja, el catálogo y las salidas del backend. */
  recargar(): void {
    this.api.bandejaSolicitudes().subscribe({ next: (lista) => this._porAtender.set(lista), error: () => {} });
    this.api.listarMedicamentos().subscribe({ next: (lista) => this._catalogo.set(lista), error: () => {} });
    this.recargarSalidas();
  }

  /**
   * Todo lo que sale del Almacén: despachos a Farmacia y, por separado, préstamos,
   * transferencias, bajas a caducados y canjes (HU20 CA3). El CPM solo cuenta FARMACIA.
   */
  recargarSalidas(): void {
    this.api.listarMovimientos().subscribe({
      next: (lista) => this._salidas.set(lista.map(aSalida).filter((salida): salida is MovimientoSalida => salida !== null)),
      error: () => {},
    });
  }

  readonly clavesConocidas = computed(() => {
    const claves = new Set(this._catalogo().map((medicamento) => medicamento.clave));
    this.inventario.lotes().forEach((lote) => claves.add(lote.clave));
    return [...claves].sort();
  });

  nombreMedicamento(clave: string): string {
    const medicamento = this._catalogo().find((item) => item.clave === clave);
    if (medicamento) return medicamento.nombreGenerico;
    const descripcion = this.inventario.lotes().find((lote) => lote.clave === clave && lote.descripcion)?.descripcion;
    return descripcion ? nombreCorto(descripcion) : '';
  }
}

// AAAA-MM-DD → DD/MM/AAAA y AAAA-MM → MM/AAAA. La hoja del Drive trae el día;
// las entradas capturadas a mano solo el mes, como viene impreso en la caja.
export function formatoCaducidad(caducidad: string): string {
  const [anio, mes, dia] = (caducidad ?? '').split('-');
  if (anio && mes && dia) return `${dia}/${mes}/${anio}`;
  return anio && mes ? `${mes}/${anio}` : caducidad;
}

// Con día, el lote vence al terminar ese día; con solo mes, al terminar el mes.
export function estaVencido(caducidad: string): boolean {
  const hoy = fechaLocal().slice(0, 10);
  return caducidad.length > 7 ? caducidad < hoy : caducidad < hoy.slice(0, 7);
}

// "Lactulosa. Jarabe. Cada 100 ml contienen…" → "Lactulosa. Jarabe".
export function nombreCorto(descripcion: string): string {
  const corto = descripcion.split(/[.,\s]+cada\s/i)[0].trim();
  return corto.length > 60 ? `${corto.slice(0, 57).trim()}…` : corto;
}

// Fecha y hora local sin zona (AAAA-MM-DDTHH:mm:ss).
export function fechaLocal(fecha = new Date()): string {
  return new Date(fecha.getTime() - fecha.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}

// Mes actual como AAAA-MM; un lote con caducidad anterior ya está vencido.
export function mesActual(): string {
  return fechaLocal().slice(0, 7);
}

const FOLIO_POR_TIPO: Partial<Record<Movimiento['tipo'], string>> = {
  PRESTAMO: 'Préstamo',
  TRANSFERENCIA: 'Transferencia',
  BAJA_CADUCIDAD: 'Apartado de caducados',
  CANJE: 'Canje de lote',
};

// Movimiento del backend → salida del historial; null si no es una salida.
function aSalida(movimiento: Movimiento): MovimientoSalida | null {
  let tipo: TipoSalida;
  if (movimiento.tipo === 'SALIDA') tipo = 'FARMACIA';
  else if (movimiento.sentido === 'SALIDA' && movimiento.tipo in FOLIO_POR_TIPO) tipo = movimiento.tipo as TipoSalida;
  else return null;
  return {
    tipo,
    fecha: movimiento.fecha,
    usuario: movimiento.usuario,
    folio: movimiento.solicitudId ? folioSolicitud(movimiento.solicitudId) : FOLIO_POR_TIPO[movimiento.tipo] ?? '—',
    clave: movimiento.clave,
    lote: movimiento.numeroLote,
    caducidad: movimiento.caducidad,
    ubicacion: movimiento.ubicacion,
    cajas: movimiento.cajas,
    institucion: movimiento.institucion ?? undefined,
  };
}
