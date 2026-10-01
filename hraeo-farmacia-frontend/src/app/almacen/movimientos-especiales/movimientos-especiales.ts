import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { ALMACEN_MENU } from '../almacen-nav';
import { recargarAlmacen } from '../recargar-almacen';
import { MovimientosEspecialesAlmacen } from '../movimientos-especiales-almacen';

// Contenedor de Movimientos especiales: canje (HU18), caducados (HU19) y
// préstamos y transferencias (HU20), cada uno en su página del submenú.
@Component({
  selector: 'app-movimientos-especiales',
  imports: [RouterOutlet, Topbar, Sidebar],
  templateUrl: './movimientos-especiales.html',
  styleUrl: '../recepcion-almacen/recepcion-almacen.css',
})
export class MovimientosEspeciales {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = ALMACEN_MENU;

  constructor() {
    recargarAlmacen();
    inject(MovimientosEspecialesAlmacen).recargar();
  }
}
