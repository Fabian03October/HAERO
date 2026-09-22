import { Component } from '@angular/core';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';

@Component({
  selector: 'app-panel-almacen',
  imports: [Topbar, Sidebar],
  templateUrl: './panel-almacen.html',
  styleUrl: './panel-almacen.css',
})
export class PanelAlmacen {
  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/almacen' },
    { etiqueta: 'Entradas' },
    { etiqueta: 'Salidas' },
    { etiqueta: 'Lotes y caducidades' },
  ];
}
