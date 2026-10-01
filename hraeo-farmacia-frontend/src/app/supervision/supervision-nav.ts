import { ItemMenu } from '../shared/sidebar/sidebar';

// Menú de Supervisión compartido por todas sus pantallas.
export const SUPERVISION_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/supervision' },
  { etiqueta: 'Alertas de stock', routerLink: '/supervision/alertas', etiquetaExtra: 'HU17' },
  { etiqueta: 'Consultas', etiquetaExtra: '(solo lectura)' },
  { etiqueta: 'Reportes', etiquetaExtra: '(solo lectura)' },
];
