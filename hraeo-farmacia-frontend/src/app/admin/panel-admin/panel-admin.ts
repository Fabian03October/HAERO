import { Component } from '@angular/core';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';

@Component({
  selector: 'app-panel-admin',
  imports: [Topbar, Sidebar],
  templateUrl: './panel-admin.html',
  styleUrl: './panel-admin.css',
})
export class PanelAdmin {
  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/admin' },
    { etiqueta: 'Usuarios', routerLink: '/admin/usuarios' },
  ];
}
