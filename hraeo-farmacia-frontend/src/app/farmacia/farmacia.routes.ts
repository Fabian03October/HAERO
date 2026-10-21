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
];
