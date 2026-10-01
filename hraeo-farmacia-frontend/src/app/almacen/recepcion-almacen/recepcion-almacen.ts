import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { ALMACEN_MENU } from '../almacen-nav';
import { recargarAlmacen } from '../recargar-almacen';

// Contenedor de Recepción: barra superior + menú lateral. Cada sección
// (Registrar entrada, Pedidos, Cancelar pedido, Inventario) es una página hija.
@Component({
  selector: 'app-recepcion-almacen',
  imports: [RouterOutlet, Topbar, Sidebar],
  templateUrl: './recepcion-almacen.html',
  styleUrl: './recepcion-almacen.css',
})
export class RecepcionAlmacen {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = ALMACEN_MENU;

  constructor() {
    recargarAlmacen();
  }
}
