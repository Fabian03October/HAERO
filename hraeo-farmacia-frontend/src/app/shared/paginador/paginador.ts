import { Component, computed, input, signal } from '@angular/core';

// Tamaños de página que puede elegir el usuario.
export const TAMANOS_PAGINA = [25, 50, 100];

/**
 * Paginación de una lista que ya está en memoria (filtrada en la pantalla). Se crea con la lista
 * de la que sale (`new Paginacion(() => this.filtrados())`) y la tabla pinta `visibles()`.
 * Pintar solo una página evita que las tablas largas se traben.
 */
export class Paginacion<T> {
  readonly pagina = signal(1);
  readonly tamano = signal(TAMANOS_PAGINA[0]);

  constructor(private readonly fuente: () => T[]) {}

  readonly total = computed(() => this.fuente().length);
  readonly paginas = computed(() => Math.max(1, Math.ceil(this.total() / this.tamano())));
  // Si un filtro deja menos páginas, se muestra la última que existe.
  readonly paginaActual = computed(() => Math.min(this.pagina(), this.paginas()));
  readonly desde = computed(() => (this.total() ? (this.paginaActual() - 1) * this.tamano() + 1 : 0));
  readonly hasta = computed(() => Math.min(this.paginaActual() * this.tamano(), this.total()));
  readonly visibles = computed(() => this.fuente().slice(this.desde() - 1 < 0 ? 0 : this.desde() - 1, this.hasta()));

  /** Vuelve a la primera página (al cambiar un filtro o una búsqueda). */
  reiniciar(): void {
    this.pagina.set(1);
  }

  ir(pagina: number): void {
    this.pagina.set(Math.min(Math.max(1, pagina), this.paginas()));
  }

  cambiarTamano(tamano: number): void {
    this.tamano.set(tamano);
    this.pagina.set(1);
  }
}

/**
 * Controles de la paginación: "Mostrando 26–50 de 240", ‹ 1 … 4 5 6 … 10 › y cuántos por página.
 * Solo aparece cuando hay más de una página (o se eligió un tamaño mayor al mínimo).
 */
@Component({
  selector: 'app-paginador',
  template: `
    @let p = control();
    @if (p.total() > tamanos[0] || p.tamano() !== tamanos[0]) {
      <nav class="paginador" aria-label="Páginas">
        <span class="paginador-info">Mostrando {{ p.desde() }}–{{ p.hasta() }} de {{ p.total() }}</span>
        <div class="paginador-botones">
          <button type="button" aria-label="Página anterior" [disabled]="p.paginaActual() === 1" (click)="p.ir(p.paginaActual() - 1)">‹</button>
          @for (n of numeros(); track $index) {
            @if (n === 0) {
              <span class="paginador-puntos" aria-hidden="true">…</span>
            } @else {
              <button type="button" [class.actual]="n === p.paginaActual()" [attr.aria-current]="n === p.paginaActual() ? 'page' : null"
                      [attr.aria-label]="'Página ' + n" (click)="p.ir(n)">{{ n }}</button>
            }
          }
          <button type="button" aria-label="Página siguiente" [disabled]="p.paginaActual() === p.paginas()" (click)="p.ir(p.paginaActual() + 1)">›</button>
        </div>
        <label class="paginador-tamano">
          <span>Por página</span>
          <select [value]="p.tamano()" (change)="p.cambiarTamano(+$any($event.target).value)">
            @for (t of tamanos; track t) { <option [value]="t" [selected]="t === p.tamano()">{{ t }}</option> }
          </select>
        </label>
      </nav>
    }
  `,
  styles: `
    .paginador { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px 16px; margin: 12px 0 4px; font-size: 13px; color: #5a5a5a; }
    .paginador-botones { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
    .paginador-botones button { min-width: 36px; height: 36px; padding: 0 8px; border: 1px solid #ddd4c8; border-radius: 8px; background: #fff; color: #4a4a4a; font: inherit; font-weight: 600; cursor: pointer; }
    .paginador-botones button:hover:not(:disabled) { border-color: #611232; color: #611232; }
    .paginador-botones button.actual { border-color: #611232; background: #611232; color: #fff; }
    .paginador-botones button:disabled { opacity: .4; cursor: default; }
    .paginador-puntos { padding: 0 4px; color: #8c8a86; }
    .paginador-tamano { display: inline-flex; align-items: center; gap: 6px; }
    .paginador-tamano select { height: 36px; padding: 0 8px; border: 1px solid #ddd4c8; border-radius: 8px; background: #fff; font: inherit; }
  `,
})
export class Paginador {
  readonly control = input.required<Paginacion<unknown>>();
  readonly tamanos = TAMANOS_PAGINA;

  // Números de página a mostrar: primera, última y dos alrededor de la actual; 0 = "…".
  readonly numeros = computed(() => {
    const total = this.control().paginas();
    const actual = this.control().paginaActual();
    const lista: number[] = [];
    for (let n = 1; n <= total; n++) {
      if (n === 1 || n === total || Math.abs(n - actual) <= 2) lista.push(n);
      else if (lista[lista.length - 1] !== 0) lista.push(0);
    }
    return lista;
  });
}
