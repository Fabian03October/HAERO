import { Component, ElementRef, ViewEncapsulation, inject, signal } from '@angular/core';

/**
 * Envuelve un <input type="password"> y le agrega el botón de ojo para mostrar u
 * ocultar lo que se escribe. El input se pasa como contenido, así cada pantalla
 * conserva su referencia (#nueva, #confirmar...) y sigue leyendo .value igual.
 */
@Component({
  selector: 'app-campo-contrasena',
  templateUrl: './campo-contrasena.html',
  styleUrl: './campo-contrasena.css',
  // Sin encapsulación: el input proyectado lleva los atributos de la pantalla que lo
  // usa, no los de este componente, y sus estilos no lo alcanzarían.
  encapsulation: ViewEncapsulation.None,
  host: { class: 'campo-contrasena' },
})
export class CampoContrasena {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly visible = signal(false);

  alternar(): void {
    const input = this.host.nativeElement.querySelector('input');
    if (!input) return;
    this.visible.set(!this.visible());
    input.type = this.visible() ? 'text' : 'password';
  }
}
