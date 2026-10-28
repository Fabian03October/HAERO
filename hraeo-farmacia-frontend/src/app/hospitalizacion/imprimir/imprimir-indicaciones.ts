import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { mensajeDeError } from '../../almacen/almacen-api';
import { HojaIndicaciones, HospitalizacionApi } from '../hospitalizacion-api';

// "Indicaciones médicas activas", con el mismo acomodo que la que imprime Intelisis (sin logos por
// ahora). Una hoja por paciente; se abre en otra pestaña y manda a imprimir sola.
// ?central=CE-HCLIN&ingresos=1,2 (sin ingresos: todos los del área).
@Component({
  selector: 'app-imprimir-indicaciones',
  templateUrl: './imprimir-indicaciones.html',
  styleUrl: './imprimir.css',
})
export class ImprimirIndicaciones {
  private readonly api = inject(HospitalizacionApi);
  readonly hojas = signal<HojaIndicaciones[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');

  constructor() {
    const parametros = inject(ActivatedRoute).snapshot.queryParamMap;
    const ingresos = (parametros.get('ingresos') ?? '').split(',').filter(Boolean).map(Number);
    this.api.indicaciones(parametros.get('central') ?? '', ingresos).subscribe({
      next: (hojas) => {
        this.hojas.set(hojas);
        this.cargando.set(false);
        if (hojas.length) setTimeout(() => window.print(), 300);
      },
      error: (error) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudieron preparar las indicaciones.'));
      },
    });
  }

  imprimir(): void {
    window.print();
  }

  // "miércoles, 29 julio 2026 06:30"
  fechaLarga(valor: string): string {
    const fecha = new Date(valor);
    const dia = fecha.toLocaleDateString('es-MX', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' }).replace(/ de /g, ' ');
    return `${dia} ${fecha.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', hour12: false })}`;
  }

  fechaCorta(valor: string | null): string {
    if (!valor) return '';
    const [anio, mes, dia] = valor.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio.slice(2)}`;
  }
}
