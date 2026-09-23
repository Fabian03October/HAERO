import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Rol, Session } from '../auth/session';

export interface Usuario {
  id: number;
  nombreCompleto: string;
  nombreUsuario: string;
  correo: string;
  rol: Rol;
  activo: boolean;
  debeCambiarContrasena: boolean;
}

export interface CrearUsuarioRequest {
  nombreCompleto: string;
  nombreUsuario: string;
  correo: string;
  contrasenaTemporal: string;
  rol: Rol;
}

export interface EditarUsuarioRequest {
  nombreCompleto: string;
  correo: string;
  rol: Rol;
}

export interface CambiarContrasenaRequest {
  contrasenaActual: string;
  contrasenaNueva: string;
}

const API_BASE_URL = 'http://localhost:8080/api';

@Injectable({
  providedIn: 'root',
})
export class UsuariosApi {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);

  private cabeceras() {
    return { headers: { Authorization: `Bearer ${this.session.obtenerToken()}` } };
  }

  listar(nombre?: string): Observable<Usuario[]> {
    const params = nombre ? { nombre } : undefined;
    return this.http.get<Usuario[]>(`${API_BASE_URL}/usuarios`, { ...this.cabeceras(), params });
  }

  obtener(id: number): Observable<Usuario> {
    return this.http.get<Usuario>(`${API_BASE_URL}/usuarios/${id}`, this.cabeceras());
  }

  crear(request: CrearUsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(`${API_BASE_URL}/usuarios`, request, this.cabeceras());
  }

  editar(id: number, request: EditarUsuarioRequest): Observable<Usuario> {
    return this.http.put<Usuario>(`${API_BASE_URL}/usuarios/${id}`, request, this.cabeceras());
  }

  desactivar(id: number): Observable<Usuario> {
    return this.http.patch<Usuario>(`${API_BASE_URL}/usuarios/${id}/desactivar`, {}, this.cabeceras());
  }

  reactivar(id: number): Observable<Usuario> {
    return this.http.patch<Usuario>(`${API_BASE_URL}/usuarios/${id}/reactivar`, {}, this.cabeceras());
  }

  restablecerContrasena(id: number, contrasenaTemporal: string): Observable<Usuario> {
    return this.http.patch<Usuario>(
      `${API_BASE_URL}/usuarios/${id}/restablecer-contrasena`,
      { contrasenaTemporal },
      this.cabeceras(),
    );
  }

  cambiarMiContrasena(request: CambiarContrasenaRequest): Observable<void> {
    return this.http.put<void>(`${API_BASE_URL}/usuarios/me/contrasena`, request, this.cabeceras());
  }
}
