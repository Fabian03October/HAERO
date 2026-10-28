import { ItemMenu } from '../shared/sidebar/sidebar';

// Menú de Farmacia compartido por todas sus pantallas (Iteración 3).
export const FARMACIA_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/farmacia' },
  {
    etiqueta: 'Dispensación',
    routerLink: '/farmacia/dispensacion',
    hijos: [
      { etiqueta: 'Nueva entrega', routerLink: '/farmacia/dispensacion/nueva', etiquetaExtra: 'HU28' },
      { etiqueta: 'Recetas parciales', routerLink: '/farmacia/dispensacion/recetas', etiquetaExtra: 'HU29' },
      { etiqueta: 'Devoluciones', routerLink: '/farmacia/dispensacion/devoluciones', etiquetaExtra: 'HU34' },
    ],
  },
  {
    etiqueta: 'Inventario',
    routerLink: '/farmacia/inventario',
    hijos: [
      { etiqueta: 'Existencias', routerLink: '/farmacia/inventario/existencias', etiquetaExtra: 'HU27' },
      { etiqueta: 'Recepción', routerLink: '/farmacia/inventario/recepcion', etiquetaExtra: 'HU26' },
      { etiqueta: 'Alta inicial', routerLink: '/farmacia/inventario/alta-inicial', etiquetaExtra: 'HU25' },
      { etiqueta: 'Caducados y mermas', routerLink: '/farmacia/inventario/bajas', etiquetaExtra: 'HU35' },
    ],
  },
  { etiqueta: 'Baja en Intelisis', routerLink: '/farmacia/baja-intelisis', etiquetaExtra: 'HU31 · HU32' },
  { etiqueta: 'Hospitalización', routerLink: '/farmacia/hospitalizacion' },
  { etiqueta: 'Trazabilidad', routerLink: '/farmacia/trazabilidad', etiquetaExtra: 'HU33' },
  { etiqueta: 'Solicitudes a Almacén', routerLink: '/farmacia/solicitudes', etiquetaExtra: 'HU13' },
];
