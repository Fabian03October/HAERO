import { Routes } from '@angular/router';
import { PanelFarmacia } from './panel-farmacia/panel-farmacia';
import { roleGuard } from '../auth/role-guard';

export const FARMACIA_ROUTES: Routes = [
  { path: '', component: PanelFarmacia, canActivate: [roleGuard], data: { roles: ['FARMACIA'] } },
];
