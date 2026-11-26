import { Component, computed, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SeguridadMedicamentos } from '../seguridad-medicamentos';

/**
 * Cantidad entregada, en piezas o en cajas (desplegable). Por dentro siempre son piezas: 2 cajas de 20 = 40.
 * El desplegable solo sale si la caja de esa clave trae más de una pieza. Igual en Ventanilla, Quimio y
 * Hospitalización.
 */
@Component({
  selector: 'app-cantidad-piezas',
  imports: [FormsModule],
  template: `
    <span class="cantidad">
      <input type="number" min="1" [id]="idCampo()" [attr.aria-label]="etiqueta()" [placeholder]="placeholder()"
             [ngModel]="mostrado()" (ngModelChange)="cambiar($event)" (keyup.enter)="enter.emit()" />
      @if (porCaja() > 1) {
        <select [ngModel]="unidad()" (ngModelChange)="cambiarUnidad($event)" aria-label="Piezas o cajas">
          <option value="PIEZA">pieza(s)</option>
          <option value="CAJA">caja(s)</option>
        </select>
        @if (unidad() === 'CAJA' && piezas()) {
          <small>= {{ piezas() }} piezas</small>
        }
      }
    </span>
  `,
  styles: `
    .cantidad { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    input { width: 80px; padding: 8px 10px; border: 1px solid var(--color-border-campo); border-radius: 8px; font: inherit; }
    select { padding: 8px 6px; border: 1px solid var(--color-border-campo); border-radius: 8px; background: var(--color-card); font: inherit; }
    small { color: var(--color-text-muted); font-size: 12px; white-space: nowrap; }
  `,
})
export class CantidadPiezas {
  readonly clave = input<string | null | undefined>(null);
  /** Siempre en piezas. */
  readonly piezas = model<number | null>(null);
  readonly idCampo = input<string | null>(null);
  readonly etiqueta = input('Cantidad entregada');
  readonly placeholder = input('');
  readonly enter = output<void>();

  private readonly catalogo = inject(SeguridadMedicamentos);
  readonly porCaja = computed(() => this.catalogo.piezasPorCaja(this.clave()));
  readonly unidad = signal<'PIEZA' | 'CAJA'>('PIEZA');
  /** Lo que se ve en el campo: piezas, o cajas si se eligió "caja(s)". */
  readonly mostrado = computed(() => {
    const p = this.piezas();
    if (p === null) return null;
    return this.unidad() === 'CAJA' ? +(p / this.porCaja()).toFixed(2) : p;
  });

  constructor() {
    this.catalogo.cargar();
  }

  cambiar(valor: number | string | null): void {
    const n = valor === null || valor === '' ? null : Number(valor);
    this.piezas.set(n === null || !Number.isFinite(n) ? null : this.unidad() === 'CAJA' ? Math.round(n * this.porCaja()) : n);
  }

  /** Cambiar la unidad respeta el número escrito: "2" en piezas pasa a "2 cajas" (= 2 × piezas por caja). */
  cambiarUnidad(unidad: 'PIEZA' | 'CAJA'): void {
    const escrito = this.mostrado();
    this.unidad.set(unidad);
    if (escrito !== null) this.cambiar(escrito);
  }
}
