import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar, ItemMenu } from '../../shared/sidebar/sidebar';

interface FilaUsuario {
  nombreCompleto: string;
  nombreUsuario: string;
  correo: string;
  rol: string;
  activo: boolean;
}

@Component({
  selector: 'app-gestion-usuarios',
  imports: [RouterLink, Topbar, Sidebar],
  templateUrl: './gestion-usuarios.html',
  styleUrl: './gestion-usuarios.css',
})
export class GestionUsuarios {
  readonly items: ItemMenu[] = [
    { etiqueta: 'Inicio', routerLink: '/admin' },
    { etiqueta: 'Usuarios', routerLink: '/admin/usuarios' },
  ];

  // Datos de ejemplo; se reemplazan por la lista real de ServicioUsuarios (backend) más adelante.
  readonly usuarios: FilaUsuario[] = [
    { nombreCompleto: 'Ana Torres', nombreUsuario: 'atorres', correo: 'atorres@ejemplo.com', rol: 'Almacén', activo: true },
    { nombreCompleto: 'Luis Ramos', nombreUsuario: 'lramos', correo: 'lramos@ejemplo.com', rol: 'Farmacia', activo: true },
    { nombreCompleto: 'Marta Díaz', nombreUsuario: 'mdiaz', correo: 'mdiaz@ejemplo.com', rol: 'Supervisión', activo: true },
    { nombreCompleto: 'Iván Paz', nombreUsuario: 'ipaz', correo: 'ipaz@ejemplo.com', rol: 'Administrador', activo: true },
    { nombreCompleto: 'Carlos Nieto', nombreUsuario: 'cnieto', correo: 'cnieto@ejemplo.com', rol: 'Farmacia', activo: false },
  ];
}
