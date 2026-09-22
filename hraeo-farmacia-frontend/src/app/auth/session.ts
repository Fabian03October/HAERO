import { Injectable, signal } from '@angular/core';

export type Rol = 'ALMACEN' | 'FARMACIA' | 'SUPERVISION' | 'ADMIN';

export interface UsuarioSesion {
  nombreCompleto: string;
  nombreUsuario: string;
  rol: Rol;
  debeCambiarContrasena: boolean;
}

export const RUTA_POR_ROL: Record<Rol, string> = {
  ALMACEN: '/almacen',
  FARMACIA: '/farmacia',
  SUPERVISION: '/supervision',
  ADMIN: '/admin',
};

interface UsuarioPrueba extends UsuarioSesion {
  contrasena: string;
  activo: boolean;
}

// Usuarios de prueba en memoria, solo para navegar el prototipo sin backend.
// Se reemplaza por la llamada real a ServicioAutenticacion cuando exista la API.
// Contraseña de todos: 1234
const USUARIOS_PRUEBA: UsuarioPrueba[] = [
  { nombreCompleto: 'Ana Torres', nombreUsuario: 'atorres', rol: 'ALMACEN', activo: true, debeCambiarContrasena: false, contrasena: '1234' },
  { nombreCompleto: 'Luis Ramos', nombreUsuario: 'lramos', rol: 'FARMACIA', activo: true, debeCambiarContrasena: false, contrasena: '1234' },
  { nombreCompleto: 'Marta Díaz', nombreUsuario: 'mdiaz', rol: 'SUPERVISION', activo: true, debeCambiarContrasena: false, contrasena: '1234' },
  { nombreCompleto: 'Iván Paz', nombreUsuario: 'ipaz', rol: 'ADMIN', activo: true, debeCambiarContrasena: false, contrasena: '1234' },
  { nombreCompleto: 'Carlos Nieto', nombreUsuario: 'cnieto', rol: 'FARMACIA', activo: false, debeCambiarContrasena: false, contrasena: '1234' },
  { nombreCompleto: 'Usuario Nuevo', nombreUsuario: 'nuevo', rol: 'ALMACEN', activo: true, debeCambiarContrasena: true, contrasena: '1234' },
];

@Injectable({
  providedIn: 'root',
})
export class Session {
  readonly usuarioActual = signal<UsuarioSesion | null>(null);

  iniciarSesion(nombreUsuario: string, contrasena: string): UsuarioSesion | null {
    const encontrado = USUARIOS_PRUEBA.find(
      (u) => u.nombreUsuario === nombreUsuario && u.contrasena === contrasena && u.activo,
    );
    if (!encontrado) {
      return null;
    }
    const { contrasena: _contrasena, activo: _activo, ...usuario } = encontrado;
    this.usuarioActual.set(usuario);
    return usuario;
  }

  cerrarSesion(): void {
    this.usuarioActual.set(null);
  }

  contrasenaCambiada(): void {
    const actual = this.usuarioActual();
    if (actual) {
      this.usuarioActual.set({ ...actual, debeCambiarContrasena: false });
    }
  }
}
