import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

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

export const ROL_ETIQUETA: Record<Rol, string> = {
  ALMACEN: 'Almacén',
  FARMACIA: 'Farmacia',
  SUPERVISION: 'Supervisión',
  ADMIN: 'Administrador',
};

const API_BASE_URL = 'http://localhost:8080/api';

interface LoginResponse {
  token: string;
  rol: Rol;
  debeCambiarContrasena: boolean;
  nombreCompleto: string;
}

@Injectable({
  providedIn: 'root',
})
export class Session {
  private readonly http = inject(HttpClient);

  readonly usuarioActual = signal<UsuarioSesion | null>(null);

  private token: string | null = null;

  obtenerToken(): string | null {
    return this.token;
  }

  async iniciarSesion(nombreUsuario: string, contrasena: string): Promise<UsuarioSesion | null> {
    try {
      const respuesta = await firstValueFrom(
        this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, { nombreUsuario, contrasena }),
      );

      this.token = respuesta.token;
      const usuario: UsuarioSesion = {
        nombreCompleto: respuesta.nombreCompleto,
        nombreUsuario,
        rol: respuesta.rol,
        debeCambiarContrasena: respuesta.debeCambiarContrasena,
      };
      this.usuarioActual.set(usuario);
      return usuario;
    } catch {
      return null;
    }
  }

  cerrarSesion(): void {
    const token = this.token;
    this.token = null;
    this.usuarioActual.set(null);

    if (token) {
      this.http
        .post(`${API_BASE_URL}/auth/logout`, {}, { headers: { Authorization: `Bearer ${token}` } })
        .subscribe({ error: () => {} });
    }
  }

  contrasenaCambiada(): void {
    const actual = this.usuarioActual();
    if (actual) {
      this.usuarioActual.set({ ...actual, debeCambiarContrasena: false });
    }
  }
}
