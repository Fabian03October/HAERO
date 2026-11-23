import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Session } from './session';

// Motivos que el backend manda en el encabezado X-Auth-Error (JwtAuthenticationFilter).
const MOTIVOS: Record<string, string> = {
  inactividad: 'Tu sesión se cerró por 15 minutos de inactividad. Vuelve a iniciar sesión.',
  'sesion-cerrada':
    'Tu sesión ya no es válida: se inició sesión con tu usuario en otro equipo, se cerró desde otro lugar, se cambió la contraseña o el administrador cambió tu rol. Vuelve a iniciar sesión.',
  'cuenta-inactiva': 'Tu cuenta fue desactivada. Consulta con el administrador.',
};

// Sin encabezado: el token no lo reconoce el servidor (por ejemplo, porque se reinició).
const MOTIVO_TOKEN_INVALIDO = 'Tu sesión expiró. Vuelve a iniciar sesión.';

const PAGINAS_ERROR = ['/sin-conexion', '/error-sistema'];

/**
 * Errores comunes de todas las pantallas:
 * - 401 o X-Auth-Error: la sesión ya no es válida → al login con el motivo.
 * - 403: la sesión es válida pero el rol no tiene permiso → la pantalla muestra el mensaje.
 * - Sin conexión (0) o falla del servidor (500, 502, 504) al CONSULTAR → página de error con
 *   "Reintentar". Al guardar no se cambia de página, para no perder lo capturado.
 *   El 503 (p. ej. Intelisis no responde) lo muestra cada pantalla, porque el resto sí funciona.
 */
export const sesionInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(Session);
  const router = inject(Router);

  // Cada petición con sesión cuenta como actividad para el servidor (aviso de inactividad).
  if (request.headers.has('Authorization')) session.marcarActividad();

  return next(request).pipe(
    catchError((error: unknown) => {
      const rol = session.usuarioActual()?.rol;
      // El servidor exige primero cambiar la contraseña temporal o aceptar las políticas
      // (HU08 CA4): la sesión sigue siendo válida, solo se manda a esa pantalla.
      const pendiente = error instanceof HttpErrorResponse ? error.headers.get('X-Auth-Pendiente') : null;
      if (pendiente && rol) {
        session.marcarPendiente(pendiente === 'cambiar-contrasena' ? 'contrasena' : 'politicas');
        router.navigateByUrl(pendiente === 'cambiar-contrasena' ? '/cambiar-contrasena' : '/politicas');
        return throwError(() => error);
      }
      if (error instanceof HttpErrorResponse && rol && request.headers.has('Authorization')) {
        const motivo = error.headers.get('X-Auth-Error');
        if (motivo || error.status === 401) {
          session.expirar(MOTIVOS[motivo ?? ''] ?? MOTIVO_TOKEN_INVALIDO);
          router.navigateByUrl(urlLogin(router.url));
        } else if (request.method === 'GET' && [0, 500, 502, 504].includes(error.status)) {
          const actual = router.url ?? '';
          if (!PAGINAS_ERROR.some((p) => actual.startsWith(p))) {
            const pagina = error.status === 0 ? '/sin-conexion' : '/error-sistema';
            router.navigateByUrl(`${pagina}?volver=${encodeURIComponent(actual)}`);
          }
        }
      }
      return throwError(() => error);
    }),
  );
};

/** Login que, al entrar de nuevo, regresa a la pantalla donde estaba el usuario. */
export function urlLogin(actual: string | undefined): string {
  const volver = actual && actual.startsWith('/') && !actual.startsWith('/login') ? actual : '';
  return volver ? `/login?volver=${encodeURIComponent(volver)}` : '/login';
}
