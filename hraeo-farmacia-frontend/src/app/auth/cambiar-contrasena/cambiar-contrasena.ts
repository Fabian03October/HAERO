import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { RUTA_POR_ROL, Session } from '../session';

@Component({
  selector: 'app-cambiar-contrasena',
  imports: [],
  templateUrl: './cambiar-contrasena.html',
  styleUrl: './cambiar-contrasena.css',
})
export class CambiarContrasena {
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  readonly esTemporal = computed(() => this.session.usuarioActual()?.debeCambiarContrasena ?? false);

  guardar(): void {
    const usuario = this.session.usuarioActual();
    this.session.contrasenaCambiada();
    this.router.navigateByUrl(usuario ? RUTA_POR_ROL[usuario.rol] : '/login');
  }
}
