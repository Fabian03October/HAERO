import { Injectable, signal } from '@angular/core';

const CLAVE_OCULTO = 'hraeo.menu.oculto';
// Mismo corte que el @media de sidebar.css y topbar.css.
const CONSULTA_ANGOSTA = '(max-width: 900px)';

// Estado del menú lateral, compartido por la barra superior (botón ☰) y el menú.
// - Pantallas angostas: el menú está oculto y ☰ lo abre encima de la página.
// - Computadora: el menú está visible y ☰ lo desliza a la izquierda para dejar
//   la página a todo lo ancho; la preferencia se recuerda en este navegador.
@Injectable({ providedIn: 'root' })
export class MenuMovil {
  readonly abierto = signal(false);
  readonly oculto = signal(leerOculto());

  alternar(): void {
    if (esPantallaAngosta()) {
      this.abierto.update((valor) => !valor);
      return;
    }
    const oculto = !this.oculto();
    this.oculto.set(oculto);
    try {
      localStorage.setItem(CLAVE_OCULTO, String(oculto));
    } catch {
      // Sin localStorage la preferencia dura solo mientras la pestaña esté abierta.
    }
  }

  /** Para el aria-expanded del botón ☰: si el menú se ve en el tamaño de pantalla actual. */
  visible(): boolean {
    return esPantallaAngosta() ? this.abierto() : !this.oculto();
  }

  cerrar(): void {
    this.abierto.set(false);
  }
}

function esPantallaAngosta(): boolean {
  return typeof matchMedia === 'function' && matchMedia(CONSULTA_ANGOSTA).matches;
}

function leerOculto(): boolean {
  try {
    return localStorage.getItem(CLAVE_OCULTO) === 'true';
  } catch {
    return false;
  }
}
