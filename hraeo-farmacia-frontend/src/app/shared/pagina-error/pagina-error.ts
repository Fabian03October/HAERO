import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CONTACTO_ADMINISTRADOR, RUTA_POR_ROL, Session } from '../../auth/session';

export type TipoError = 'no-encontrada' | 'sin-acceso' | 'error-sistema' | 'sin-conexion';

// Texto de cada página de error; el código es el que se ve en grande.
const TEXTOS: Record<TipoError, { codigo: string; titulo: string; texto: string }> = {
  'no-encontrada': {
    codigo: '404',
    titulo: 'Página no encontrada',
    texto: 'La dirección que abriste no existe en HAERO. Revisa el enlace o regresa a tu panel.',
  },
  'sin-acceso': {
    codigo: '403',
    titulo: 'Sin acceso',
    texto: `Tu usuario no tiene permiso para esta sección. Si crees que es un error, escribe al administrador: ${CONTACTO_ADMINISTRADOR}.`,
  },
  'error-sistema': {
    codigo: '500',
    titulo: 'Error del sistema',
    texto: 'El servidor tuvo un problema al consultar la información. Intenta de nuevo; si sigue pasando, avisa a Sistemas.',
  },
  'sin-conexion': {
    codigo: 'Sin conexión',
    titulo: 'No hay conexión con el servidor',
    texto: 'HAERO no pudo comunicarse con el servidor. Revisa la red del equipo e intenta de nuevo.',
  },
};

// Páginas de error de todo el sistema (404, 403, 500 y sin conexión). El tipo llega en la ruta;
// "volver" es la pantalla que falló, para el botón Reintentar.
@Component({
  selector: 'app-pagina-error',
  template: `
    <main class="pagina-error">
      <section class="card" role="alert">
        <p class="codigo">{{ textos().codigo }}</p>
        <h1>{{ textos().titulo }}</h1>
        <p class="texto">{{ textos().texto }}</p>
        <div class="acciones">
          @if (volver) {
            <button class="btn" type="button" (click)="reintentar()">Reintentar</button>
          }
          <button class="btn" type="button" [class.secondary]="!!volver" (click)="irAlPanel()">{{ destino().etiqueta }}</button>
        </div>
      </section>
    </main>
  `,
  styles: `
    .pagina-error { min-height: 100vh; display: grid; place-items: center; padding: 16px; background: var(--color-bg); }
    .card { width: 100%; max-width: 480px; text-align: center; border-top: 4px solid var(--color-guinda); }
    .codigo { margin: 0; color: var(--color-dorado); font-size: 40px; font-weight: 700; }
    h1 { margin: 8px 0; color: var(--color-guinda); font-size: 22px; }
    .texto { margin: 0 0 20px; color: var(--color-text-muted); font-size: 14px; }
    .acciones { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
  `,
})
export class PaginaError {
  private readonly router = inject(Router);
  private readonly session = inject(Session);
  private readonly ruta = inject(ActivatedRoute);

  readonly tipo = (this.ruta.snapshot.data['tipo'] ?? 'no-encontrada') as TipoError;
  readonly volver = this.ruta.snapshot.queryParamMap.get('volver');
  readonly textos = computed(() => TEXTOS[this.tipo]);
  readonly destino = computed(() => {
    const rol = this.session.usuarioActual()?.rol;
    return rol ? { url: RUTA_POR_ROL[rol], etiqueta: 'Ir a mi panel' } : { url: '/login', etiqueta: 'Ir al inicio de sesión' };
  });

  reintentar(): void {
    if (this.volver) this.router.navigateByUrl(this.volver);
  }

  irAlPanel(): void {
    this.router.navigateByUrl(this.destino().url);
  }
}
