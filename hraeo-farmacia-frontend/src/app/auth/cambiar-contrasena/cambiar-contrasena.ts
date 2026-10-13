import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { RUTA_POR_ROL, Session } from '../session';
import { UsuariosApi } from '../../admin/usuarios-api';
import { Notificaciones } from '../../shared/notificaciones/notificaciones';
import { CampoContrasena } from '../../shared/campo-contrasena/campo-contrasena';

@Component({
  selector: 'app-cambiar-contrasena',
  imports: [RouterLink, CampoContrasena],
  templateUrl: './cambiar-contrasena.html',
  styleUrl: './cambiar-contrasena.css',
})
export class CambiarContrasena {
  private readonly session = inject(Session);
  private readonly usuariosApi = inject(UsuariosApi);
  private readonly router = inject(Router);
  private readonly notificaciones = inject(Notificaciones);

  readonly esTemporal = computed(() => this.session.usuarioActual()?.debeCambiarContrasena ?? false);
  // Panel del rol, para el botón de volver. Con contraseña temporal no se muestra:
  // el guard de rutas lo regresaría aquí hasta que la cambie.
  readonly rutaInicio = computed(() => {
    const rol = this.session.usuarioActual()?.rol;
    return rol ? RUTA_POR_ROL[rol] : '/login';
  });
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
      next: async () => {
        // El backend invalida el token anterior al cambiar la contraseña; se pide
        // uno nuevo con la contraseña nueva para no sacar al usuario de inmediato.
        const nombreUsuario = this.session.usuarioActual()?.nombreUsuario ?? '';
        const usuario = await this.session.iniciarSesion(nombreUsuario, contrasenaNueva);
        if (usuario) this.notificaciones.exito('Contraseña actualizada', 'Usa tu nueva contraseña la próxima vez que entres.');
        else this.session.cerrarSesion();
        this.router.navigateByUrl(usuario ? RUTA_POR_ROL[usuario.rol] : '/login');
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(err?.error?.mensaje ?? 'No se pudo cambiar la contraseña.');
      },
    });
  }
}
