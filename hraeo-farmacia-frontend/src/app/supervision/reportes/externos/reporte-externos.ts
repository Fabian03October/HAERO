import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MovimientosEspecialesAlmacen, SentidoExterno, TipoExterno } from '../../../almacen/movimientos-especiales-almacen';
import { descargarExcel, enRango, fechaCorta } from '../reporte';

// Supervisión · Reporte de movimientos con otras instituciones (HU20 "alimenta los
// reportes de movimientos con otras instituciones"; CA3: se distinguen de los
// traspasos a Farmacia). Préstamos y transferencias, con un resumen por institución.
@Component({
  selector: 'app-reporte-externos',
  imports: [FormsModule],
  templateUrl: './reporte-externos.html',
  styleUrls: ['../../../almacen/recepcion-almacen/recepcion-comun.css', '../../../almacen/despacho/despacho-comun.css', '../reportes.css'],
})
export class ReporteExternos {
  protected readonly movimientos = inject(MovimientosEspecialesAlmacen);
  protected readonly fechaCorta = fechaCorta;

  readonly buscar = signal('');
  readonly tipo = signal<'' | TipoExterno>('');
  readonly sentido = signal<'' | SentidoExterno>('');
  readonly desde = signal('');
  readonly hasta = signal('');

  readonly filas = computed(() => {
    const texto = this.buscar().trim().toLowerCase();
    return this.movimientos
      .externos()
      .filter((item) => !this.tipo() || item.tipo === this.tipo())
      .filter((item) => !this.sentido() || item.sentido === this.sentido())
      .filter((item) => enRango(item.fecha, this.desde(), this.hasta()))
      .filter((item) => !texto || `${item.institucion} ${item.clave} ${item.lote}`.toLowerCase().includes(texto));
  });

  readonly salieron = computed(() => this.filas().filter((item) => item.sentido === 'SALIDA').reduce((total, item) => total + item.cajas, 0));
  readonly entraron = computed(() => this.filas().filter((item) => item.sentido === 'ENTRADA').reduce((total, item) => total + item.cajas, 0));

  // Saldo con cada institución: lo que se le dio y lo que se recibió de ella.
  readonly porInstitucion = computed(() => {
    const resumen = new Map<string, { institucion: string; salieron: number; entraron: number; movimientos: number }>();
    for (const item of this.filas()) {
      const fila = resumen.get(item.institucion) ?? { institucion: item.institucion, salieron: 0, entraron: 0, movimientos: 0 };
      if (item.sentido === 'SALIDA') fila.salieron += item.cajas;
      else fila.entraron += item.cajas;
      fila.movimientos++;
      resumen.set(item.institucion, fila);
    }
    return [...resumen.values()].sort((a, b) => b.salieron + b.entraron - (a.salieron + a.entraron));
  });

  tipoTexto(tipo: TipoExterno): string {
    return tipo === 'PRESTAMO' ? 'Préstamo' : 'Transferencia';
  }

  limpiar(): void {
    this.buscar.set('');
    this.tipo.set('');
    this.sentido.set('');
    this.desde.set('');
    this.hasta.set('');
  }

  descargar(): void {
    descargarExcel(
      'reporte-movimientos-otras-instituciones',
      'Movimientos',
      ['FECHA', 'TIPO', 'SENTIDO', 'INSTITUCIÓN', 'CLAVE', 'LOTE', 'CADUCIDAD', 'UBICACIÓN', 'CAJAS', 'REGISTRÓ'],
      this.filas().map((item) => [
        fechaCorta(item.fecha),
        this.tipoTexto(item.tipo),
        item.sentido === 'SALIDA' ? 'Sale' : 'Entra',
        item.institucion,
        item.clave,
        item.lote,
        fechaCorta(item.caducidad),
        item.ubicacion,
        item.cajas,
        item.usuario,
      ]),
    );
  }
}
