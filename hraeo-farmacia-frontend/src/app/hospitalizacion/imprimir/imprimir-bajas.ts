import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { mensajeDeError } from '../../almacen/almacen-api';
import { HojaBaja, HospitalizacionApi } from '../hospitalizacion-api';

// Hoja de baja propia de HAERO con el acomodo del "Consumo Farmacia" de Intelisis. Se abre en otra
// pestaña y manda a imprimir sola.
// ?central=CE-HCLIN&dia=2026-07-29&ingreso=123: hojas por paciente (detalle en HAERO).
// ?central=CE-HCLIN&volumen=1&folio=4: baja por volumen del área, la que se captura en Intelisis.
// Día, ingreso y folio son opcionales.
@Component({
  selector: 'app-imprimir-bajas',
  templateUrl: './imprimir-bajas.html',
  styleUrl: './imprimir.css',
})
export class ImprimirBajas {
  private readonly api = inject(HospitalizacionApi);
  readonly hojas = signal<HojaBaja[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');

  constructor() {
    const parametros = inject(ActivatedRoute).snapshot.queryParamMap;
    const central = parametros.get('central') ?? '';
    const dia = parametros.get('dia') ?? undefined;
    const ingreso = parametros.get('ingreso');
    const folio = parametros.get('folio');
    const hojas = parametros.get('volumen')
      ? this.api.bajasVolumen(central, dia, folio ? Number(folio) : undefined)
      : this.api.hojasBaja(central, dia, ingreso ? Number(ingreso) : undefined);
    hojas.subscribe({
      next: (hojas) => {
        this.hojas.set(hojas);
        this.cargando.set(false);
        if (hojas.length) setTimeout(() => window.print(), 300);
      },
      error: (error) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudieron preparar las hojas de baja.'));
      },
    });
  }

  imprimir(): void {
    window.print();
  }

  // "29 Julio 2026"
  fechaTitulo(valor: string): string {
    const [anio, mes, dia] = valor.slice(0, 10).split('-').map(Number);
    const nombre = new Date(anio, mes - 1, dia).toLocaleDateString('es-MX', { month: 'long' });
    return `${dia} ${nombre.charAt(0).toUpperCase()}${nombre.slice(1)} ${anio}`;
  }

  fecha(valor: string): string {
    const [anio, mes, dia] = valor.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
  }

  dinero(valor: number | null): string {
    return valor === null ? '' : `$${valor.toFixed(2)}`;
  }
}
