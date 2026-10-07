import { Injectable, inject } from '@angular/core';
import { Session, UsuarioSesion } from './session';

// Cambiar la versión obliga a todos los usuarios a aceptar de nuevo al entrar.
export const VERSION_POLITICAS = '1.0';
export const FECHA_POLITICAS = '29/09/2026';

export interface AceptacionPoliticas {
  version: string;
  fecha: string;
}

/**
 * Aceptación de las políticas de uso y privacidad, guardada por usuario en el
 * servidor (antes solo en el navegador). El login trae la versión que ya aceptó;
 * si no coincide con VERSION_POLITICAS, el guard lo manda a aceptarlas.
 */
@Injectable({ providedIn: 'root' })
export class PoliticasUso {
  private readonly session = inject(Session);

  debeAceptar(usuario: UsuarioSesion): boolean {
    return usuario.versionPoliticasAceptada !== VERSION_POLITICAS;
  }

  aceptacionVigente(usuario: UsuarioSesion): AceptacionPoliticas | undefined {
    if (usuario.versionPoliticasAceptada !== VERSION_POLITICAS || !usuario.fechaAceptacionPoliticas) return undefined;
    return { version: usuario.versionPoliticasAceptada, fecha: usuario.fechaAceptacionPoliticas };
  }

  /** Guarda la aceptación en el servidor; devuelve el error para mostrarlo, o null. */
  async aceptar(): Promise<string | null> {
    try {
      await this.session.aceptarPoliticas(VERSION_POLITICAS);
      return null;
    } catch (error) {
      return (error as { error?: { mensaje?: string } })?.error?.mensaje ?? 'No se pudo registrar la aceptación. Intenta de nuevo.';
    }
  }
}
