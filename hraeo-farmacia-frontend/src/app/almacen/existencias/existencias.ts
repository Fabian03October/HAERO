import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AlmacenApi, MotivoAjuste, Movimiento, mensajeDeError } from '../almacen-api';
import { RouterLink } from '@angular/router';
import { InventarioAlmacen } from '../inventario-almacen';
import { MovimientosEspecialesAlmacen } from '../movimientos-especiales-almacen';
import { Session } from '../../auth/session';
import { SolicitudesAlmacen, estaVencido, formatoCaducidad } from '../solicitudes-almacen';

interface GrupoLote {
  clave: string;
  medicamento: string;
  lote: string;
  caducidad: string;
  total: number;
  fefo: boolean;
  vencido: boolean;
  loteOrigen?: string;
  ubicaciones: Renglon[];
}

interface Renglon {
  existenciaId?: number;
  ubicacion: string;
  cajas: number;
}

// Captura abierta sobre una ubicación de un lote: ajuste por conteo (HU36) o reubicación (HU37).
interface Captura {
  accion: 'AJUSTE' | 'REUBICACION';
  grupo: GrupoLote;
  renglon: Renglon;
}

const MOTIVOS: { valor: MotivoAjuste; texto: string }[] = [
  { valor: 'ERROR_CONTEO', texto: 'Error de conteo' },
  { valor: 'DANADA', texto: 'Caja dañada' },
  { valor: 'FALTANTE', texto: 'Faltante' },
  { valor: 'SOBRANTE', texto: 'Sobrante' },
];

// Despacho · Existencias por ubicación (HU12). Cada lote muestra su desglose
// por ubicación física y se marca con FEFO el que debe salir primero (HU15).
@Component({
  selector: 'app-existencias',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './existencias.html',
  styleUrls: ['../recepcion-almacen/recepcion-comun.css', '../despacho/despacho-comun.css', './existencias.css'],
})
export class Existencias {
  protected readonly inventario = inject(InventarioAlmacen);
  private readonly solicitudes = inject(SolicitudesAlmacen);
  protected readonly formatoCaducidad = formatoCaducidad;
  // Lote nuevo de un canje → lote al que sustituye (HU18 CA3, PA-I2-13).
  private readonly especiales = inject(MovimientosEspecialesAlmacen);
  private readonly origenPorLote = computed(() => new Map(this.especiales.canjes().map((canje) => [canje.loteNuevoId, canje.loteOrigen])));
  // Supervisión usa esta misma consulta sin acciones de Almacén (solo lectura).
  protected readonly soloLectura = inject(Session).usuarioActual()?.rol === 'SUPERVISION';

  private readonly api = inject(AlmacenApi);
  protected readonly motivos = MOTIVOS;

  readonly captura = signal<Captura | null>(null);
  readonly cajas = signal<number | null>(null);
  readonly motivo = signal<MotivoAjuste | ''>('');
  readonly detalle = signal('');
  readonly destino = signal('');
  readonly guardando = signal(false);
  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'success' | 'error'>('success');
  // HU36 CA4: ajustes y reubicaciones de los últimos 30 días.
  readonly recientes = signal<Movimiento[]>([]);

  readonly filtroClave = signal('');
  readonly filtroLote = signal('');
  readonly filtroUbicacion = signal('');

  readonly ubicaciones = computed(() => [...new Set(this.inventario.lotes().map((lote) => lote.ubicacion))].sort());

  readonly grupos = computed(() => {
    const lotes = [...this.inventario.lotes()].sort(
      (a, b) => a.clave.localeCompare(b.clave) || a.caducidad.localeCompare(b.caducidad) || a.lote.localeCompare(b.lote) || a.ubicacion.localeCompare(b.ubicacion),
    );

    // Caducidad FEFO por clave: la más próxima que todavía no vence.
    const fefoPorClave = new Map<string, string>();
    for (const lote of lotes) {
      if (!estaVencido(lote.caducidad) && !fefoPorClave.has(lote.clave)) fefoPorClave.set(lote.clave, lote.caducidad);
    }

    const clave = this.filtroClave().trim().toLowerCase();
    const numeroLote = this.filtroLote().trim().toLowerCase();
    const ubicacion = this.filtroUbicacion();
    const grupos = new Map<string, GrupoLote>();
    for (const lote of lotes) {
      const llave = `${lote.clave}|${lote.lote}`;
      let grupo = grupos.get(llave);
      if (!grupo) {
        grupo = {
          clave: lote.clave,
          medicamento: this.solicitudes.nombreMedicamento(lote.clave),
          lote: lote.lote,
          caducidad: lote.caducidad,
          total: 0,
          fefo: fefoPorClave.get(lote.clave) === lote.caducidad,
          vencido: estaVencido(lote.caducidad),
          loteOrigen: lote.loteId ? this.origenPorLote().get(lote.loteId) : undefined,
          ubicaciones: [],
        };
        grupos.set(llave, grupo);
      }
      // El total es del lote completo aunque se filtre por ubicación (CA1).
      grupo.total += lote.cajas;
      if (!ubicacion || lote.ubicacion === ubicacion)
        grupo.ubicaciones.push({ existenciaId: lote.existenciaId, ubicacion: lote.ubicacion, cajas: lote.cajas });
    }

    return [...grupos.values()].filter(
      (grupo) =>
        grupo.ubicaciones.length &&
        (!clave || grupo.clave.toLowerCase().includes(clave) || grupo.medicamento.toLowerCase().includes(clave)) &&
        (!numeroLote || grupo.lote.toLowerCase().includes(numeroLote)),
    );
  });

