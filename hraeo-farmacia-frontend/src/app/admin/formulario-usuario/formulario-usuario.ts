import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Rol } from '../../auth/session';
import { UsuariosApi } from '../usuarios-api';

@Component({
  selector: 'app-formulario-usuario',
  imports: [RouterLink],
  templateUrl: './formulario-usuario.html',
  styleUrl: './formulario-usuario.css',
})
export class FormularioUsuario {
  private readonly usuariosApi = inject(UsuariosApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly idParam = this.route.snapshot.paramMap.get('id');
  readonly modoEdicion = this.idParam !== null;

  readonly error = signal<string | null>(null);
  readonly guardando = signal(false);
  readonly cargando = signal(this.modoEdicion);

  readonly nombreCompletoInicial = signal('');
  readonly nombreUsuarioInicial = signal('');
  readonly correoInicial = signal('');
  readonly rolInicial = signal('');

  constructor() {
    if (this.modoEdicion && this.idParam) {
      this.usuariosApi.obtener(Number(this.idParam)).subscribe({
        next: (usuario) => {
          this.nombreCompletoInicial.set(usuario.nombreCompleto);
          this.nombreUsuarioInicial.set(usuario.nombreUsuario);
          this.correoInicial.set(usuario.correo);
          this.rolInicial.set(usuario.rol);
          this.cargando.set(false);
        },
        error: () => {
          this.error.set('No se pudo cargar el usuario.');
          this.cargando.set(false);
        },
      });
    }
  }

  guardar(nombreCompleto: string, nombreUsuario: string, correo: string, contrasenaTemporal: string, rol: string): void {
    if (!rol) {
      this.error.set('Selecciona un rol.');
      return;
    }

    this.error.set(null);
    this.guardando.set(true);

    const peticion =
      this.modoEdicion && this.idParam
        ? this.usuariosApi.editar(Number(this.idParam), { nombreCompleto, correo, rol: rol as Rol })
        : this.usuariosApi.crear({ nombreCompleto, nombreUsuario, correo, contrasenaTemporal, rol: rol as Rol });

    peticion.subscribe({
      next: () => this.router.navigateByUrl('/admin/usuarios'),
      error: (err) => {
        this.guardando.set(false);
        this.error.set(err?.error?.mensaje ?? 'No se pudo guardar el usuario.');
      },
    });
  }
}
