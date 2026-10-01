import { ItemMenu } from '../shared/sidebar/sidebar';

// Menú de Farmacia compartido por todas sus pantallas.
export const FARMACIA_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/farmacia' },
  { etiqueta: 'Solicitudes a Almacén', routerLink: '/farmacia/solicitudes', etiquetaExtra: 'HU13' },
  { etiqueta: 'Dispensación' },
  { etiqueta: 'Recetas' },
];
