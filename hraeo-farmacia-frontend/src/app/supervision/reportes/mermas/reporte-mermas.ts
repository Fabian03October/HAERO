import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MovimientosEspecialesAlmacen } from '../../../almacen/movimientos-especiales-almacen';
import { formatoCaducidad } from '../../../almacen/solicitudes-almacen';
import { descargarExcel, enRango, fechaCorta } from '../reporte';

// Supervisión · Reporte de mermas por caducidad (HU19 "alimenta el reporte de
// mermas"). Lotes apartados en caducados, con cuándo y quién los apartó. Solo lectura.
@Component({
  selector: 'app-reporte-mermas',
  imports: [FormsModule],
  templateUrl: './reporte-mermas.html',
  styleUrls: ['../../../almacen/recepcion-almacen/recepcion-comun.css', '../../../almacen/despacho/despacho-comun.css', '../reportes.css'],
})
export class ReporteMermas {
  protected readonly movimientos = inject(MovimientosEspecialesAlmacen);
  protected readonly formatoCaducidad = formatoCaducidad;
  protected readonly fechaCorta = fechaCorta;

  readonly buscar = signal('');
  readonly desde = signal('');
  readonly hasta = signal('');

  readonly filas = computed(() => {
    const texto = this.buscar().trim().toLowerCase();
    return this.movimientos
      .caducados()
      .filter((lote) => enRango(lote.fechaApartado, this.desde(), this.hasta()))
      .filter((lote) => !texto || `${lote.clave} ${lote.nombreGenerico} ${lote.numeroLote} ${lote.proveedor}`.toLowerCase().includes(texto));
  });
  readonly totalCajas = computed(() => this.filas().reduce((total, lote) => total + lote.cajas, 0));
  readonly totalClaves = computed(() => new Set(this.filas().map((lote) => lote.clave)).size);
  // Vencidos que siguen en el inventario: todavía no son merma registrada.
  readonly porApartar = computed(() => this.movimientos.vencidosPorApartar());

  ubicaciones(lista: { ubicacion: string; cajas: number }[]): string {
    return lista.map((item) => `${item.ubicacion} (${item.cajas})`).join(', ');
  }

  limpiar(): void {
    this.buscar.set('');
    this.desde.set('');
    this.hasta.set('');
  }

  descargar(): void {
    descargarExcel(
      'reporte-mermas-caducidad',
      'Mermas',
      ['CLAVE', 'MEDICAMENTO', 'LOTE', 'CADUCIDAD', 'PROVEEDOR', 'CAJAS', 'UBICACIONES', 'APARTADO EL', 'REGISTRÓ'],
      this.filas().map((lote) => [
        lote.clave,
        lote.nombreGenerico,
        lote.numeroLote,
        fechaCorta(lote.caducidad),
        lote.proveedor,
        lote.cajas,
        this.ubicaciones(lote.ubicaciones),
        fechaCorta(lote.fechaApartado),
        lote.usuario ?? '',
      ]),
    );
  }
}
