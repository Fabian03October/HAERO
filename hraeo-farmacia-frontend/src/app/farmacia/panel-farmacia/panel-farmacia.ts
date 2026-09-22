import { Component } from '@angular/core';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';

@Component({
  selector: 'app-panel-farmacia',
  imports: [Topbar, Sidebar],
  templateUrl: './panel-farmacia.html',
  styleUrl: './panel-farmacia.css',
})
export class PanelFarmacia {
  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/farmacia' },
    { etiqueta: 'Dispensación' },
    { etiqueta: 'Recetas' },
  ];
}
