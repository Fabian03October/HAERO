import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { RUTA_POR_ROL, Session } from '../session';
import { UsuariosApi } from '../../admin/usuarios-api';

@Component({
  selector: 'app-cambiar-contrasena',
  imports: [],
  templateUrl: './cambiar-contrasena.html',
  styleUrl: './cambiar-contrasena.css',
})
export class CambiarContrasena {
  private readonly session = inject(Session);
  private readonly usuariosApi = inject(UsuariosApi);
  private readonly router = inject(Router);

  readonly esTemporal = computed(() => this.session.usuarioActual()?.debeCambiarContrasena ?? false);
  readonly error = signal<string | null>(null);
  readonly guardando = signal(false);

  guardar(contrasenaActual: string, contrasenaNueva: string, confirmar: string): void {
    if (contrasenaNueva !== confirmar) {
      this.error.set('Las contraseñas no coinciden.');
      return;
    }

    this.error.set(null);
    this.guardando.set(true);

    this.usuariosApi.cambiarMiContrasena({ contrasenaActual, contrasenaNueva }).subscribe({
      next: () => {
        this.session.contrasenaCambiada();
        const usuario = this.session.usuarioActual();
        this.router.navigateByUrl(usuario ? RUTA_POR_ROL[usuario.rol] : '/login');
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(err?.error?.mensaje ?? 'No se pudo cambiar la contraseña.');
      },
    });
  }
}
