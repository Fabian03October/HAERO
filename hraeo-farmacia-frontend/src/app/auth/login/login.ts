import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CONTACTO_ADMINISTRADOR, Rol, RUTA_POR_ROL, Session } from '../session';

/**
 * A dónde entra el usuario: a la pantalla donde se cerró la sesión solo si es de su módulo o común
 * (la pudo dejar otra persona con otro rol en la misma computadora); si no, a su panel.
 */
export function destinoAlEntrar(rol: Rol, volver: string | null): string {
  const deOtroModulo = (Object.entries(RUTA_POR_ROL) as [Rol, string][])
    .some(([otro, ruta]) => otro !== rol && !!volver && (volver === ruta || volver.startsWith(`${ruta}/`) || volver.startsWith(`${ruta}?`)));
  return volver && !deOtroModulo ? volver : RUTA_POR_ROL[rol];
}
import { CampoContrasena } from '../../shared/campo-contrasena/campo-contrasena';

@Component({
  selector: 'app-login',
  imports: [CampoContrasena],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  protected readonly session = inject(Session);
  private readonly router = inject(Router);

  readonly error = signal(false);

  constructor() {
    // Llegar al login (por ejemplo, con la flecha de atrás del navegador) cierra la sesión abierta:
    // así la flecha de adelante ya no vuelve a entrar sin iniciar sesión.
    if (this.session.usuarioActual()) this.session.cerrarSesion();
  }
  readonly contacto = CONTACTO_ADMINISTRADOR;
  // Pantalla donde estaba al cerrarse la sesión; al volver a entrar regresa ahí (solo rutas internas).
  private readonly volver = this.rutaInterna(inject(ActivatedRoute).snapshot.queryParamMap.get('volver'));

  async iniciarSesion(nombreUsuario: string, contrasena: string): Promise<void> {
    const usuario = await this.session.iniciarSesion(nombreUsuario, contrasena);
    if (!usuario) {
      this.error.set(true);
      return;
    }

    this.error.set(false);
    const destino = usuario.debeCambiarContrasena ? '/cambiar-contrasena' : destinoAlEntrar(usuario.rol, this.volver);
    this.router.navigateByUrl(destino);
  }

  // Evita redirigir a otro sitio ("//sitio.com") o de regreso al login.
  private rutaInterna(url: string | null): string | null {
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : null;
  }
}
