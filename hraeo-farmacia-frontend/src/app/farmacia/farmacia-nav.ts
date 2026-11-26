import { ItemMenu } from '../shared/sidebar/sidebar';
import { inventarioInicialCerrado } from './inventario/alta-inicial/inventario-inicial-estado';

// Menú de Farmacia compartido por todas sus pantallas (Iteración 3).
// Cinco grupos para que no se amontone: ventanilla, atención en piso, abasto e inventario,
// y consultas y cierre. Alto riesgo y red fría se configura en Almacén (Farmacia solo lo ve al surtir).
// Los grupos cuyas rutas no comparten prefijo se abren por la ruta de sus hijos.
export const FARMACIA_MENU: ItemMenu[] = [
  { etiqueta: 'Inicio', routerLink: '/farmacia', icono: 'inicio' },
  {
    // Ventanilla = pacientes con receta.
    etiqueta: 'Ventanilla',
    routerLink: '/farmacia/dispensacion',
    icono: 'ventanilla',
    hijos: [
      { etiqueta: 'Entrega con receta', routerLink: '/farmacia/dispensacion/nueva' },
      { etiqueta: 'Recetas parciales', routerLink: '/farmacia/dispensacion/recetas' },
      { etiqueta: 'Devoluciones', routerLink: '/farmacia/dispensacion/devoluciones' },
    ],
  },
  {
    // Hospitalización, Quimioterapia y Quirófano entregan con su propio flujo.
    etiqueta: 'Atención en piso',
    icono: 'piso',
    hijos: [
      { etiqueta: 'Hospitalización', routerLink: '/farmacia/hospitalizacion' },
      { etiqueta: 'Quimioterapia', routerLink: '/farmacia/quimioterapia' },
      { etiqueta: 'Quirófano: cajas', routerLink: '/farmacia/quirofano/cajas' },
      { etiqueta: 'Quirófano: catálogo', routerLink: '/farmacia/quirofano/catalogo' },
    ],
  },
  {
    // Pedir y recibir son los dos pasos del mismo trámite con Almacén; luego el inventario propio.
    etiqueta: 'Abasto e inventario',
    icono: 'abasto',
    hijos: [
      { etiqueta: 'Solicitudes a Almacén', routerLink: '/farmacia/solicitudes' },
      { etiqueta: 'Recepción de despachos', routerLink: '/farmacia/inventario/recepcion' },
      { etiqueta: 'Existencias', routerLink: '/farmacia/inventario/existencias' },
      { etiqueta: 'Caducados y mermas', routerLink: '/farmacia/inventario/bajas' },
      // Se hace una sola vez: después de guardarlo desaparece del menú.
      { etiqueta: 'Inventario inicial', routerLink: '/farmacia/inventario/alta-inicial', oculto: inventarioInicialCerrado },
    ],
  },
  {
    etiqueta: 'Consultas y cierre',
    icono: 'consultas',
    hijos: [
      { etiqueta: 'Baja en Intelisis', routerLink: '/farmacia/baja-intelisis' },
      { etiqueta: 'Trazabilidad', routerLink: '/farmacia/trazabilidad' },
    ],
  },
];
