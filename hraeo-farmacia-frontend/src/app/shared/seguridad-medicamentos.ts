import { Injectable, inject, signal } from '@angular/core';
import { AlmacenApi, Medicamento } from '../almacen/almacen-api';

export interface MarcasSeguridad {
  altoRiesgo: boolean;
  redFria: boolean;
}

const SIN_MARCAS: MarcasSeguridad = { altoRiesgo: false, redFria: false };

/**
 * Marcas de seguridad del paciente por clave (alto riesgo de la lista ISMP y red fría), para
 * mostrarlas en cualquier pantalla sin pedir el catálogo otra vez. Se carga una sola vez.
 */
@Injectable({ providedIn: 'root' })
export class SeguridadMedicamentos {
  private readonly api = inject(AlmacenApi);
  private readonly porClave = signal<Map<string, MarcasSeguridad>>(new Map());
  private cargado = false;

  cargar(): void {
    if (this.cargado) return;
    this.cargado = true;
    this.api.listarMedicamentos().subscribe({
      next: (lista) => this.porClave.set(new Map(lista.map((m) => [m.clave, { altoRiesgo: m.altoRiesgo, redFria: m.redFria }]))),
      // Sin catálogo no se muestran etiquetas; se vuelve a intentar en la siguiente pantalla.
      error: () => (this.cargado = false),
    });
  }

  /** Acepta la clave de HAERO o la de Intelisis con su "-1". */
  marcas(clave: string | null | undefined): MarcasSeguridad {
    if (!clave) return SIN_MARCAS;
    return this.porClave().get(clave.replace(/-1$/, '')) ?? SIN_MARCAS;
  }

  /** Después de que Farmacia cambia una marca, para que se vea en todas las pantallas. */
  actualizar(medicamento: Medicamento): void {
    this.porClave.update((actual) => new Map(actual).set(medicamento.clave, { altoRiesgo: medicamento.altoRiesgo, redFria: medicamento.redFria }));
  }
}
