import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../api-config';

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


const SESSION_STORAGE_KEY = 'hraeo.session';
const TOKEN_STORAGE_KEY = 'hraeo.token';

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
  // Por qué se cerró la sesión sin que el usuario lo pidiera; el login lo muestra.
  readonly motivoSalida = signal('');

  private token: string | null = null;

  constructor() {
    this.restaurarSesion();
  }

  obtenerToken(): string | null {
    return this.token;
  }

  async iniciarSesion(nombreUsuario: string, contrasena: string): Promise<UsuarioSesion | null> {
    try {
      const respuesta = await firstValueFrom(
        this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, { nombreUsuario, contrasena }),
      );

      this.token = respuesta.token;
      this.motivoSalida.set('');
      const usuario: UsuarioSesion = {
        nombreCompleto: respuesta.nombreCompleto,
        nombreUsuario,
        rol: respuesta.rol,
        debeCambiarContrasena: respuesta.debeCambiarContrasena,
      };
      this.usuarioActual.set(usuario);
      this.persistirSesion(usuario);
      return usuario;
    } catch {
      return null;
    }
  }

  cerrarSesion(): void {
    const token = this.token;
    this.token = null;
    this.usuarioActual.set(null);
    localStorage.removeItem(SESSION_STORAGE_KEY);
    localStorage.removeItem(TOKEN_STORAGE_KEY);

    if (token) {
      this.http
        .post(`${API_BASE_URL}/auth/logout`, {}, { headers: { Authorization: `Bearer ${token}` } })
        .subscribe({ error: () => {} });
    }
  }

  /**
   * La sesión dejó de ser válida en el servidor: se borra localmente (sin llamar
   * a /logout, que también fallaría) y se guarda el motivo para el login.
   */
  expirar(motivo: string): void {
    if (!this.usuarioActual()) return;
    this.token = null;
    this.usuarioActual.set(null);
    localStorage.removeItem(SESSION_STORAGE_KEY);
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    this.motivoSalida.set(motivo);
  }

  restaurarSesion(): void {
    const usuarioGuardado = localStorage.getItem(SESSION_STORAGE_KEY);
    const tokenGuardado = localStorage.getItem(TOKEN_STORAGE_KEY);

    if (!usuarioGuardado || !tokenGuardado) {
      return;
    }

    try {
      const usuario = JSON.parse(usuarioGuardado) as UsuarioSesion;
      if (usuario.nombreUsuario && usuario.nombreCompleto && usuario.rol) {
        this.token = tokenGuardado;
        this.usuarioActual.set(usuario);
      }
    } catch {
      localStorage.removeItem(SESSION_STORAGE_KEY);
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  }

  private persistirSesion(usuario: UsuarioSesion): void {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(usuario));
    if (this.token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, this.token);
    }
  }
}
