import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MovimientosEspecialesAlmacen } from '../../../almacen/movimientos-especiales-almacen';
import { formatoCaducidad } from '../../../almacen/solicitudes-almacen';
import { descargarExcel, enRango, fechaCorta } from '../reporte';

// Supervisión · Reporte de trazabilidad de canjes (HU18 "la trazabilidad lote
// origen → lote nuevo se consulta en los reportes de trazabilidad"). Solo lectura.
@Component({
  selector: 'app-reporte-canjes',
  imports: [FormsModule],
  templateUrl: './reporte-canjes.html',
  styleUrls: ['../../../almacen/recepcion-almacen/recepcion-comun.css', '../../../almacen/despacho/despacho-comun.css', '../reportes.css'],
})
export class ReporteCanjes {
  protected readonly movimientos = inject(MovimientosEspecialesAlmacen);
  protected readonly formatoCaducidad = formatoCaducidad;
  protected readonly fechaCorta = fechaCorta;

  readonly buscar = signal('');
  readonly desde = signal('');
  readonly hasta = signal('');

  readonly filas = computed(() => {
    const texto = this.buscar().trim().toLowerCase();
    return this.movimientos
      .canjes()
      .filter((canje) => enRango(canje.fecha, this.desde(), this.hasta()))
      .filter(
        (canje) =>
          !texto || `${canje.clave} ${canje.nombreGenerico} ${canje.loteOrigen} ${canje.loteNuevo} ${canje.proveedor}`.toLowerCase().includes(texto),
      );
  });
  readonly cajasCanjeadas = computed(() => this.filas().reduce((total, canje) => total + canje.cajasOrigen, 0));
  readonly cajasRecibidas = computed(() => this.filas().reduce((total, canje) => total + canje.cajasNuevas, 0));

  limpiar(): void {
    this.buscar.set('');
    this.desde.set('');
    this.hasta.set('');
  }

  descargar(): void {
    descargarExcel(
      'reporte-trazabilidad-canjes',
      'Canjes',
      ['FECHA', 'CLAVE', 'MEDICAMENTO', 'PROVEEDOR', 'LOTE ORIGEN', 'CADUCIDAD ORIGEN', 'CAJAS ORIGEN', 'LOTE NUEVO', 'CADUCIDAD NUEVA', 'CAJAS NUEVAS', 'UBICACIÓN', 'REGISTRÓ'],
      this.filas().map((canje) => [
        fechaCorta(canje.fecha),
        canje.clave,
        canje.nombreGenerico,
        canje.proveedor,
        canje.loteOrigen,
        fechaCorta(canje.caducidadOrigen),
        canje.cajasOrigen,
        canje.loteNuevo,
        fechaCorta(canje.caducidadNueva),
        canje.cajasNuevas,
        canje.ubicacion,
        canje.usuario ?? '',
      ]),
    );
  }
}
