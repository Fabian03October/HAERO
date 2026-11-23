import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CONTACTO_ADMINISTRADOR, RUTA_POR_ROL, Session } from '../session';
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
    const destino = usuario.debeCambiarContrasena ? '/cambiar-contrasena' : (this.volver ?? RUTA_POR_ROL[usuario.rol]);
    this.router.navigateByUrl(destino);
  }

  // Evita redirigir a otro sitio ("//sitio.com") o de regreso al login.
  private rutaInterna(url: string | null): string | null {
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : null;
  }
}
