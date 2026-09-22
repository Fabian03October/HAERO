import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Rol, RUTA_POR_ROL, Session } from './session';

// Validación solo en el cliente (oculta menú y protege rutas para mejorar la
// experiencia). La seguridad real la aplica ControlAcceso en el backend.
export const roleGuard: CanActivateFn = (route) => {
  const session = inject(Session);
  const router = inject(Router);
  const usuario = session.usuarioActual();

  if (!usuario) {
    return router.parseUrl('/login');
  }

  if (usuario.debeCambiarContrasena) {
    return router.parseUrl('/cambiar-contrasena');
  }

  const rolesPermitidos = route.data['roles'] as Rol[] | undefined;
  if (rolesPermitidos && !rolesPermitidos.includes(usuario.rol)) {
    return router.parseUrl(RUTA_POR_ROL[usuario.rol]);
  }

  return true;
};
