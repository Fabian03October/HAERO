import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificacionesPopup } from './shared/notificaciones/notificaciones-popup';
import { VigilanteSesion } from './shared/vigilante-sesion/vigilante-sesion';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NotificacionesPopup, VigilanteSesion],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
