import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../auth/session';
import { Topbar } from '../shared/topbar/topbar';
import { Sidebar } from '../shared/sidebar/sidebar';
import { recargarAlmacen } from '../almacen/recargar-almacen';
import { MovimientosEspecialesAlmacen } from '../almacen/movimientos-especiales-almacen';
import { SUPERVISION_MENU } from './supervision-nav';

// Contenedor de Consultas y Reportes de Supervisión: barra superior + menú lateral,
// y cada consulta o reporte es una página hija. Todo es de solo lectura (GET).
@Component({
  selector: 'app-seccion-supervision',
  imports: [RouterOutlet, Topbar, Sidebar],
  template: `
    <div class="panel">
      <app-topbar
        [nombreCompleto]="session.usuarioActual()?.nombreCompleto ?? ''"
        [rol]="rolEtiqueta[session.usuarioActual()!.rol]"
      />
      <div class="shell">
        <app-sidebar [items]="items" />
        <main class="main">
          <div class="reception-page">
            <router-outlet />
          </div>
        </main>
      </div>
    </div>
  `,
  styleUrl: '../almacen/recepcion-almacen/recepcion-almacen.css',
})
export class SeccionSupervision {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = SUPERVISION_MENU;

  constructor() {
    // Al entrar se lee lo vigente en el servidor.
    recargarAlmacen();
    inject(MovimientosEspecialesAlmacen).recargar();
  }
}
