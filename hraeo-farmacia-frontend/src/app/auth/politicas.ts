import { Injectable, signal } from '@angular/core';
import { Rol, UsuarioSesion } from './session';

// Cambiar la versión obliga a todos los usuarios a aceptar de nuevo al entrar.
export const VERSION_POLITICAS = '1.0';
export const FECHA_POLITICAS = '29/09/2026';

export interface AceptacionPoliticas {
  nombreUsuario: string;
  nombreCompleto: string;
  rol: Rol;
  version: string;
  fecha: string;
}

const CLAVE_REGISTRO = 'hraeo.politicas.aceptaciones';

/**
 * Aceptación de las políticas de uso y privacidad.
 *
 * Por ahora se guarda en localStorage (solo frontend): vale para este navegador y
 * se pierde si se borran sus datos. Para pasarlo al backend basta con cambiar este
 * servicio: `debeAceptar` leería un campo que venga en el login y `aceptar` haría
 * la petición al servidor. Las pantallas y el guard no cambian.
 */
@Injectable({ providedIn: 'root' })
export class PoliticasUso {
  // Historial de aceptaciones en este navegador: quién, qué versión y cuándo.
  private readonly _registro = signal<AceptacionPoliticas[]>(this.cargar());
  readonly registro = this._registro.asReadonly();

  debeAceptar(usuario: UsuarioSesion): boolean {
    return !this.aceptacionVigente(usuario.nombreUsuario);
  }

  aceptacionVigente(nombreUsuario: string): AceptacionPoliticas | undefined {
    return this._registro().find((item) => item.nombreUsuario === nombreUsuario && item.version === VERSION_POLITICAS);
  }

  aceptar(usuario: UsuarioSesion): AceptacionPoliticas {
    const aceptacion: AceptacionPoliticas = {
      nombreUsuario: usuario.nombreUsuario,
      nombreCompleto: usuario.nombreCompleto,
      rol: usuario.rol,
      version: VERSION_POLITICAS,
      fecha: fechaLocal(),
    };
    const registro = [aceptacion, ...this._registro()];
    this._registro.set(registro);
    try {
      localStorage.setItem(CLAVE_REGISTRO, JSON.stringify(registro));
    } catch {
      // Sin localStorage la aceptación solo dura mientras la pestaña esté abierta.
    }
    return aceptacion;
  }

  private cargar(): AceptacionPoliticas[] {
    try {
      const guardado = localStorage.getItem(CLAVE_REGISTRO);
      const datos = guardado ? JSON.parse(guardado) : null;
      if (Array.isArray(datos)) return datos;
    } catch {
      // Dato dañado o localStorage bloqueado: se pedirá aceptar otra vez.
    }
    return [];
  }
}

// Fecha y hora local sin zona (AAAA-MM-DDTHH:mm:ss).
function fechaLocal(): string {
  const fecha = new Date();
  return new Date(fecha.getTime() - fecha.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}
