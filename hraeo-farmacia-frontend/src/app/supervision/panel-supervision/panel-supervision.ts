import { Component, inject } from '@angular/core';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';

@Component({
  selector: 'app-panel-supervision',
  imports: [Topbar, Sidebar],
  templateUrl: './panel-supervision.html',
  styleUrl: './panel-supervision.css',
})
export class PanelSupervision {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;

  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/supervision' },
    { etiqueta: 'Consultas', etiquetaExtra: '(solo lectura)' },
    { etiqueta: 'Reportes', etiquetaExtra: '(solo lectura)' },
  ];
}
