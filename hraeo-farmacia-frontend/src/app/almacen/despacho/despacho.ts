import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { ALMACEN_MENU } from '../almacen-nav';
import { recargarAlmacen } from '../recargar-almacen';

// Contenedor de Despacho: barra superior + menú lateral. Cada sección
// (Existencias, Solicitudes, Bandeja, Salidas) es una página hija, igual que Recepción.
@Component({
  selector: 'app-despacho',
  imports: [RouterOutlet, Topbar, Sidebar],
  templateUrl: './despacho.html',
  styleUrl: '../recepcion-almacen/recepcion-almacen.css',
})
export class Despacho {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = ALMACEN_MENU;

  constructor() {
    recargarAlmacen();
  }
}