  readonly hayFiltros = computed(() => !!(this.filtroClave() || this.filtroLote() || this.filtroUbicacion()));

  constructor() {
    this.cargarRecientes();
  }

  abrir(accion: Captura['accion'], grupo: GrupoLote, renglon: Renglon): void {
    this.captura.set({ accion, grupo, renglon });
    this.cajas.set(accion === 'AJUSTE' ? renglon.cajas : null);
    this.motivo.set('');
    this.detalle.set('');
    this.destino.set('');
    this.mensaje.set('');
  }

  cerrar(): void {
    this.captura.set(null);
  }

  /** Lo que impide guardar, o '' si se puede. El backend vuelve a validar todo. */
  readonly errorCaptura = computed(() => {
    const captura = this.captura();
    const cajas = this.cajas();
    if (!captura) return '';
    if (cajas === null || !Number.isInteger(cajas) || cajas < 0) return 'Escribe un número entero de cajas.';
    if (captura.accion === 'AJUSTE') {
      if (cajas === captura.renglon.cajas) return 'Las cajas contadas son las mismas que tiene HAERO.';
      if (!this.motivo()) return 'Elige el motivo del ajuste.';
      return '';
    }
    if (cajas === 0) return 'Indica cuántas cajas vas a mover.';
    if (cajas > captura.renglon.cajas) return `Solo hay ${captura.renglon.cajas} cajas en ${captura.renglon.ubicacion}.`;
    const destino = this.destino().trim();
    if (!destino) return 'Escribe la ubicación destino.';
    if (destino.toLowerCase() === captura.renglon.ubicacion.toLowerCase()) return 'La ubicación destino es la misma que la de origen.';
    return '';
  });

  guardar(): void {
    const captura = this.captura();
    const existenciaId = captura?.renglon.existenciaId;
    if (!captura || !existenciaId || this.errorCaptura() || this.guardando()) return;
    const cajas = this.cajas()!;
    const peticion =
      captura.accion === 'AJUSTE'
        ? this.api.ajustar({ existenciaId, cajasContadas: cajas, motivo: this.motivo() as MotivoAjuste, detalle: this.detalle().trim() || undefined })
        : this.api.reubicar({ existenciaId, cajas, ubicacionDestino: this.destino().trim() });
    this.guardando.set(true);
    peticion.subscribe({
      next: (movimiento) => {
        this.guardando.set(false);
        this.captura.set(null);
        this.mostrar(`${captura.grupo.clave} · lote ${captura.grupo.lote}: ${movimiento.motivo ?? 'registrado'}.`, 'success');
        this.inventario.recargar();
        this.cargarRecientes();
      },
      error: (error) => {
        this.guardando.set(false);
        this.mostrar(mensajeDeError(error, 'No se pudo registrar.'), 'error');
        // Otra persona pudo mover las mismas cajas: se vuelve a leer el inventario.
        this.inventario.recargar();
      },
    });
  }

  private mostrar(texto: string, tipo: 'success' | 'error'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }

  private cargarRecientes(): void {
    const hoy = new Date();
    const desde = new Date(hoy.getTime() - 30 * 86400000);
    const dia = (fecha: Date) => fecha.toISOString().slice(0, 10);
    this.api.listarMovimientos({ desde: dia(desde), hasta: dia(hoy) }).subscribe({
      next: (lista) =>
        this.recientes.set(
          lista.filter((m) => m.tipo === 'AJUSTE' || (m.tipo === 'REUBICACION' && m.sentido === 'ENTRADA')).slice(0, 50),
        ),
      error: () => this.recientes.set([]),
    });
  }

  limpiarFiltros(): void {
    this.filtroClave.set('');
    this.filtroLote.set('');
    this.filtroUbicacion.set('');
  }
}
