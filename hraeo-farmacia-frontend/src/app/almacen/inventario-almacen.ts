import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { AlmacenApi, Existencia, mensajeDeError } from './almacen-api';

// Mismas columnas que la hoja de inventario del Drive de Almacén.
export interface LoteInventario {
  // Ids del backend (vienen de GET /api/almacen/existencias).
  existenciaId?: number;
  loteId?: number;
  clave: string;
  descripcion?: string;
  lote: string;
  // AAAA-MM-DD (la hoja trae el día) o AAAA-MM (captura manual).
  caducidad: string;
  proveedor: string;
  ubicacion: string;
  // Columna CANTIDAD de la hoja.
  cajas: number;
  // Columna FUENTE de la hoja (R-56, SADMI, SAEAC, U013…).
  fuente?: string;
  // AAAA-MM-DD.
  fechaIngreso?: string;
  // Lote al que sustituye cuando entró por un canje (HU18 CA3).
  loteOrigen?: string;
}

const CLAVE_CPM_CARGA = 'hraeo.almacen.cpm-carga.v2';

// Columna CONSUMO PROM. de la misma hoja.
const CPM_EJEMPLO: Record<string, number> = {
  '010.000.6059.01-1': 93.75,
  '010.000.6107.00-1': 526.43,
  '010.000.6111.01-1': 99.21,
  '010.000.6120.00-1': 6100,
  '010.000.6130.00-1': 8.36,
  '040.000.6140.01-1': 22.73,
  '040.000.6141.01-1': 63.92,
  '010.000.6144.00-1': 182.69,
  '010.000.6150.00-1': 270,
  '010.000.6153.00-1': 5200,
  '010.000.6154.00-1': 8776,
  '010.000.6157.00-1': 132.54,
  '010.000.6165.00-1': 363.94,
  '010.000.6169.00-1': 1.05,
  '010.000.6117.01-1': 118.5,
  '010.000.6172.00-1': 771.42,
  '010.000.6173.00-1': 3683.33,
};

// Inventario de Almacén leído del backend (GET /api/almacen/existencias). Las
// pantallas usan lotes(), totalCajas() y totalClaves(); después de cualquier
// entrada, carga o despacho se llama a recargar() para ver lo que quedó en la base.
// El CPM de la hoja (columna CONSUMO PROM.) sigue en localStorage porque el
// backend aún no lo guarda.
@Injectable({ providedIn: 'root' })
export class InventarioAlmacen {
  private readonly api = inject(AlmacenApi);

  private readonly _lotes = signal<LoteInventario[]>([]);
  // Columna CONSUMO PROM. de la hoja, por clave. Sirve de CPM mientras el sistema
  // no tenga salidas propias de esa clave (HU16).
  private readonly _cpmCarga = signal<Record<string, number>>(this.cargarCpm());

  readonly lotes = this._lotes.asReadonly();
  readonly cpmCarga = this._cpmCarga.asReadonly();
  readonly totalCajas = computed(() => this._lotes().reduce((total, lote) => total + lote.cajas, 0));
  readonly totalClaves = computed(() => new Set(this._lotes().map((lote) => lote.clave)).size);
  readonly cargando = signal(false);
  readonly error = signal('');

  constructor() {
    this.recargar();
  }

  /** Vuelve a leer las existencias del backend. Las ubicaciones vacías no se muestran. */
  recargar(): void {
    this.cargando.set(true);
    this.leer().subscribe({
      next: () => this.cargando.set(false),
      error: (error) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo cargar el inventario.'));
      },
    });
  }

  /** Igual que recargar(), pero quien llama puede esperar a que termine. */
  leer(): Observable<LoteInventario[]> {
    return this.api.listarExistencias().pipe(
      map((existencias) => existencias.filter((fila) => fila.cajas > 0).map(aLoteInventario)),
      tap((lotes) => {
        this._lotes.set(lotes);
        this.error.set('');
      }),
    );
  }

  guardarCpmCarga(cpm: Record<string, number>): void {
    const actualizado = { ...this._cpmCarga(), ...cpm };
    this._cpmCarga.set(actualizado);
    try {
      localStorage.setItem(CLAVE_CPM_CARGA, JSON.stringify(actualizado));
    } catch {
      // Sin localStorage el CPM de la hoja solo se conserva mientras la pestaña esté abierta.
    }
  }

  private cargarCpm(): Record<string, number> {
    try {
      const guardado = localStorage.getItem(CLAVE_CPM_CARGA);
      const cpm = guardado ? JSON.parse(guardado) : null;
      if (cpm && typeof cpm === 'object' && !Array.isArray(cpm)) return cpm;
    } catch {
      // Dato dañado o localStorage bloqueado: se usa el CPM de la hoja de ejemplo.
    }
    return { ...CPM_EJEMPLO };
  }
}

function aLoteInventario(fila: Existencia): LoteInventario {
  return {
    existenciaId: fila.existenciaId,
    loteId: fila.loteId,
    clave: fila.clave,
    descripcion: fila.nombreGenerico,
    lote: fila.numeroLote,
    caducidad: fila.caducidad,
    proveedor: fila.proveedor ?? '',
    ubicacion: fila.ubicacion,
    cajas: fila.cajas,
  };
}
