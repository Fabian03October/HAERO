import { Component, inject } from '@angular/core';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { CpmClave } from '../../almacen/rotacion-alertas/cpm/cpm-clave';
import { RotacionAlmacen } from '../../almacen/rotacion-almacen';
import { SUPERVISION_MENU } from '../supervision-nav';

// Supervisión · CPM por clave (HU16). Misma consulta que ve Almacén, en solo lectura
// (módulo "Rotación y alertas: Supervisión solo lectura" del documento de diseño).
@Component({
  selector: 'app-cpm-supervision',
  imports: [Topbar, Sidebar, CpmClave],
  templateUrl: './cpm-supervision.html',
  styleUrl: '../../almacen/recepcion-almacen/recepcion-almacen.css',
})
export class CpmSupervision {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = SUPERVISION_MENU;

  constructor() {
    inject(RotacionAlmacen).recargar();
  }
}
