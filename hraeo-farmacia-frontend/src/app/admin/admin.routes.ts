import { Routes } from '@angular/router';
import { PanelAdmin } from './panel-admin/panel-admin';
import { GestionUsuarios } from './gestion-usuarios/gestion-usuarios';
import { FormularioUsuario } from './formulario-usuario/formulario-usuario';
import { roleGuard } from '../auth/role-guard';

export const ADMIN_ROUTES: Routes = [
  { path: '', component: PanelAdmin, canActivate: [roleGuard], data: { roles: ['ADMIN'] } },
  { path: 'usuarios', component: GestionUsuarios, canActivate: [roleGuard], data: { roles: ['ADMIN'] } },
  { path: 'usuarios/nuevo', component: FormularioUsuario, canActivate: [roleGuard], data: { roles: ['ADMIN'] } },
  { path: 'usuarios/:id/editar', component: FormularioUsuario, canActivate: [roleGuard], data: { roles: ['ADMIN'] } },
];
