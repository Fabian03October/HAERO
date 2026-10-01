import { inject } from '@angular/core';
import { InventarioAlmacen } from './inventario-almacen';
import { PedidosAlmacen } from './pedidos-almacen';
import { SolicitudesAlmacen } from './solicitudes-almacen';

/**
 * Vuelve a leer del backend inventario, pedidos, bandeja y catálogo. Se llama en el
 * constructor de cada sección de Almacén (contexto de inyección) para que al
 * entrar se vea lo que hay en la base y no lo que quedó de una visita anterior.
 */
export function recargarAlmacen(): void {
  inject(InventarioAlmacen).recargar();
  inject(PedidosAlmacen).recargar();
  inject(SolicitudesAlmacen).recargar();
}
