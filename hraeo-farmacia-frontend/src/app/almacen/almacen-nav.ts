import { ItemMenu } from '../shared/sidebar/sidebar';

// Fuente única de verdad del menú de Almacén. Todas las pantallas usan este
// arreglo para que la navegación sea idéntica en todas las pantallas. Recepción
// es un submenú: cada historia de usuario tiene su propia página.
export const ALMACEN_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/almacen' },
  {
    etiqueta: 'Recepción',
    routerLink: '/almacen/recepcion',
    hijos: [
      { etiqueta: 'Catálogo de medicamentos', routerLink: '/almacen/recepcion/catalogo' },
      { etiqueta: 'Inventario', routerLink: '/almacen/recepcion/inventario', etiquetaExtra: 'HU21' },
      { etiqueta: 'Registrar entrada', routerLink: '/almacen/recepcion/entrada', etiquetaExtra: 'HU10' },
      { etiqueta: 'Pedidos', routerLink: '/almacen/recepcion/pedidos', etiquetaExtra: 'HU22' },
      { etiqueta: 'Cancelar pedido', routerLink: '/almacen/recepcion/cancelar-pedido', etiquetaExtra: 'HU11' },
    ],
  },
  {
    etiqueta: 'Despacho',
    routerLink: '/almacen/despacho',
    hijos: [
      { etiqueta: 'Existencias', routerLink: '/almacen/despacho/existencias', etiquetaExtra: 'HU12' },
      { etiqueta: 'Bandeja de despacho', routerLink: '/almacen/despacho/bandeja', etiquetaExtra: 'HU14-HU15' },
      { etiqueta: 'Salidas registradas', routerLink: '/almacen/despacho/salidas', etiquetaExtra: 'HU14' },
    ],
  },
  {
    etiqueta: 'Rotación y alertas',
    routerLink: '/almacen/rotacion-alertas',
    hijos: [
      { etiqueta: 'Alertas de stock', routerLink: '/almacen/rotacion-alertas/alertas', etiquetaExtra: 'HU17' },
      { etiqueta: 'CPM por clave', routerLink: '/almacen/rotacion-alertas/cpm', etiquetaExtra: 'HU16' },
    ],
  },
  {
    etiqueta: 'Movimientos especiales',
    routerLink: '/almacen/movimientos-especiales',
    hijos: [
      { etiqueta: 'Canje de lote', routerLink: '/almacen/movimientos-especiales/canje', etiquetaExtra: 'HU18' },
      { etiqueta: 'Caducados', routerLink: '/almacen/movimientos-especiales/caducados', etiquetaExtra: 'HU19' },
      { etiqueta: 'Préstamos y transferencias', routerLink: '/almacen/movimientos-especiales/externos', etiquetaExtra: 'HU20' },
    ],
  },
];

