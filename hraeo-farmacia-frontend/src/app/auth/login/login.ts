import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { RUTA_POR_ROL, Session } from '../session';

@Component({
  selector: 'app-login',
  imports: [],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  readonly error = signal(false);

  async iniciarSesion(nombreUsuario: string, contrasena: string): Promise<void> {
    const usuario = await this.session.iniciarSesion(nombreUsuario, contrasena);
    if (!usuario) {
      this.error.set(true);
      return;
    }

    this.error.set(false);
    const destino = usuario.debeCambiarContrasena ? '/cambiar-contrasena' : RUTA_POR_ROL[usuario.rol];
    this.router.navigateByUrl(destino);
  }
}
