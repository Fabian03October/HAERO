import { ItemMenu } from '../shared/sidebar/sidebar';

// Fuente única de verdad del menú de Almacén. Todas las pantallas usan este
// arreglo para que la navegación sea idéntica en todas las pantallas. Recepción
// es un submenú: cada historia de usuario tiene su propia página.
export const ALMACEN_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/almacen', icono: 'inicio' },
  {
    etiqueta: 'Recepción',
    icono: 'recepcion',
    routerLink: '/almacen/recepcion',
    hijos: [
      { etiqueta: 'Catálogo de medicamentos', routerLink: '/almacen/recepcion/catalogo' },
      { etiqueta: 'Alto riesgo y red fría', routerLink: '/almacen/recepcion/seguridad' },
      { etiqueta: 'Inventario', routerLink: '/almacen/recepcion/inventario' },
      { etiqueta: 'Inventario inicial (conteo)', routerLink: '/almacen/recepcion/conteo-inicial' },
      { etiqueta: 'Registrar entrada', routerLink: '/almacen/recepcion/entrada' },
      { etiqueta: 'Pedidos', routerLink: '/almacen/recepcion/pedidos' },
      { etiqueta: 'Pedidos cancelados', routerLink: '/almacen/recepcion/cancelar-pedido' },
      { etiqueta: 'Intelisis: avisos y comparación', routerLink: '/almacen/recepcion/intelisis' },
    ],
  },
  {
    etiqueta: 'Despacho',
    icono: 'despacho',
    routerLink: '/almacen/despacho',
    hijos: [
      { etiqueta: 'Existencias', routerLink: '/almacen/despacho/existencias' },
      { etiqueta: 'Bandeja de despacho', routerLink: '/almacen/despacho/bandeja' },
      { etiqueta: 'Salidas registradas', routerLink: '/almacen/despacho/salidas' },
    ],
  },
  {
    etiqueta: 'Rotación y alertas',
    icono: 'alertas',
    routerLink: '/almacen/rotacion-alertas',
    hijos: [
      { etiqueta: 'Alertas de stock', routerLink: '/almacen/rotacion-alertas/alertas' },
      { etiqueta: 'CPM por clave', routerLink: '/almacen/rotacion-alertas/cpm' },
    ],
  },
  {
    etiqueta: 'Movimientos especiales',
    icono: 'movimientos',
    routerLink: '/almacen/movimientos-especiales',
    hijos: [
      { etiqueta: 'Canje de lote', routerLink: '/almacen/movimientos-especiales/canje' },
      { etiqueta: 'Caducados', routerLink: '/almacen/movimientos-especiales/caducados' },
      { etiqueta: 'Préstamos y transferencias', routerLink: '/almacen/movimientos-especiales/externos' },
    ],
  },
];

