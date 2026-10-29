import { ItemMenu } from '../shared/sidebar/sidebar';

// Menú del Administrador compartido por todas sus pantallas.
export const ADMIN_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/admin' },
  { etiqueta: 'Usuarios', routerLink: '/admin/usuarios' },
  { etiqueta: 'Bitácora', routerLink: '/admin/bitacora', etiquetaExtra: 'HU41' },
];
