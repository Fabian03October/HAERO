import { inject } from '@angular/core';
import { InventarioAlmacen } from './inventario-almacen';
import { PedidosAlmacen } from './pedidos-almacen';
import { SolicitudesAlmacen } from './solicitudes-almacen';
import { RotacionAlmacen } from './rotacion-almacen';
import { Session } from '../auth/session';

// shortcut: recarga a lo más una vez por minuto y por usuario; si se necesitan datos al
// segundo entre pestañas, cambiar a notificaciones del servidor.
const INTERVALO_MINIMO_MS = 60_000;
let ultimaRecarga = 0;
let ultimoUsuario = '';

/**
 * Vuelve a leer del backend inventario, pedidos, bandeja, catálogo y
 * rotación/alertas. Se llama en el constructor de cada sección de Almacén
 * (contexto de inyección) para que al entrar se vea lo que hay en la base y
 * no lo que quedó de una visita anterior — y, para rotación/alertas, no lo
 * que se intentó leer sin token antes de iniciar sesión.
 */
export function recargarAlmacen(): void {
  // Pasar de una sección a otra no vuelve a bajar ~2 MB: lo que se guarda en cada pantalla
  // ya recarga lo suyo al momento.
  const usuario = inject(Session).usuarioActual()?.nombreUsuario ?? '';
  if (usuario === ultimoUsuario && Date.now() - ultimaRecarga < INTERVALO_MINIMO_MS) return;
  ultimaRecarga = Date.now();
  ultimoUsuario = usuario;
  inject(InventarioAlmacen).recargar();
  inject(PedidosAlmacen).recargar();
  inject(SolicitudesAlmacen).recargar();
  inject(RotacionAlmacen).recargar();
}
