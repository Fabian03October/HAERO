import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { MESES_STOCK_MAXIMO, MESES_STOCK_MINIMO, RotacionAlmacen } from '../../almacen/rotacion-almacen';
import { SUPERVISION_MENU } from '../supervision-nav';

// Alertas que se muestran en el Inicio; el resto en la página de alertas.
const ALERTAS_EN_INICIO = 6;

// Supervisión · Inicio. Resumen en solo lectura de las alertas de stock (HU17, base
// del tablero de Supervisión): qué claves están en riesgo y desde cuándo (CA5).
@Component({
  selector: 'app-panel-supervision',
  imports: [RouterLink, Topbar, Sidebar],
  templateUrl: './panel-supervision.html',
  styleUrls: ['../../almacen/panel-almacen/panel-almacen.css', './panel-supervision.css'],
})
export class PanelSupervision {
  protected readonly session = inject(Session);
  protected readonly rotacion = inject(RotacionAlmacen);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  protected readonly mesesMinimo = MESES_STOCK_MINIMO;
  protected readonly mesesMaximo = MESES_STOCK_MAXIMO;
  readonly items = SUPERVISION_MENU;

  readonly fechaHoy = (() => {
    const fecha = new Date().toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    return fecha.charAt(0).toUpperCase() + fecha.slice(1);
  })();

  readonly primerNombre = computed(() => (this.session.usuarioActual()?.nombreCompleto ?? '').split(' ')[0]);

  private readonly porEstado = computed(() => {
    const cuenta = { DESABASTO: 0, SOBREABASTO: 0, NORMAL: 0, SIN_CONSUMO: 0 };
    for (const item of this.rotacion.rotacion()) cuenta[item.estado]++;
    return cuenta;
  });

  readonly indicadores = computed(() => {
    const cuenta = this.porEstado();
    return [
      { titulo: 'Riesgo de desabasto', valor: cuenta.DESABASTO, detalle: `En su mínimo o por debajo (${MESES_STOCK_MINIMO} meses de CPM)`, ruta: '/supervision/alertas', tono: 'danger' },
      { titulo: 'Riesgo de sobreabasto', valor: cuenta.SOBREABASTO, detalle: `En su máximo o por encima (${MESES_STOCK_MAXIMO} meses de CPM)`, ruta: '/supervision/alertas', tono: 'warning' },
      { titulo: 'Claves en rango', valor: cuenta.NORMAL, detalle: 'Entre su mínimo y su máximo', ruta: '/supervision/cpm', tono: 'success' },
      { titulo: 'Sin consumo', valor: cuenta.SIN_CONSUMO, detalle: 'Sin salidas ni CPM de la hoja', ruta: '/supervision/cpm', tono: 'info' },
    ];
  });

  // Las más antiguas primero: son las que llevan más tiempo en riesgo.
  readonly alertas = computed(() => this.rotacion.alertasActivas().slice(0, ALERTAS_EN_INICIO));
  readonly alertasRestantes = computed(() => this.rotacion.alertasActivas().length - this.alertas().length);

  constructor() {
    // Lo vigente en el servidor al entrar, aunque nadie de Almacén esté conectado (HU17 CA4).
    this.rotacion.recargar();
  }

  medicamento(clave: string): string {
    return this.rotacion.buscar(clave)?.medicamento ?? '';
  }

  diasDesde(fecha: string): string {
    const dias = Math.floor((Date.now() - new Date(fecha).getTime()) / 86400000);
    if (dias <= 0) return 'desde hoy';
    return dias === 1 ? 'desde hace 1 día' : `desde hace ${dias} días`;
  }
}
