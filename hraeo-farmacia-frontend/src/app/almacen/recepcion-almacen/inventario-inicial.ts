import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import * as XLSX from 'xlsx';
import { InventarioAlmacen } from '../inventario-almacen';
import { formatoCaducidad } from '../solicitudes-almacen';
import { RotacionAlmacen } from '../rotacion-almacen';
import { CapturaIntelisis } from './captura-intelisis/captura-intelisis';

// Mismo orden que la hoja del Drive; Fuente es el registro de Intelisis del que salió el lote.
const ENCABEZADOS_EXCEL = ['CLAVE', 'DESCRIPCION', 'LOTE', 'CADUCIDAD', 'CANTIDAD', 'CONSUMO PROM.', 'UBICACION', 'FUENTE', 'FECHA DE INGRESO', 'PROVEEDOR'];

// Recepción · Inventario (HU21). Es una página hija de Recepción, por eso no
// incluye barra superior ni menú lateral (los pone el contenedor).
// Diseño aprobado: ya no se carga un Excel. Lo que está en los anaqueles se da de
// alta escaneándolo y eligiéndolo de la existencia actual de Intelisis; el Excel
// solo se descarga.
@Component({
  selector: 'app-inventario-inicial',
  imports: [FormsModule, CapturaIntelisis],
  templateUrl: './inventario-inicial.html',
  styleUrl: './inventario-inicial.css',
})
export class InventarioInicial {
  protected readonly inventario = inject(InventarioAlmacen);
  protected readonly rotacion = inject(RotacionAlmacen);
  protected readonly formatoFecha = formatoCaducidad;

  readonly altaAbierta = signal(false);

  readonly filtro = signal('');
  readonly lotesFiltrados = computed(() => {
    const texto = this.normalizar(this.filtro());
    const lotes = this.inventario.lotes();
    if (!texto) return lotes;
    return lotes.filter((lote) =>
      this.normalizar(`${lote.clave} ${lote.descripcion ?? ''} ${lote.lote} ${lote.ubicacion} ${lote.fuente ?? ''} ${lote.proveedor}`).includes(texto),
    );
  });

  // Descarga lo que se ve en la tabla (respeta la búsqueda), con las columnas de la hoja del Drive.
  descargarInventario(): void {
    const filas = this.lotesFiltrados().map((lote) => [
      lote.clave,
      lote.descripcion ?? '',
      lote.lote,
      formatoCaducidad(lote.caducidad),
      lote.cajas,
      this.rotacion.buscar(lote.clave)?.cpm ?? '',
      lote.ubicacion,
      lote.fuente ?? '',
      lote.fechaIngreso ? formatoCaducidad(lote.fechaIngreso) : '',
      lote.proveedor,
    ]);
    const hoja = XLSX.utils.aoa_to_sheet([ENCABEZADOS_EXCEL, ...filas]);
    hoja['!cols'] = [{ wch: 18 }, { wch: 48 }, { wch: 14 }, { wch: 12 }, { wch: 10 }, { wch: 14 }, { wch: 16 }, { wch: 26 }, { wch: 16 }, { wch: 24 }];
    const libro = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(libro, hoja, 'Inventario');
    XLSX.writeFile(libro, `inventario-almacen-${new Date().toLocaleDateString('en-CA')}.xlsx`);
  }

  private normalizar(valor: unknown): string {
    return String(valor ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase();
  }
}
