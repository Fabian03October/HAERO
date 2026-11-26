import { Injectable, inject, signal } from '@angular/core';
import { AlmacenApi, Medicamento } from '../almacen/almacen-api';

export interface MarcasSeguridad {
  altoRiesgo: boolean;
  redFria: boolean;
}

const SIN_MARCAS: MarcasSeguridad = { altoRiesgo: false, redFria: false };

// "Envase con 20 tabletas", "con 3 ampolletas": el número de piezas que trae la caja.
const PIEZAS_EN_TEXTO = /con\s+(\d+)\s+(tabletas?|c[aá]psulas?|comprimidos?|grageas?|ampolletas?|frascos?|[aá]mpulas?|sobres?|piezas?|parches?|jeringas?|cartuchos?|bolsas?|viales?|supositorios?|[oó]vulos?|plumas?)/i;

/**
 * Piezas que cuenta un escaneo: la caja completa (piezas por caja del catálogo). En líquidos
 * Intelisis a veces guarda ml en vez de piezas: si la presentación habla de ml y no dice las piezas
 * con ese mismo número, cada escaneo es 1 frasco.
 */
export function piezasDeLaCaja(m: Pick<Medicamento, 'piezasPorCaja' | 'presentacion' | 'nombreGenerico'>): number {
  const porCaja = m.piezasPorCaja ?? 1;
  if (porCaja <= 1) return 1;
  const texto = `${m.presentacion ?? ''} ${m.nombreGenerico ?? ''}`;
  const dice = PIEZAS_EN_TEXTO.exec(texto);
  if (/\bml\b/i.test(texto) && Number(dice?.[1]) !== porCaja) return 1;
  return porCaja;
}

/**
 * Marcas de seguridad del paciente por clave (alto riesgo de la lista ISMP y red fría), para
 * mostrarlas en cualquier pantalla sin pedir el catálogo otra vez. Se carga una sola vez.
 */
@Injectable({ providedIn: 'root' })
export class SeguridadMedicamentos {
  private readonly api = inject(AlmacenApi);
  private readonly porClave = signal<Map<string, MarcasSeguridad>>(new Map());
  private readonly cajaPorClave = signal<Map<string, number>>(new Map());
  private cargado = false;

  cargar(): void {
    if (this.cargado) return;
    this.cargado = true;
    this.api.listarMedicamentos().subscribe({
      next: (lista) => {
        this.porClave.set(new Map(lista.map((m) => [m.clave, { altoRiesgo: m.altoRiesgo, redFria: m.redFria }])));
        this.cajaPorClave.set(new Map(lista.map((m) => [m.clave, piezasDeLaCaja(m)])));
      },
      // Sin catálogo no se muestran etiquetas; se vuelve a intentar en la siguiente pantalla.
      error: () => (this.cargado = false),
    });
  }

  /** Acepta la clave de HAERO o la de Intelisis con su "-1". */
  marcas(clave: string | null | undefined): MarcasSeguridad {
    if (!clave) return SIN_MARCAS;
    return this.porClave().get(clave.replace(/-1$/, '')) ?? SIN_MARCAS;
  }

  /** Piezas que trae una caja de esa clave (1 si no se sabe): un escaneo cuenta hasta una caja completa. */
  piezasPorCaja(clave: string | null | undefined): number {
    if (!clave) return 1;
    return this.cajaPorClave().get(clave.replace(/-1$/, '')) ?? 1;
  }

  /** Después de que Farmacia cambia una marca, para que se vea en todas las pantallas. */
  actualizar(medicamento: Medicamento): void {
    this.porClave.update((actual) => new Map(actual).set(medicamento.clave, { altoRiesgo: medicamento.altoRiesgo, redFria: medicamento.redFria }));
  }
}
