import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';
import { Usuario, UsuariosApi } from '../usuarios-api';
import { Notificaciones } from '../../shared/notificaciones/notificaciones';

@Component({
  selector: 'app-gestion-usuarios',
  imports: [RouterLink, Topbar, Sidebar],
  templateUrl: './gestion-usuarios.html',
  styleUrl: './gestion-usuarios.css',
})
export class GestionUsuarios {
  private readonly usuariosApi = inject(UsuariosApi);
  private readonly notificaciones = inject(Notificaciones);

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

  async desactivar(usuario: Usuario): Promise<void> {
    const confirmado = await this.notificaciones.confirmar({
      titulo: '¿Desactivar a este usuario?',
      mensaje: `${usuario.nombreCompleto} ya no podrá entrar al sistema. Puedes reactivarlo después.`,
      textoAceptar: 'Sí, desactivar',
    });
    if (!confirmado) return;
    this.usuariosApi.desactivar(usuario.id).subscribe({
      next: () => {
        this.notificaciones.exito('Usuario desactivado', `${usuario.nombreCompleto} ya no puede entrar al sistema.`);
        this.cargarUsuarios();
      },
      error: (err) => this.notificaciones.error(err?.error?.mensaje ?? 'No se pudo desactivar el usuario.'),
    });
  }

  reactivar(usuario: Usuario): void {
    this.usuariosApi.reactivar(usuario.id).subscribe({
      next: () => {
        this.notificaciones.exito('Usuario reactivado', `${usuario.nombreCompleto} puede volver a entrar al sistema.`);
        this.cargarUsuarios();
      },
      error: (err) => this.notificaciones.error(err?.error?.mensaje ?? 'No se pudo reactivar el usuario.'),
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
