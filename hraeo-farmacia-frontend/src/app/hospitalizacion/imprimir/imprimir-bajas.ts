import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { mensajeDeError } from '../../almacen/almacen-api';
import { HojaBaja, HospitalizacionApi } from '../hospitalizacion-api';

// Hoja de baja propia de HAERO con el acomodo del "Consumo Farmacia" de Intelisis: una por paciente,
// con folio de HAERO. Se abre en otra pestaña y manda a imprimir sola.
// ?central=CE-HCLIN&dia=2026-07-29&ingreso=123 (ingreso y día son opcionales).
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
    const ingreso = parametros.get('ingreso');
    this.api.hojasBaja(parametros.get('central') ?? '', parametros.get('dia') ?? undefined, ingreso ? Number(ingreso) : undefined).subscribe({
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
