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
      { etiqueta: 'Entrega con receta', routerLink: '/farmacia/dispensacion/nueva', etiquetaExtra: 'HU28' },
      { etiqueta: 'Recetas parciales', routerLink: '/farmacia/dispensacion/recetas', etiquetaExtra: 'HU29' },
      { etiqueta: 'Devoluciones', routerLink: '/farmacia/dispensacion/devoluciones', etiquetaExtra: 'HU34' },
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
      { etiqueta: 'Solicitudes', routerLink: '/farmacia/solicitudes', etiquetaExtra: 'HU13' },
      { etiqueta: 'Recepción de despachos', routerLink: '/farmacia/inventario/recepcion', etiquetaExtra: 'HU26' },
    ],
  },
  {
    etiqueta: 'Inventario',
    routerLink: '/farmacia/inventario',
    hijos: [
      { etiqueta: 'Existencias', routerLink: '/farmacia/inventario/existencias', etiquetaExtra: 'HU27' },
      { etiqueta: 'Caducados y mermas', routerLink: '/farmacia/inventario/bajas', etiquetaExtra: 'HU35' },
      { etiqueta: 'Alta inicial', routerLink: '/farmacia/inventario/alta-inicial', etiquetaExtra: 'HU25' },
    ],
  },
  { etiqueta: 'Baja en Intelisis', routerLink: '/farmacia/baja-intelisis', etiquetaExtra: 'HU31 · HU32' },
  { etiqueta: 'Trazabilidad', routerLink: '/farmacia/trazabilidad', etiquetaExtra: 'HU33' },
  { etiqueta: 'Alto riesgo y red fría', routerLink: '/farmacia/seguridad' },
];
