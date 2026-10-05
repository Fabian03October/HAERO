import { Component, computed, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ESTADO_STOCK_TEXTO, MESES_STOCK_MAXIMO, MESES_STOCK_MINIMO, RotacionAlmacen } from '../../rotacion-almacen';
import { SolicitudesAlmacen } from '../../solicitudes-almacen';

// Rotación · Alertas de stock (HU17). Solo consulta: las alertas se abren y se
// cierran solas en RotacionAlmacen. Supervisión usa esta misma página (CA4).
@Component({
  selector: 'app-alertas-stock',
  imports: [DatePipe],
  templateUrl: './alertas-stock.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../../despacho/despacho-comun.css', './alertas-stock.css'],
})
export class AlertasStock {
  protected readonly rotacion = inject(RotacionAlmacen);
  private readonly solicitudes = inject(SolicitudesAlmacen);
  protected readonly estadoTexto = ESTADO_STOCK_TEXTO;
  protected readonly mesesMinimo = MESES_STOCK_MINIMO;
  protected readonly mesesMaximo = MESES_STOCK_MAXIMO;

  // Se muestran por separado: desabasto (hay que pedir), sobreabasto (hay de
  // más) y caducidad próxima (hay que canjear o despachar primero ese lote).
  readonly desabasto = computed(() => this.rotacion.alertasActivas().filter((alerta) => alerta.tipo === 'DESABASTO'));
  readonly sobreabasto = computed(() => this.rotacion.alertasActivas().filter((alerta) => alerta.tipo === 'SOBREABASTO'));
  readonly porVencer = computed(() => this.rotacion.alertasActivas().filter((alerta) => alerta.tipo === 'CADUCIDAD_PROXIMA'));

  // Primero las claves en riesgo, luego las que están en rango.
  readonly niveles = computed(() => {
    const orden = { DESABASTO: 0, SOBREABASTO: 1, NORMAL: 2, SIN_CONSUMO: 3 };
    return [...this.rotacion.rotacion()].sort((a, b) => orden[a.estado] - orden[b.estado] || a.clave.localeCompare(b.clave));
  });

  medicamento(clave: string): string {
    return this.solicitudes.nombreMedicamento(clave);
  }

  existenciaActual(clave: string): number {
    return this.rotacion.buscar(clave)?.existencia ?? 0;
  }

  diasDesde(fecha: string): string {
    const dias = Math.floor((Date.now() - new Date(fecha).getTime()) / 86400000);
    if (dias <= 0) return 'hoy';
    return dias === 1 ? 'hace 1 día' : `hace ${dias} días`;
  }

  diasHasta(fecha: string): string {
    const dias = Math.ceil((new Date(fecha).getTime() - Date.now()) / 86400000);
    if (dias <= 0) return 'vence hoy';
    return dias === 1 ? 'falta 1 día' : `faltan ${dias} días`;
  }
}
