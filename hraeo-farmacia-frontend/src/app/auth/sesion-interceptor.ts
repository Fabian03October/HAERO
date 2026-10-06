import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Rol, Session } from './session';

// Motivos que el backend manda en el encabezado X-Auth-Error (JwtAuthenticationFilter).
const MOTIVOS: Record<string, string> = {
  inactividad: 'Tu sesión se cerró por 15 minutos de inactividad. Vuelve a iniciar sesión.',
  'sesion-cerrada': 'Tu sesión ya no es válida: se cerró desde otro lugar o se cambió la contraseña. Vuelve a iniciar sesión.',
  'cuenta-inactiva': 'Tu cuenta fue desactivada. Consulta con el administrador.',
};

// Sin encabezado: el token no lo reconoce el servidor (por ejemplo, porque se reinició).
const MOTIVO_TOKEN_INVALIDO = 'Tu sesión expiró. Vuelve a iniciar sesión.';

/**
 * Detecta cuando la sesión dejó de ser válida y manda al login con el motivo, en
 * lugar de dejar las pantallas vacías. El backend responde 403 tanto a un token
 * inválido como a una ruta sin permiso; por eso un 403 solo se toma como sesión
 * vencida si la ruta sí está permitida para el rol (mismas reglas que SecurityConfig).
 */
export const sesionInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(Session);
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: unknown) => {
      const rol = session.usuarioActual()?.rol;
      if (error instanceof HttpErrorResponse && rol && request.headers.has('Authorization')) {
        const motivo = error.headers.get('X-Auth-Error');
        const sesionInvalida =
          !!motivo || error.status === 401 || (error.status === 403 && rolTieneAcceso(rol, request));
        if (sesionInvalida) {
          session.expirar(MOTIVOS[motivo ?? ''] ?? MOTIVO_TOKEN_INVALIDO);
          router.navigateByUrl('/login');
        }
      }
      return throwError(() => error);
    }),
  );
};

/** Reglas de SecurityConfig: si el rol puede usar la ruta, un 403 significa sesión inválida. */
export function rolTieneAcceso(rol: Rol, request: HttpRequest<unknown>): boolean {
  const ruta = new URL(request.url, globalThis.location?.origin ?? 'http://localhost').pathname;
  const consulta = request.method === 'GET';
  if (ruta.startsWith('/api/usuarios/me/')) return true;
  if (ruta.startsWith('/api/usuarios')) return rol === 'ADMIN';
  if (ruta.startsWith('/api/almacen/medicamentos') && consulta) return rol === 'ALMACEN' || rol === 'SUPERVISION' || rol === 'FARMACIA';
  if (ruta.startsWith('/api/almacen')) return consulta ? rol === 'ALMACEN' || rol === 'SUPERVISION' : rol === 'ALMACEN';
  if (ruta.startsWith('/api/farmacia')) return consulta ? rol === 'FARMACIA' || rol === 'SUPERVISION' : rol === 'FARMACIA';
  return false;
}
