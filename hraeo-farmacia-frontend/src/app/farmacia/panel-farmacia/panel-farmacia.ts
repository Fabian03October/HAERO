import { Component, inject } from '@angular/core';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { FARMACIA_MENU } from '../farmacia-nav';

@Component({
  selector: 'app-panel-farmacia',
  imports: [Topbar, Sidebar],
  templateUrl: './panel-farmacia.html',
  styleUrl: './panel-farmacia.css',
})
export class PanelFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;

  readonly items = FARMACIA_MENU;
}
