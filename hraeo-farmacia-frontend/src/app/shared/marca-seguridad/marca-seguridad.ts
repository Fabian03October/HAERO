import { Component, computed, inject, input } from '@angular/core';
import { SeguridadMedicamentos } from '../seguridad-medicamentos';

/**
 * Etiquetas de seguridad del paciente junto a una clave: ALTO RIESGO en rojo (lista ISMP) y
 * RED FRÍA en azul. No ocupa espacio si la clave no tiene ninguna marca.
 */
@Component({
  selector: 'app-marca-seguridad',
  template: `
    @if (marcas().altoRiesgo) {
      <span class="marca riesgo" title="Medicamento de alto riesgo (lista ISMP): verificar dos veces antes de entregar">⚠ Alto riesgo</span>
    }
    @if (marcas().redFria) {
      <span class="marca fria" title="Red fría: conservar en refrigeración; no se deja en cajas ni carritos">❄ Red fría</span>
    }
  `,
  styles: `
    :host { display: inline-flex; flex-wrap: wrap; gap: 4px; vertical-align: middle; }
    .marca { display: inline-block; padding: 1px 7px; border-radius: 999px; font-size: 10px; font-weight: 800; letter-spacing: 0.02em; white-space: nowrap; }
    .riesgo { background: #fde2e1; color: #b3261e; border: 1px solid #f3b4b0; }
    .fria { background: #e3f0fb; color: #1b5e9a; border: 1px solid #b6d4ef; }
  `,
})
export class MarcaSeguridad {
  readonly clave = input<string | null | undefined>();
  private readonly seguridad = inject(SeguridadMedicamentos);
  readonly marcas = computed(() => this.seguridad.marcas(this.clave()));

  constructor() {
    this.seguridad.cargar();
  }
}
