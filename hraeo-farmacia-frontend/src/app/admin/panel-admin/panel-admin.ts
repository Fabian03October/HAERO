import { Component, inject } from '@angular/core';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';

@Component({
  selector: 'app-panel-admin',
  imports: [Topbar, Sidebar],
  templateUrl: './panel-admin.html',
  styleUrl: './panel-admin.css',
})
export class PanelAdmin {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;

  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/admin' },
    { etiqueta: 'Usuarios', routerLink: '/admin/usuarios' },
  ];
}
