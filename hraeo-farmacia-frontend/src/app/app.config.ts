import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';
import { RotacionAlmacen } from './almacen/rotacion-almacen';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    // Se crea al arrancar para que las alertas de stock (HU17) se abran o cierren
    // con cada movimiento, aunque nadie tenga abierta la pantalla de alertas.
    provideAppInitializer(() => {
      inject(RotacionAlmacen);
    }),
  ]
};
