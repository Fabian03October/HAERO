import { Component, inject } from '@angular/core';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';

@Component({
  selector: 'app-panel-farmacia',
  imports: [Topbar, Sidebar],
  templateUrl: './panel-farmacia.html',
  styleUrl: './panel-farmacia.css',
})
export class PanelFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;

  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/farmacia' },
    { etiqueta: 'Dispensación' },
    { etiqueta: 'Recetas' },
  ];
}
