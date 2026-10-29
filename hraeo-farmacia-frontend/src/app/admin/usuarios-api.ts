import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Rol, Session } from '../auth/session';
import { API_BASE_URL } from '../api-config';

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

export type AccionBitacora = 'ALTA' | 'EDICION' | 'DESACTIVACION' | 'REACTIVACION' | 'RESTABLECIMIENTO_CONTRASENA';

export interface RegistroBitacora {
  id: number;
  fecha: string;
  realizadoPor: string;
  usuarioId: number;
  nombreUsuario: string;
  nombreCompleto: string;
  accion: AccionBitacora;
  detalle: string | null;
}

export interface CambiarContrasenaRequest {
  contrasenaActual: string;
  contrasenaNueva: string;
}

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

  /** HU41: quién hizo qué con cada cuenta; fechas en formato AAAA-MM-DD. */
  bitacora(filtro: { usuarioId?: number; desde?: string; hasta?: string }): Observable<RegistroBitacora[]> {
    const params: Record<string, string> = {};
    if (filtro.usuarioId) params['usuarioId'] = String(filtro.usuarioId);
    if (filtro.desde) params['desde'] = filtro.desde;
    if (filtro.hasta) params['hasta'] = filtro.hasta;
    return this.http.get<RegistroBitacora[]>(`${API_BASE_URL}/usuarios/bitacora`, { ...this.cabeceras(), params });
  }

  cambiarMiContrasena(request: CambiarContrasenaRequest): Observable<void> {
    return this.http.put<void>(`${API_BASE_URL}/usuarios/me/contrasena`, request, this.cabeceras());
  }
}
