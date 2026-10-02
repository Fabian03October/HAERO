import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';

// Las alertas de stock (HU17) las abre y cierra el backend (ServicioRotacion,
// tarea programada cada hora + reevaluación en cada consulta), no el cliente;
// no hace falta instanciar nada en el arranque de la app para eso.
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
  ]
};
