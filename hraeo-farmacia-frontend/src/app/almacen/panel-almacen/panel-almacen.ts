import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { ALMACEN_MENU } from '../almacen-nav';
import { recargarAlmacen } from '../recargar-almacen';
import { InventarioAlmacen } from '../inventario-almacen';
import { PedidosAlmacen } from '../pedidos-almacen';
import { SolicitudesAlmacen } from '../solicitudes-almacen';

// Meses hacia adelante para considerar un lote "próximo a caducar".
const MESES_ALERTA_CADUCIDAD = 9;
// Renglones que se muestran en la tabla "Próximos a salir" del Inicio.
const LOTES_EN_INICIO = 6;
// Pedidos que se muestran en "Pedidos por recibir"; el resto se ve en Recepción.
const PEDIDOS_EN_INICIO = 4;

@Component({
  selector: 'app-panel-almacen',
  imports: [RouterLink, Topbar, Sidebar],
  templateUrl: './panel-almacen.html',
  styleUrl: './panel-almacen.css',
})
export class PanelAlmacen {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  protected readonly inventario = inject(InventarioAlmacen);
  protected readonly pedidos = inject(PedidosAlmacen);
  private readonly solicitudes = inject(SolicitudesAlmacen);

  readonly items = ALMACEN_MENU;

  constructor() {
    recargarAlmacen();
  }

  // Solo se capitaliza la primera letra ("Lunes, 28 de septiembre de 2026").
  readonly fechaHoy = (() => {
    const fecha = new Date().toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    return fecha.charAt(0).toUpperCase() + fecha.slice(1);
  })();

  readonly saludo = (() => {
    const hora = new Date().getHours();
    if (hora < 12) return 'Buenos días';
    if (hora < 19) return 'Buenas tardes';
    return 'Buenas noches';
  })();

  readonly primerNombre = computed(() => (this.session.usuarioActual()?.nombreCompleto ?? '').split(' ')[0]);

  // Lotes ordenados por caducidad: el primero de cada clave es el que debe salir antes (FEFO).
  readonly lotesOrdenados = computed(() => {
    const ordenados = [...this.inventario.lotes()].sort((a, b) => a.caducidad.localeCompare(b.caducidad));
    const vistos = new Set<string>();
    return ordenados.map((lote) => {
      const fefo = !vistos.has(lote.clave);
      vistos.add(lote.clave);
      return { ...lote, fefo, porCaducar: this.caducaPronto(lote.caducidad) };
    });
  });

  readonly proximosASalir = computed(() => this.lotesOrdenados().slice(0, LOTES_EN_INICIO));
  readonly pedidosEnInicio = computed(() => this.pedidos.pendientes().slice(0, PEDIDOS_EN_INICIO));
  readonly pedidosRestantes = computed(() => this.pedidos.pendientes().length - this.pedidosEnInicio().length);
  readonly lotesPorCaducar = computed(() => this.lotesOrdenados().filter((lote) => lote.porCaducar).length);

  readonly indicadores = computed(() => [
    {
      titulo: 'Pedidos por recibir',
      valor: this.pedidos.pendientes().length,
      detalle: `${this.cajasPendientes()} cajas pendientes de entrada`,
      ruta: '/almacen/recepcion/entrada',
      tono: 'info',
      ejemplo: false,
    },
    {
      titulo: 'Cajas en inventario',
      valor: this.inventario.totalCajas(),
      detalle: `${this.inventario.lotes().length} lotes · ${this.inventario.totalClaves()} claves`,
      ruta: '/almacen/recepcion/inventario',
      tono: 'success',
      ejemplo: false,
    },
    {
      titulo: 'Lotes por caducar',
      valor: this.lotesPorCaducar(),
      detalle: `En los próximos ${MESES_ALERTA_CADUCIDAD} meses`,
      ruta: '/almacen/despacho/existencias',
      tono: 'warning',
      ejemplo: false,
    },
    {
      titulo: 'Solicitudes de Farmacia',
      valor: this.solicitudes.porAtender().length,
      detalle: 'Esperan despacho FEFO',
      ruta: '/almacen/despacho/bandeja',
      tono: 'danger',
      ejemplo: false,
    },
  ]);



  avance(recibidas: number, esperadas: number): number {
    return esperadas ? Math.min(100, Math.round((recibidas / esperadas) * 100)) : 0;
  }

  private cajasPendientes(): number {
    return this.pedidos.pendientes().reduce((total, pedido) => total + pedido.cajasEsperadas - pedido.cajasRecibidas, 0);
  }

  private caducaPronto(caducidad: string): boolean {
    const [anio, mes] = caducidad.split('-').map(Number);
    if (!anio || !mes) return false;
    const hoy = new Date();
    const mesesRestantes = (anio - hoy.getFullYear()) * 12 + (mes - (hoy.getMonth() + 1));
    return mesesRestantes <= MESES_ALERTA_CADUCIDAD;
  }
}
