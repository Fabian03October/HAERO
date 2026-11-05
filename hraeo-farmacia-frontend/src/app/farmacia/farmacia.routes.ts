import { Routes } from '@angular/router';
import { PanelFarmacia } from './panel-farmacia/panel-farmacia';
import { SolicitudesFarmacia } from './solicitudes/solicitudes-farmacia';
import { NuevaEntrega } from './dispensacion/nueva-entrega/nueva-entrega';
import { RecetasParciales } from './dispensacion/recetas-parciales/recetas-parciales';
import { Devoluciones } from './dispensacion/devoluciones/devoluciones';
import { ExistenciasFarmacia } from './inventario/existencias/existencias-farmacia';
import { RecepcionFarmacia } from './inventario/recepcion/recepcion-farmacia';
import { AltaInicialFarmacia } from './inventario/alta-inicial/alta-inicial-farmacia';
import { BajasFarmacia } from './inventario/bajas/bajas-farmacia';
import { BajaIntelisis } from './baja-intelisis/baja-intelisis';
import { Trazabilidad } from './trazabilidad/trazabilidad';
import { Hospitalizacion } from '../hospitalizacion/hospitalizacion';
import { ImprimirIndicaciones } from '../hospitalizacion/imprimir/imprimir-indicaciones';
import { ImprimirBajas } from '../hospitalizacion/imprimir/imprimir-bajas';
import { SeguridadMedicamentosFarmacia } from './seguridad/seguridad-medicamentos-farmacia';
import { OperacionCaja } from './cajas-quirofano/operacion-caja/operacion-caja';
import { CatalogoCajas } from './cajas-quirofano/catalogo-cajas/catalogo-cajas';
import { Quimioterapia } from './quimioterapia/quimioterapia';
import { ImprimirVale } from './cajas-quirofano/imprimir-vale/imprimir-vale';
import { roleGuard } from '../auth/role-guard';

const SOLO_FARMACIA = { canActivate: [roleGuard], data: { roles: ['FARMACIA'] } };

export const FARMACIA_ROUTES: Routes = [
  { path: '', component: PanelFarmacia, ...SOLO_FARMACIA },
  { path: 'solicitudes', component: SolicitudesFarmacia, ...SOLO_FARMACIA },
  // Iteración 3
  { path: 'dispensacion/nueva', component: NuevaEntrega, ...SOLO_FARMACIA },
  { path: 'dispensacion/recetas', component: RecetasParciales, ...SOLO_FARMACIA },
  { path: 'dispensacion/devoluciones', component: Devoluciones, ...SOLO_FARMACIA },
  { path: 'inventario/existencias', component: ExistenciasFarmacia, ...SOLO_FARMACIA },
  { path: 'inventario/recepcion', component: RecepcionFarmacia, ...SOLO_FARMACIA },
  { path: 'inventario/alta-inicial', component: AltaInicialFarmacia, ...SOLO_FARMACIA },
  { path: 'inventario/bajas', component: BajasFarmacia, ...SOLO_FARMACIA },
  { path: 'baja-intelisis', component: BajaIntelisis, ...SOLO_FARMACIA },
  { path: 'trazabilidad', component: Trazabilidad, ...SOLO_FARMACIA },
  { path: 'seguridad', component: SeguridadMedicamentosFarmacia, ...SOLO_FARMACIA },
  { path: 'hospitalizacion', component: Hospitalizacion, ...SOLO_FARMACIA },
  { path: 'quirofano/cajas', component: OperacionCaja, ...SOLO_FARMACIA },
  { path: 'quirofano/catalogo', component: CatalogoCajas, ...SOLO_FARMACIA },
  { path: 'quimioterapia', component: Quimioterapia, ...SOLO_FARMACIA },
  // Vale de quirófano para imprimir (se abre en otra pestaña).
  { path: 'quirofano/vale', component: ImprimirVale, ...SOLO_FARMACIA },
  // Hojas del carrito para imprimir (se abren en otra pestaña).
  { path: 'hospitalizacion/imprimir/indicaciones', component: ImprimirIndicaciones, ...SOLO_FARMACIA },
  { path: 'hospitalizacion/imprimir/bajas', component: ImprimirBajas, ...SOLO_FARMACIA },
];
