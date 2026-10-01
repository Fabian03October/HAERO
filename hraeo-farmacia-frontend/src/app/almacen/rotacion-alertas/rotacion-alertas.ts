import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { ALMACEN_MENU } from '../almacen-nav';
import { recargarAlmacen } from '../recargar-almacen';

// Contenedor de Rotación y alertas: barra superior + menú lateral. CPM (HU16) y
// Alertas de stock (HU17) son páginas hijas, igual que Recepción y Despacho.
@Component({
  selector: 'app-rotacion-alertas',
  imports: [RouterOutlet, Topbar, Sidebar],
  templateUrl: './rotacion-alertas.html',
  styleUrl: '../recepcion-almacen/recepcion-almacen.css',
})
export class RotacionAlertas {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = ALMACEN_MENU;

  constructor() {
    recargarAlmacen();
  }
}
