import { Injectable, signal } from '@angular/core';

export interface Aviso {
  tipo: 'exito' | 'error';
  titulo: string;
  mensaje: string;
}

export interface OpcionesConfirmacion {
  titulo: string;
  mensaje: string;
  textoAceptar?: string;
  textoCancelar?: string;
}

interface Confirmacion extends OpcionesConfirmacion {
  resolver: (aceptado: boolean) => void;
}

// Un aviso de éxito se cierra solo; uno de error espera a que el usuario lo cierre.
const DURACION_EXITO_MS = 3500;

/**
 * Popups globales: avisos de éxito (con palomita) o error, y confirmaciones que
 * reemplazan al confirm()/alert() del navegador. Se dibujan en <app-notificaciones>,
 * montado una sola vez en app.html.
 */
@Injectable({ providedIn: 'root' })
export class Notificaciones {
  private readonly _aviso = signal<Aviso | null>(null);
  readonly aviso = this._aviso.asReadonly();

  private readonly _confirmacion = signal<Confirmacion | null>(null);
  readonly confirmacion = this._confirmacion.asReadonly();

  private temporizador?: ReturnType<typeof setTimeout>;

  exito(titulo: string, mensaje = ''): void {
    this.mostrar({ tipo: 'exito', titulo, mensaje });
    this.temporizador = setTimeout(() => this.cerrarAviso(), DURACION_EXITO_MS);
  }

  error(mensaje: string, titulo = 'No se pudo completar'): void {
    this.mostrar({ tipo: 'error', titulo, mensaje });
  }

  cerrarAviso(): void {
    clearTimeout(this.temporizador);
    this._aviso.set(null);
  }

  /** Resuelve true si el usuario acepta; false si cancela, presiona Esc o hace clic fuera. */
  confirmar(opciones: OpcionesConfirmacion): Promise<boolean> {
    // Si hubiera una confirmación abierta, se da por cancelada.
    this._confirmacion()?.resolver(false);
    return new Promise((resolver) => this._confirmacion.set({ ...opciones, resolver }));
  }

  responder(aceptado: boolean): void {
    const actual = this._confirmacion();
    if (!actual) return;
    this._confirmacion.set(null);
    actual.resolver(aceptado);
  }

  private mostrar(aviso: Aviso): void {
    clearTimeout(this.temporizador);
    this._aviso.set(aviso);
  }
}
