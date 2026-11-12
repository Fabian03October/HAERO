import { ItemMenu } from '../shared/sidebar/sidebar';

// Menú de Supervisión compartido por todas sus pantallas. Todo es de solo lectura:
// Supervisión consulta, no registra movimientos.
export const SUPERVISION_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/supervision' },
  { etiqueta: 'Alertas de stock', routerLink: '/supervision/alertas' },
  { etiqueta: 'CPM por clave', routerLink: '/supervision/cpm' },
  {
    etiqueta: 'Consultas',
    routerLink: '/supervision/consultas',
    hijos: [
      { etiqueta: 'Existencias', routerLink: '/supervision/consultas/existencias' },
      { etiqueta: 'Salidas registradas', routerLink: '/supervision/consultas/salidas' },
      { etiqueta: 'Préstamos y transferencias', routerLink: '/supervision/consultas/prestamos' },
      { etiqueta: 'HAERO contra Intelisis', routerLink: '/supervision/consultas/intelisis' },
    ],
  },
  {
    etiqueta: 'Reportes',
    routerLink: '/supervision/reportes',
    hijos: [
      { etiqueta: 'Mermas por caducidad', routerLink: '/supervision/reportes/mermas' },
      { etiqueta: 'Trazabilidad de canjes', routerLink: '/supervision/reportes/canjes' },
      { etiqueta: 'Otras instituciones', routerLink: '/supervision/reportes/instituciones' },
    ],
  },
];
