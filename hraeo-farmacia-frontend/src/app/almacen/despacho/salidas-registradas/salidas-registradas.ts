import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { SolicitudesAlmacen, TIPO_SALIDA_TEXTO, fechaLocal, formatoCaducidad } from '../../solicitudes-almacen';

// Despacho · Salidas registradas (HU14 CA5). Cada despacho deja un movimiento
// con fecha, usuario, lote, cantidad y ubicación de origen. Se consulta por día:
// abre en el día de hoy y se puede elegir cualquier fecha anterior.
@Component({
  selector: 'app-salidas-registradas',
  imports: [FormsModule, DatePipe],
  templateUrl: './salidas-registradas.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../despacho-comun.css', './salidas-registradas.css'],
})
export class SalidasRegistradas {
  protected readonly solicitudes = inject(SolicitudesAlmacen);
  protected readonly formatoCaducidad = formatoCaducidad;
  protected readonly tipoTexto = TIPO_SALIDA_TEXTO;

  readonly hoy = fechaLocal().slice(0, 10);
  readonly dia = signal(this.hoy);
  readonly esHoy = computed(() => this.dia() === this.hoy);
  readonly diaTexto = computed(() => {
    const [anio, mes, dia] = this.dia().split('-').map(Number);
    const texto = new Date(anio, mes - 1, dia).toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  });

  // Día más reciente con movimientos antes del elegido, para saltar a él si el día está vacío.
  readonly diaAnteriorConSalidas = computed(() => this.solicitudes.salidas().map((salida) => salida.fecha.slice(0, 10)).filter((dia) => dia < this.dia()).sort().pop() ?? '');

  readonly filtro = signal('');
  readonly salidas = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    const salidas = this.solicitudes.salidas().filter((salida) => salida.fecha.startsWith(this.dia()));
    if (!texto) return salidas;
    return salidas.filter((salida) => `${salida.folio} ${salida.clave} ${salida.lote} ${salida.ubicacion} ${salida.usuario} ${salida.institucion ?? ''}`.toLowerCase().includes(texto));
  });
  readonly totalCajas = computed(() => this.salidas().reduce((total, salida) => total + salida.cajas, 0));

  cambiarDia(dias: number): void {
    const [anio, mes, dia] = this.dia().split('-').map(Number);
    const fecha = fechaLocal(new Date(anio, mes - 1, dia + dias, 12)).slice(0, 10);
    this.dia.set(fecha > this.hoy ? this.hoy : fecha);
  }

  elegirDia(valor: string): void {
    // Si se borra la fecha del selector se vuelve a hoy.
    this.dia.set(!valor || valor > this.hoy ? this.hoy : valor);
  }
}
