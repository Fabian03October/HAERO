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

// Inventario de Almacén leído del backend (GET /api/almacen/existencias). Las
// pantallas usan lotes(), totalCajas() y totalClaves(); después de cualquier
// entrada, carga o despacho se llama a recargar() para ver lo que quedó en la base.
// El CPM de la hoja (columna CONSUMO PROM.) se envía al backend en la carga
// inicial y se consulta vía RotacionAlmacen (HU16): aquí ya no se guarda.
@Injectable({ providedIn: 'root' })
export class InventarioAlmacen {
  private readonly api = inject(AlmacenApi);

  private readonly _lotes = signal<LoteInventario[]>([]);

  readonly lotes = this._lotes.asReadonly();
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
