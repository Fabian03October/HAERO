import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { API_BASE_URL } from '../../api-config';
import { LIMITE_INACTIVIDAD_MS, Session, TOKEN_STORAGE_KEY } from '../../auth/session';
import { urlLogin } from '../../auth/sesion-interceptor';

// Cuánto antes de que el servidor cierre la sesión aparece el aviso.
const AVISO_ANTES_MS = 2 * 60 * 1000;
const MOTIVO_INACTIVIDAD = 'Tu sesión se cerró por 15 minutos de inactividad. Vuelve a iniciar sesión.';

/**
 * Vigila la sesión en todas las pantallas (va en app.html):
 * - Si en otra pestaña se cierra la sesión, esta también se cierra y va al login.
 * - Al volver a la pestaña se pregunta al servidor si la sesión sigue válida (p. ej. se entró
 *   con el mismo usuario en otra computadora); si no, el interceptor manda al login con el motivo.
 * - Dos minutos antes de que el servidor la cierre por inactividad avisa con "Seguir trabajando",
 *   para no perder lo que se está capturando. Si se vence, al entrar de nuevo regresa a la misma pantalla.
 */
@Component({
  selector: 'app-vigilante-sesion',
  template: `
    @if (restante() !== null) {
      <div class="fondo">
        <section class="aviso card" role="alertdialog" aria-modal="true" aria-labelledby="titulo-inactividad" aria-describedby="texto-inactividad">
          <p class="eyebrow">Sesión por vencer</p>
          <h2 id="titulo-inactividad">¿Sigues ahí?</h2>
          <p id="texto-inactividad">Por seguridad, tu sesión se cerrará en <strong>{{ reloj() }}</strong> por inactividad.</p>
          <div class="acciones">
            <button class="btn" type="button" (click)="seguir()">Seguir trabajando</button>
            <button class="btn secondary" type="button" (click)="salir()">Cerrar sesión</button>
          </div>
        </section>
      </div>
    }
  `,
  styles: `
    .fondo { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 16px; background: rgb(0 0 0 / 35%); }
    .aviso { width: 100%; max-width: 420px; text-align: center; border-top: 4px solid var(--color-dorado); }
    .eyebrow { margin: 0 0 6px; color: var(--color-dorado); font-size: 11px; font-weight: 800; letter-spacing: 0.06em; text-transform: uppercase; }
    h2 { margin: 0 0 8px; color: var(--color-guinda); font-size: 20px; }
    p { margin: 0 0 18px; color: var(--color-text); font-size: 14px; }
    .acciones { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
  `,
})
export class VigilanteSesion {
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);

  // Milisegundos para que se venza; null = sin aviso.
  readonly restante = signal<number | null>(null);

  constructor() {
    const alCambiarOtraPestana = (evento: StorageEvent) => {
      if (evento.key !== TOKEN_STORAGE_KEY) return;
      const habiaSesion = !!this.session.usuarioActual();
      this.session.sincronizarConOtraPestana();
      if (habiaSesion && !this.session.usuarioActual()) this.router.navigateByUrl('/login');
    };
    const alVolver = () => {
      if (document.visibilityState !== 'visible' || !this.session.usuarioActual()) return;
      this.session.sincronizarConOtraPestana();
      if (this.session.usuarioActual()) this.verificar();
      else this.router.navigateByUrl('/login');
    };
    window.addEventListener('storage', alCambiarOtraPestana);
    document.addEventListener('visibilitychange', alVolver);
    const reloj = setInterval(() => this.revisarInactividad(), 1000);
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('storage', alCambiarOtraPestana);
      document.removeEventListener('visibilitychange', alVolver);
      clearInterval(reloj);
    });
  }

  reloj(): string {
    const segundos = Math.max(0, Math.ceil((this.restante() ?? 0) / 1000));
    return `${Math.floor(segundos / 60)}:${String(segundos % 60).padStart(2, '0')}`;
  }

  /** Una petición cualquiera renueva la sesión en el servidor. */
  seguir(): void {
    this.restante.set(null);
    this.verificar();
  }

  salir(): void {
    this.restante.set(null);
    this.session.cerrarSesion();
    this.router.navigateByUrl('/login');
  }

  private verificar(): void {
    const token = this.session.obtenerToken();
    if (!token) return;
    // Si ya no es válida, el interceptor cierra la sesión y manda al login con el motivo.
    this.http.get(`${API_BASE_URL}/usuarios/me`, { headers: { Authorization: `Bearer ${token}` } }).subscribe({ error: () => {} });
  }

  private revisarInactividad(): void {
    if (!this.session.usuarioActual()) {
      this.restante.set(null);
      return;
    }
    const falta = LIMITE_INACTIVIDAD_MS - this.session.inactivoDesde();
    if (falta <= 0) {
      this.restante.set(null);
      const volver = urlLogin(this.router.url);
      this.session.expirar(MOTIVO_INACTIVIDAD);
      this.router.navigateByUrl(volver);
    } else {
      this.restante.set(falta <= AVISO_ANTES_MS ? falta : null);
    }
  }
}
