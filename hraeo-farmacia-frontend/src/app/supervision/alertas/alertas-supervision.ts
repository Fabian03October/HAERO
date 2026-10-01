import { Component, inject } from '@angular/core';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { AlertasStock } from '../../almacen/rotacion-alertas/alertas/alertas-stock';
import { SUPERVISION_MENU } from '../supervision-nav';

// Supervisión · Alertas de stock (HU17 CA4). Misma consulta que ve Almacén, en solo lectura.
@Component({
  selector: 'app-alertas-supervision',
  imports: [Topbar, Sidebar, AlertasStock],
  templateUrl: './alertas-supervision.html',
  styleUrl: '../../almacen/recepcion-almacen/recepcion-almacen.css',
})
export class AlertasSupervision {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = SUPERVISION_MENU;
}
