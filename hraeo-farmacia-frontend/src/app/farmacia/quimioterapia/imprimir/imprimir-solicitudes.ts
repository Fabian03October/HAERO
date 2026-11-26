import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { mensajeDeError } from '../../../almacen/almacen-api';
import { FarmaciaApi, SolicitudQuimioDetalle } from '../../farmacia-api';

// Solicitud de Oncología Médica para surtir, con el acomodo de la hoja de indicaciones. No lleva lista de
// medicamentos: van en las notas del médico. Una hoja por solicitud; ?folios=1,2 (se abre en otra pestaña).
@Component({
  selector: 'app-imprimir-solicitudes',
  imports: [DatePipe],
  templateUrl: './imprimir-solicitudes.html',
  styleUrl: '../../../hospitalizacion/imprimir/imprimir.css',
})
export class ImprimirSolicitudes {
  private readonly api = inject(FarmaciaApi);
  readonly hojas = signal<SolicitudQuimioDetalle[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly impreso = new Date();

  constructor() {
    const folios = (inject(ActivatedRoute).snapshot.queryParamMap.get('folios') ?? '').split(',').filter(Boolean);
    if (!folios.length) {
      this.cargando.set(false);
      return;
    }
    forkJoin(folios.map((f) => this.api.solicitudQuimio(f))).subscribe({
      next: (hojas) => {
        this.hojas.set(hojas);
        this.cargando.set(false);
        setTimeout(() => window.print(), 300);
      },
      error: (error) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudieron preparar las solicitudes.'));
      },
    });
  }

  imprimir(): void {
    window.print();
  }
}
