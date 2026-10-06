import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { EstadoStock, ESTADO_STOCK_TEXTO, MESES_STOCK_MAXIMO, MESES_STOCK_MINIMO, RotacionAlmacen, nombreMes } from '../../rotacion-almacen';
import { SolicitudesAlmacen, mesActual } from '../../solicitudes-almacen';

type GrupoAlerta = 'desabasto' | 'sobreabasto' | 'caducidad';
type GrupoAviso = 'agotadas' | 'sin-salidas' | 'sin-movimiento' | 'consumo-alto' | 'merma';

export interface AvisoRotacion {
  clave: string;
  detalle: string;
  extra?: string;
}

interface GrupoDeAvisos {
  id: GrupoAviso;
  titulo: string;
  descripcion: string;
  tono: 'low' | 'high' | 'expiry' | 'info';
  items: AvisoRotacion[];
}

// Meses cerrados seguidos sin salidas para considerar una clave "sin movimiento".
const MESES_SIN_MOVIMIENTO = 3;
// El consumo del mes en curso se marca alto cuando ya pasa del CPM por este factor.
const FACTOR_CONSUMO_ALTO = 1;

// Rotación · Alertas de stock (HU17). Solo consulta: las alertas se abren y se
// cierran solas en RotacionAlmacen. Supervisión usa esta misma página (CA4).
// Además de las alertas del backend, se calculan avisos de rotación con los
// mismos datos (CPM, consumo por mes, existencia y lotes por caducar).
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

  // ---------- Avisos de rotación (calculados aquí, informativos) ----------

  private readonly diaDelMes = new Date().getDate();

  readonly avisos = computed<GrupoDeAvisos[]>(() => {
    const claves = this.rotacion.rotacion();
    const mesEnCurso = nombreMes(mesActual());

    const agotadas = claves
      .filter((fila) => fila.cpm > 0 && fila.existencia === 0)
      .map((fila) => ({ clave: fila.clave, detalle: `Sin existencia · CPM ${fila.cpm} · mínimo ${fila.minimo}`, extra: 'pedir ya' }));

    const sinSalidas = claves
      .filter((fila) => fila.cpm > 0 && fila.existencia > 0 && fila.consumoMesActual === 0)
      .map((fila) => ({
        clave: fila.clave,
        detalle: `Hay ${fila.existencia} cajas · CPM ${fila.cpm} · 0 salidas en ${mesEnCurso}`,
        extra: `${this.diaDelMes} ${this.diaDelMes === 1 ? 'día' : 'días'} del mes`,
      }));

    const sinMovimiento = claves
      .filter((fila) => {
        const ultimos = fila.meses.slice(-MESES_SIN_MOVIMIENTO);
        return (
          fila.existencia > 0 &&
          fila.mesesHistorial >= MESES_SIN_MOVIMIENTO &&
          ultimos.length === MESES_SIN_MOVIMIENTO &&
          ultimos.every((mes) => mes.cajas === 0) &&
          fila.consumoMesActual === 0
        );
      })
      .map((fila) => ({
        clave: fila.clave,
        detalle: `${fila.existencia} cajas paradas · sin salidas desde ${nombreMes(fila.meses[fila.meses.length - MESES_SIN_MOVIMIENTO].mes)}`,
        extra: 'revisar canje o transferencia',
      }));

    const consumoAlto = claves
      .filter((fila) => fila.cpm > 0 && fila.consumoMesActual > fila.cpm * FACTOR_CONSUMO_ALTO)
      .map((fila) => ({
        clave: fila.clave,
        detalle: `${fila.consumoMesActual} cajas en ${mesEnCurso} · CPM ${fila.cpm}`,
        extra: `+${Math.round((fila.consumoMesActual / fila.cpm - 1) * 100)}% sobre lo normal`,
      }));

    return [
      { id: 'agotadas', titulo: 'Agotadas', descripcion: 'Claves con consumo que ya no tienen existencia.', tono: 'low', items: agotadas },
      { id: 'sin-salidas', titulo: 'Sin salidas este mes', descripcion: `Claves con consumo habitual que no han salido en ${mesEnCurso}.`, tono: 'info', items: sinSalidas },
      { id: 'sin-movimiento', titulo: 'Sin movimiento', descripcion: `Existencia sin ninguna salida en los últimos ${MESES_SIN_MOVIMIENTO} meses.`, tono: 'high', items: sinMovimiento },
      { id: 'consumo-alto', titulo: 'Consumo alto', descripcion: 'En lo que va del mes ya salió más que su CPM.', tono: 'info', items: consumoAlto },
      { id: 'merma', titulo: 'Riesgo de merma', descripcion: 'Lotes que, al ritmo de consumo actual, caducarían antes de terminarse.', tono: 'expiry', items: this.riesgoMerma() },
    ];
  });

  /**
   * Lotes por caducar que no alcanzarían a despacharse: con FEFO salen primero los
   * lotes que caducan antes, así que a cada lote se le suman las cajas de los lotes
   * de la misma clave que caducan antes que él y se compara con lo que se consumiría
   * (CPM × meses que le quedan).
   */
  private riesgoMerma(): AvisoRotacion[] {
    const avisos: AvisoRotacion[] = [];
    const lotes = this.porVencer().filter((alerta) => alerta.caducidad);
    const porClave = new Map<string, typeof lotes>();
    for (const lote of lotes) porClave.set(lote.clave, [...(porClave.get(lote.clave) ?? []), lote]);

    for (const [clave, deLaClave] of porClave) {
      const cpm = this.rotacion.buscar(clave)?.cpm ?? 0;
      let acumuladas = 0;
      for (const lote of [...deLaClave].sort((a, b) => a.caducidad!.localeCompare(b.caducidad!))) {
        acumuladas += lote.existencia;
        const meses = Math.max(0, (new Date(lote.caducidad!).getTime() - Date.now()) / (30.4 * 86400000));
        const sobrarian = Math.min(lote.existencia, Math.round(acumuladas - cpm * meses));
        if (sobrarian > 0) {
          avisos.push({
            clave,
            detalle: `Lote ${lote.numeroLote} · ${lote.existencia} cajas · caduca ${lote.caducidad!.slice(8, 10)}/${lote.caducidad!.slice(5, 7)}/${lote.caducidad!.slice(0, 4)}`,
            extra: cpm ? `sobrarían ~${sobrarian} cajas` : 'sin consumo registrado',
          });
        }
      }
    }
    return avisos;
  }

  // ---------- Resúmenes: solo una lista abierta a la vez ----------

  readonly abierta = signal<GrupoAlerta | GrupoAviso | null>(null);
  readonly filtro = signal('');
  readonly avisoAbierto = computed(() => this.avisos().find((grupo) => grupo.id === this.abierta()) ?? null);

  alternar(grupo: GrupoAlerta | GrupoAviso): void {
    this.abierta.update((actual) => (actual === grupo ? null : grupo));
    this.filtro.set('');
  }

  // Búsqueda por clave, nombre o lote dentro de la lista abierta.
  coincide(clave: string, lote = ''): boolean {
    const texto = this.filtro().trim().toLowerCase();
    return !texto || `${clave} ${this.medicamento(clave)} ${lote}`.toLowerCase().includes(texto);
  }

  readonly desabastoFiltrado = computed(() => this.desabasto().filter((alerta) => this.coincide(alerta.clave)));
  readonly sobreabastoFiltrado = computed(() => this.sobreabasto().filter((alerta) => this.coincide(alerta.clave)));
  readonly porVencerFiltrado = computed(() => this.porVencer().filter((alerta) => this.coincide(alerta.clave, alerta.numeroLote ?? '')));
  readonly avisoFiltrado = computed(() => this.avisoAbierto()?.items.filter((aviso) => this.coincide(aviso.clave, aviso.detalle)) ?? []);

  // ---------- Tabla de niveles: filtro por estado y búsqueda ----------

  readonly estadoNivel = signal<EstadoStock | 'TODOS'>('TODOS');
  readonly buscarNivel = signal('');

  readonly filtrosNivel = computed(() => {
    const filas = this.rotacion.rotacion();
    const contar = (estado: EstadoStock) => filas.filter((fila) => fila.estado === estado).length;
    return [
      { valor: 'TODOS' as const, etiqueta: 'Todas', total: filas.length },
      { valor: 'DESABASTO' as const, etiqueta: 'Desabasto', total: contar('DESABASTO') },
      { valor: 'SOBREABASTO' as const, etiqueta: 'Sobreabasto', total: contar('SOBREABASTO') },
      { valor: 'NORMAL' as const, etiqueta: 'En rango', total: contar('NORMAL') },
      { valor: 'SIN_CONSUMO' as const, etiqueta: 'Sin consumo', total: contar('SIN_CONSUMO') },
    ];
  });

  // Primero las claves en riesgo, luego las que están en rango.
  readonly niveles = computed(() => {
    const orden = { DESABASTO: 0, SOBREABASTO: 1, NORMAL: 2, SIN_CONSUMO: 3 };
    const estado = this.estadoNivel();
    const texto = this.buscarNivel().trim().toLowerCase();
    return [...this.rotacion.rotacion()]
      .filter((fila) => estado === 'TODOS' || fila.estado === estado)
      .filter((fila) => !texto || `${fila.clave} ${fila.medicamento}`.toLowerCase().includes(texto))
      .sort((a, b) => orden[a.estado] - orden[b.estado] || a.clave.localeCompare(b.clave));
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
