import { ItemMenu } from '../shared/sidebar/sidebar';

// Menú de Supervisión compartido por todas sus pantallas. Todo es de solo lectura:
// Supervisión consulta, no registra movimientos.
export const SUPERVISION_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/supervision' },
  { etiqueta: 'Alertas de stock', routerLink: '/supervision/alertas', etiquetaExtra: 'HU17' },
  { etiqueta: 'CPM por clave', routerLink: '/supervision/cpm', etiquetaExtra: 'HU16' },
  {
    etiqueta: 'Consultas',
    routerLink: '/supervision/consultas',
    hijos: [
      { etiqueta: 'Existencias', routerLink: '/supervision/consultas/existencias', etiquetaExtra: 'HU12' },
      { etiqueta: 'Salidas registradas', routerLink: '/supervision/consultas/salidas', etiquetaExtra: 'HU14' },
      { etiqueta: 'Préstamos y transferencias', routerLink: '/supervision/consultas/prestamos', etiquetaExtra: 'HU20' },
    ],
  },
  {
    etiqueta: 'Reportes',
    routerLink: '/supervision/reportes',
    hijos: [
      { etiqueta: 'Mermas por caducidad', routerLink: '/supervision/reportes/mermas', etiquetaExtra: 'HU19' },
      { etiqueta: 'Trazabilidad de canjes', routerLink: '/supervision/reportes/canjes', etiquetaExtra: 'HU18' },
      { etiqueta: 'Otras instituciones', routerLink: '/supervision/reportes/instituciones', etiquetaExtra: 'HU20' },
    ],
  },
];
