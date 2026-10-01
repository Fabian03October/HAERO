import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RotacionAlmacen, nombreMes } from '../../rotacion-almacen';

// Rotación · CPM por clave (HU16). Consulta individual con el consumo de cada mes
// y listado general de todas las claves (CA3).
@Component({
  selector: 'app-cpm-clave',
  imports: [FormsModule],
  templateUrl: './cpm-clave.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../../despacho/despacho-comun.css', './cpm-clave.css'],
})
export class CpmClave {
  protected readonly rotacion = inject(RotacionAlmacen);
  protected readonly nombreMes = nombreMes;

  readonly claveSeleccionada = signal(this.rotacion.rotacion().find((item) => item.cpm)?.clave ?? '');
  readonly detalle = computed(() => this.rotacion.buscar(this.claveSeleccionada()));
  readonly mesMayor = computed(() => Math.max(1, ...(this.detalle()?.meses.map((mes) => mes.cajas) ?? [])));

  readonly filtro = signal('');
  readonly listado = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    const filas = this.rotacion.rotacion();
    if (!texto) return filas;
    return filas.filter((fila) => `${fila.clave} ${fila.medicamento}`.toLowerCase().includes(texto));
  });

  readonly periodo = computed(() => {
    const ventana = this.rotacion.ventana();
    return `${nombreMes(ventana[0])} – ${nombreMes(ventana[ventana.length - 1])}`;
  });

  altura(cajas: number): number {
    return Math.round((cajas / this.mesMayor()) * 100);
  }
}
