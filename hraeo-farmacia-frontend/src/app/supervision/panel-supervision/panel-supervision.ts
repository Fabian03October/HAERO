import { Component } from '@angular/core';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';

@Component({
  selector: 'app-panel-supervision',
  imports: [Topbar, Sidebar],
  templateUrl: './panel-supervision.html',
  styleUrl: './panel-supervision.css',
})
export class PanelSupervision {
  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/supervision' },
    { etiqueta: 'Consultas', etiquetaExtra: '(solo lectura)' },
    { etiqueta: 'Reportes', etiquetaExtra: '(solo lectura)' },
  ];
}
