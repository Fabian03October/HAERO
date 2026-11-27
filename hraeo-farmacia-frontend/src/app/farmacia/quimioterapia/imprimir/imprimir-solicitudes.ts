import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { mensajeDeError } from '../../../almacen/almacen-api';
import { FarmaciaApi, SolicitudQuimioDetalle } from '../../farmacia-api';

// Solicitud de Oncología Médica con el formato de Intelisis "Solicitud de procedimiento". No lleva lista de
// medicamentos: van en las notas del médico. Una hoja por solicitud; ?folios=1,2 (se abre en otra pestaña).
// Edad, sexo, CURP, sesiones, prioridad y cédula se leen de Intelisis al imprimir; si no responde, quedan en blanco.
@Component({
  selector: 'app-imprimir-solicitudes',
  imports: [DatePipe],
  templateUrl: './imprimir-solicitudes.html',
  styleUrl: '../../../hospitalizacion/imprimir/imprimir.css',
  styles: [`
    .sp-encabezado { display: grid; grid-template-columns: 90px 1fr 90px; align-items: center; gap: 8px; border-bottom: 2px solid #611232; padding-bottom: 6px; margin-bottom: 8px; }
    .sp-salud { font-weight: 800; font-size: 20px; color: #611232; line-height: 1; }
    .sp-salud small { display: block; font-size: 8px; font-weight: 600; color: #6f7271; }
    .sp-titulo { display: grid; text-align: center; gap: 2px; font-size: 11px; }
    .sp-logo { width: 90px; justify-self: end; }
    .sp-tabla { width: 100%; border-collapse: collapse; margin-bottom: 8px; font-size: 11px; }
    .sp-tabla td { border: 1px solid #c9c2b8; padding: 4px 6px; vertical-align: top; }
    .sp-caja { display: inline-block; width: 12px; height: 12px; border: 1px solid #333; text-align: center; line-height: 11px; font-size: 10px; margin: 0 2px; }
    .firmas-medicos small { display: block; }
  `],
})
export class ImprimirSolicitudes {
  private readonly api = inject(FarmaciaApi);
  readonly hojas = signal<{ detalle: SolicitudQuimioDetalle; formato: Record<string, string> }[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly impreso = new Date();

  constructor() {
    const folios = (inject(ActivatedRoute).snapshot.queryParamMap.get('folios') ?? '').split(',').filter(Boolean);
    if (!folios.length) {
      this.cargando.set(false);
      return;
    }
    forkJoin(folios.map((f) => forkJoin({
      detalle: this.api.solicitudQuimio(f),
      formato: this.api.formatoQuimio(f).pipe(catchError(() => of({} as Record<string, string>))),
    }))).subscribe({
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

  /** Edad en años cumplidos a partir de la fecha de nacimiento (aaaa-mm-dd). */
  edad(nacimiento?: string): string {
    if (!nacimiento) return '';
    const n = new Date(nacimiento.slice(0, 10) + 'T00:00:00');
    if (isNaN(n.getTime())) return '';
    const hoy = new Date();
    let anios = hoy.getFullYear() - n.getFullYear();
    if (hoy.getMonth() < n.getMonth() || (hoy.getMonth() === n.getMonth() && hoy.getDate() < n.getDate())) anios--;
    return `${anios} años`;
  }

  esSi(valor?: string): boolean {
    return !!valor && /^(1|true|s[ií]|x)$/i.test(valor.trim());
  }

  urgente(valor?: string): boolean {
    return !!valor && /urgente|^(1|true|s[ií])$/i.test(valor.trim());
  }

  imprimir(): void {
    window.print();
  }
}
