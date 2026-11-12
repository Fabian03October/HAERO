import { ItemMenu } from '../shared/sidebar/sidebar';

// Menú de Farmacia compartido por todas sus pantallas (Iteración 3).
// Orden: lo que se usa a diario con pacientes primero; abasto e inventario después;
// el cierre del día, las consultas y la configuración al final.
export const FARMACIA_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/farmacia' },
  {
    // Ventanilla = pacientes con receta; Hospitalización, Quimioterapia y Quirófano entregan con su propio flujo.
    etiqueta: 'Ventanilla',
    routerLink: '/farmacia/dispensacion',
    hijos: [
      { etiqueta: 'Entrega con receta', routerLink: '/farmacia/dispensacion/nueva' },
      { etiqueta: 'Recetas parciales', routerLink: '/farmacia/dispensacion/recetas' },
      { etiqueta: 'Devoluciones', routerLink: '/farmacia/dispensacion/devoluciones' },
    ],
  },
  { etiqueta: 'Hospitalización', routerLink: '/farmacia/hospitalizacion' },
  { etiqueta: 'Quimioterapia', routerLink: '/farmacia/quimioterapia' },
  {
    etiqueta: 'Quirófano',
    routerLink: '/farmacia/quirofano',
    hijos: [
      { etiqueta: 'Cajas de anestesia', routerLink: '/farmacia/quirofano/cajas' },
      { etiqueta: 'Catálogo de cajas', routerLink: '/farmacia/quirofano/catalogo' },
    ],
  },
  // Pedir y recibir son los dos pasos del mismo trámite con Almacén; por eso van juntos
  // (sus rutas no comparten prefijo: el grupo se abre por la ruta de sus hijos).
  {
    etiqueta: 'Pedidos a Almacén',
    hijos: [
      { etiqueta: 'Solicitudes', routerLink: '/farmacia/solicitudes' },
      { etiqueta: 'Recepción de despachos', routerLink: '/farmacia/inventario/recepcion' },
    ],
  },
  {
    etiqueta: 'Inventario',
    routerLink: '/farmacia/inventario',
    hijos: [
      { etiqueta: 'Existencias', routerLink: '/farmacia/inventario/existencias' },
      { etiqueta: 'Caducados y mermas', routerLink: '/farmacia/inventario/bajas' },
      { etiqueta: 'Alta inicial', routerLink: '/farmacia/inventario/alta-inicial' },
    ],
  },
  { etiqueta: 'Baja en Intelisis', routerLink: '/farmacia/baja-intelisis' },
  { etiqueta: 'Trazabilidad', routerLink: '/farmacia/trazabilidad' },
  { etiqueta: 'Alto riesgo y red fría', routerLink: '/farmacia/seguridad' },
];
