import { Routes } from '@angular/router';
import { Login } from './login/login';
import { CambiarContrasena } from './cambiar-contrasena/cambiar-contrasena';

export const AUTH_ROUTES: Routes = [
  { path: 'login', component: Login },
  { path: 'cambiar-contrasena', component: CambiarContrasena },
];
