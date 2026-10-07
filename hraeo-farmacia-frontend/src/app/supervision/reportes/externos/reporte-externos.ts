import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MovimientoExterno, MovimientosEspecialesAlmacen, SentidoExterno, TipoExterno } from '../../../almacen/movimientos-especiales-almacen';
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
  // Tipo: préstamo, transferencia o solo las devoluciones de préstamos.
  readonly tipo = signal<'' | TipoExterno | 'DEVOLUCION'>('');
  readonly sentido = signal<'' | SentidoExterno>('');
  readonly desde = signal('');
  readonly hasta = signal('');

  readonly filas = computed(() => {
    const texto = this.buscar().trim().toLowerCase();
    return this.movimientos
      .externos()
      .filter((item) => !this.tipo() || (this.tipo() === 'DEVOLUCION' ? item.devolucion : item.tipo === this.tipo() && !item.devolucion))
      .filter((item) => !this.sentido() || item.sentido === this.sentido())
      .filter((item) => enRango(item.fecha, this.desde(), this.hasta()))
      .filter((item) => !texto || `${item.institucion} ${item.clave} ${item.lote}`.toLowerCase().includes(texto));
  });

  // Las devoluciones se cuentan aparte: regresar un préstamo no es un préstamo nuevo.
  readonly salieron = computed(() => this.sumar((item) => item.sentido === 'SALIDA' && !item.devolucion));
  readonly entraron = computed(() => this.sumar((item) => item.sentido === 'ENTRADA' && !item.devolucion));
  readonly devueltas = computed(() => this.sumar((item) => item.devolucion));

  // Saldo con cada institución: lo que se le prestó o transfirió, lo que se recibió
  // de ella y las devoluciones en cada sentido.
  readonly porInstitucion = computed(() => {
    const resumen = new Map<string, { institucion: string; salieron: number; entraron: number; nosDevolvieron: number; devolvimos: number; movimientos: number }>();
    for (const item of this.filas()) {
      const fila = resumen.get(item.institucion) ?? { institucion: item.institucion, salieron: 0, entraron: 0, nosDevolvieron: 0, devolvimos: 0, movimientos: 0 };
      if (item.devolucion && item.sentido === 'ENTRADA') fila.nosDevolvieron += item.cajas;
      else if (item.devolucion) fila.devolvimos += item.cajas;
      else if (item.sentido === 'SALIDA') fila.salieron += item.cajas;
      else fila.entraron += item.cajas;
      fila.movimientos++;
      resumen.set(item.institucion, fila);
    }
    return [...resumen.values()].sort((a, b) => b.salieron + b.entraron - (a.salieron + a.entraron));
  });

  private sumar(cumple: (item: MovimientoExterno) => boolean): number {
    return this.filas().filter(cumple).reduce((total, item) => total + item.cajas, 0);
  }

  tipoTexto(item: MovimientoExterno): string {
    if (item.devolucion) return 'Devolución de préstamo';
    return item.tipo === 'PRESTAMO' ? 'Préstamo' : 'Transferencia';
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
        this.tipoTexto(item),
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
