import { Routes } from '@angular/router';
import { PanelAlmacen } from './panel-almacen/panel-almacen';
import { RecepcionAlmacen } from './recepcion-almacen/recepcion-almacen';
import { RegistrarEntrada } from './recepcion-almacen/registrar-entrada/registrar-entrada';
import { PedidosRecepcion } from './recepcion-almacen/pedidos/pedidos-recepcion';
import { CancelarPedido } from './recepcion-almacen/cancelar-pedido/cancelar-pedido';
import { InventarioInicial } from './recepcion-almacen/inventario-inicial';
import { CatalogoMedicamentos } from './recepcion-almacen/catalogo/catalogo-medicamentos';
import { Existencias } from './existencias/existencias';
import { Despacho } from './despacho/despacho';
import { BandejaDespacho } from './despacho/bandeja-despacho/bandeja-despacho';
import { SalidasRegistradas } from './despacho/salidas-registradas/salidas-registradas';
import { RotacionAlertas } from './rotacion-alertas/rotacion-alertas';
import { CpmClave } from './rotacion-alertas/cpm/cpm-clave';
import { AlertasStock } from './rotacion-alertas/alertas/alertas-stock';
import { MovimientosEspeciales } from './movimientos-especiales/movimientos-especiales';
import { CanjeLote } from './movimientos-especiales/canje/canje-lote';
import { Caducados } from './movimientos-especiales/caducados/caducados';
import { MovimientosExternos } from './movimientos-especiales/externos/movimientos-externos';
import { roleGuard } from '../auth/role-guard';

export const ALMACEN_ROUTES: Routes = [
  { path: '', component: PanelAlmacen, canActivate: [roleGuard], data: { roles: ['ALMACEN'] } },
  // Recepción: cada historia de usuario es una página propia dentro del submenú.
  {
    path: 'recepcion',
    component: RecepcionAlmacen,
    canActivate: [roleGuard],
    data: { roles: ['ALMACEN'] },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inventario' },
      { path: 'entrada', component: RegistrarEntrada },
      { path: 'pedidos', component: PedidosRecepcion },
      { path: 'cancelar-pedido', component: CancelarPedido },
      { path: 'inventario', component: InventarioInicial },
      { path: 'catalogo', component: CatalogoMedicamentos },
      // Ruta anterior de la carga de inventario.
      { path: 'inventario-inicial', redirectTo: 'inventario' },
    ],
  },
  // Despacho: igual que Recepción, cada historia es una página del submenú (HU12, HU14, HU15).
  // La solicitud (HU13) la captura Farmacia en /farmacia/solicitudes y llega a la bandeja.
  {
    path: 'despacho',
    component: Despacho,
    canActivate: [roleGuard],
    data: { roles: ['ALMACEN'] },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'bandeja' },
      { path: 'existencias', component: Existencias },
      { path: 'bandeja', component: BandejaDespacho },
      { path: 'salidas', component: SalidasRegistradas },
    ],
  },
  // Ruta anterior de Existencias.
  { path: 'existencias', redirectTo: 'despacho/existencias' },
  // Rotación y alertas: CPM (HU16) y alertas de stock (HU17), cada una en su página.
  {
    path: 'rotacion-alertas',
    component: RotacionAlertas,
    canActivate: [roleGuard],
    data: { roles: ['ALMACEN'] },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'alertas' },
      { path: 'cpm', component: CpmClave },
      { path: 'alertas', component: AlertasStock },
    ],
  },
  // Movimientos especiales: canje (HU18), caducados (HU19) y préstamos/transferencias (HU20).
  {
    path: 'movimientos-especiales',
    component: MovimientosEspeciales,
    canActivate: [roleGuard],
    data: { roles: ['ALMACEN'] },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'canje' },
      { path: 'canje', component: CanjeLote },
      { path: 'caducados', component: Caducados },
      { path: 'externos', component: MovimientosExternos },
    ],
  },
];
