import { WritableSignal } from '@angular/core';

/**
 * Mantiene una lista igual en todas las pestañas abiertas. Cuando otra pestaña
 * guarda en localStorage, esta recibe el evento "storage" y actualiza su copia;
 * así no se queda con datos viejos ni los vuelve a guardar encima de los nuevos.
 */
export function sincronizarEntrePestanas<T>(clave: string, lista: WritableSignal<T[]>): void {
  if (typeof window === 'undefined') return;
  window.addEventListener('storage', (evento) => {
    if (evento.key !== clave || !evento.newValue) return;
    try {
      const datos = JSON.parse(evento.newValue);
      if (Array.isArray(datos)) lista.set(datos);
    } catch {
      // Si el dato llega dañado se conserva la copia actual.
    }
  });
}
