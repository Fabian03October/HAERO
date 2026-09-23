import { Component, inject } from '@angular/core';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';

@Component({
  selector: 'app-panel-almacen',
  imports: [Topbar, Sidebar],
  templateUrl: './panel-almacen.html',
  styleUrl: './panel-almacen.css',
})
export class PanelAlmacen {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;

  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/almacen' },
    { etiqueta: 'Entradas' },
    { etiqueta: 'Salidas' },
    { etiqueta: 'Lotes y caducidades' },
  ];
}
