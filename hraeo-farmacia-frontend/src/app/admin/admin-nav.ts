import { ItemMenu } from '../shared/sidebar/sidebar';

// Menú del Administrador compartido por todas sus pantallas.
export const ADMIN_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/admin', icono: 'inicio' },
  { etiqueta: 'Usuarios', routerLink: '/admin/usuarios', icono: 'usuarios' },
  { etiqueta: 'Bitácora', routerLink: '/admin/bitacora', icono: 'bitacora' },
];
