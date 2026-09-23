import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { UsuariosApi } from '../usuarios-api';

@Component({
  selector: 'app-restablecer-contrasena',
  imports: [RouterLink],
  templateUrl: './restablecer-contrasena.html',
  styleUrl: './restablecer-contrasena.css',
})
export class RestablecerContrasena {
  private readonly usuariosApi = inject(UsuariosApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  readonly nombreUsuario = signal('');
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.usuariosApi.obtener(this.id).subscribe({
      next: (usuario) => {
        this.nombreUsuario.set(usuario.nombreCompleto);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar el usuario.');
        this.cargando.set(false);
      },
    });
  }

  guardar(contrasenaTemporal: string, confirmar: string): void {
    if (contrasenaTemporal !== confirmar) {
      this.error.set('Las contraseñas no coinciden.');
      return;
    }

    this.error.set(null);
    this.guardando.set(true);

    this.usuariosApi.restablecerContrasena(this.id, contrasenaTemporal).subscribe({
      next: () => this.router.navigateByUrl('/admin/usuarios'),
      error: (err) => {
        this.guardando.set(false);
        this.error.set(err?.error?.mensaje ?? 'No se pudo restablecer la contraseña.');
      },
    });
  }
}
