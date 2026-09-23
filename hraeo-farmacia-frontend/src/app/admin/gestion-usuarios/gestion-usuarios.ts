import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';
import { Usuario, UsuariosApi } from '../usuarios-api';

@Component({
  selector: 'app-gestion-usuarios',
  imports: [RouterLink, Topbar, Sidebar],
  templateUrl: './gestion-usuarios.html',
  styleUrl: './gestion-usuarios.css',
})
export class GestionUsuarios {
  private readonly usuariosApi = inject(UsuariosApi);

  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;

  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/admin' },
    { etiqueta: 'Usuarios', routerLink: '/admin/usuarios' },
  ];

  readonly usuarios = signal<Usuario[]>([]);
  readonly cargando = signal(true);

  constructor() {
    this.cargarUsuarios();
  }

  buscar(nombre: string): void {
    this.cargarUsuarios(nombre);
  }

  desactivar(id: number): void {
    if (!confirm('¿Desactivar a este usuario?')) {
      return;
    }
    this.usuariosApi.desactivar(id).subscribe({
      next: () => this.cargarUsuarios(),
      error: (err) => alert(err?.error?.mensaje ?? 'No se pudo desactivar el usuario.'),
    });
  }

  reactivar(id: number): void {
    this.usuariosApi.reactivar(id).subscribe({
      next: () => this.cargarUsuarios(),
      error: (err) => alert(err?.error?.mensaje ?? 'No se pudo reactivar el usuario.'),
    });
  }

  private cargarUsuarios(nombre?: string): void {
    this.cargando.set(true);
    this.usuariosApi.listar(nombre).subscribe({
      next: (usuarios) => {
        this.usuarios.set(usuarios);
        this.cargando.set(false);
      },
      error: () => {
        this.usuarios.set([]);
        this.cargando.set(false);
      },
    });
  }
}
