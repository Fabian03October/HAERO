import { Injectable, signal } from '@angular/core';

// En pantallas angostas el menú lateral se oculta y se abre con el botón ☰ de la
// barra superior. Este estado lo comparten la barra superior y el menú.
@Injectable({ providedIn: 'root' })
export class MenuMovil {
  readonly abierto = signal(false);

  alternar(): void {
    this.abierto.update((valor) => !valor);
  }

  cerrar(): void {
    this.abierto.set(false);
  }
}
