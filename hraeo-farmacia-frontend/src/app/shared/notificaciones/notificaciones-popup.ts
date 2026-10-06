import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { Notificaciones } from './notificaciones';

@Component({
  selector: 'app-notificaciones',
  templateUrl: './notificaciones-popup.html',
  styleUrl: './notificaciones-popup.css',
  host: { '(document:keydown.escape)': 'escape()' },
})
export class NotificacionesPopup {
  protected readonly notificaciones = inject(Notificaciones);

  private readonly botonAviso = viewChild<ElementRef<HTMLButtonElement>>('botonAviso');
  private readonly botonAceptar = viewChild<ElementRef<HTMLButtonElement>>('botonAceptar');

  constructor() {
    // El foco va al botón principal para poder responder con Enter.
    effect(() => this.botonAceptar()?.nativeElement.focus());
    effect(() => this.botonAviso()?.nativeElement.focus());
  }

  protected escape(): void {
    if (this.notificaciones.confirmacion()) this.notificaciones.responder(false);
    else this.notificaciones.cerrarAviso();
  }
}
