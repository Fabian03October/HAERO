import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificacionesPopup } from './shared/notificaciones/notificaciones-popup';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NotificacionesPopup],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
