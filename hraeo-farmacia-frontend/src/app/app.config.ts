import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { sesionInterceptor } from './auth/sesion-interceptor';

// Las alertas de stock (HU17) las abre y cierra el backend (ServicioRotacion,
// tarea programada cada hora + reevaluación en cada consulta), no el cliente;
// no hace falta instanciar nada en el arranque de la app para eso.
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Avisa y manda al login cuando la sesión dejó de ser válida (servidor reiniciado, inactividad…).
    provideHttpClient(withInterceptors([sesionInterceptor])),
  ]
};
