import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { folioSolicitud, mensajeDeError } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { DespachoPorRecibir, FarmaciaApi } from '../../farmacia-api';

// Farmacia · Recepción de lo despachado por Almacén (HU26). Cada despacho llega con sus lotes;
// Farmacia confirma las cajas que llegaron de cada lote y entran a su inventario en piezas, con
// el mismo lote y caducidad. Si llega algo distinto se pide el motivo y Almacén lo ve.
@Component({
  selector: 'app-recepcion-farmacia',
  imports: [FormsModule, DatePipe, Topbar, Sidebar],
  templateUrl: './recepcion-farmacia.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css'],
})
export class RecepcionFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  protected readonly folio = folioSolicitud;
  readonly items = FARMACIA_MENU;
  private readonly api = inject(FarmaciaApi);

  readonly despachos = signal<DespachoPorRecibir[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal('');
  readonly aviso = signal('');
  readonly busqueda = signal('');
  readonly seleccionada = signal<DespachoPorRecibir | null>(null);
  // Cajas que llegaron de cada lote (por movimiento de Almacén).
  readonly recibidas = signal<Record<number, number | null>>({});
  readonly motivo = signal('');

  // Lo que Almacén despachó y Farmacia no ha recibido, lo más reciente primero.
  readonly despachadas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.despachos().filter(
      (item) => !texto || `${folioSolicitud(item.solicitudId)} ${item.clave} ${item.medicamento}`.toLowerCase().includes(texto),
    );
  });

  readonly totalRecibidas = computed(() =>
    Object.values(this.recibidas()).reduce<number>((total, cajas) => total + (cajas ?? 0), 0),
  );

  readonly diferencia = computed(() => {
    const despacho = this.seleccionada();
    return despacho ? this.totalRecibidas() - despacho.cajasDespachadas : 0;
  });

  readonly faltantes = computed(() => {
    const faltan: string[] = [];
    const despacho = this.seleccionada();
    const valores = despacho ? despacho.partidas.map((p) => this.recibidas()[p.movimientoId]) : [];
    if (valores.some((cajas) => cajas === null || cajas === undefined || cajas < 0 || !Number.isInteger(cajas))) {
      faltan.push('las cajas que llegaron de cada lote');
    }
    const conDiferencia = despacho?.partidas.some((p) => this.recibidas()[p.movimientoId] !== p.cajas);
    if (conDiferencia && !this.motivo().trim()) faltan.push('el motivo de la diferencia');
    return faltan;
  });

  constructor() {
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.api.porRecibir().subscribe({
      next: (lista) => {
        this.despachos.set([...lista].sort((a, b) => b.fechaSolicitud.localeCompare(a.fechaSolicitud)));
        this.cargando.set(false);
      },
      error: (error) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudieron cargar los despachos de Almacén.'));
      },
    });
  }

  elegir(despacho: DespachoPorRecibir): void {
    this.seleccionada.set(despacho);
    // Lo normal es que llegue lo despachado; si no, se corrige y se pide el motivo.
    this.recibidas.set(Object.fromEntries(despacho.partidas.map((p) => [p.movimientoId, p.cajas])));
    this.motivo.set('');
    this.aviso.set('');
  }

  cambiarRecibidas(movimientoId: number, valor: number | string | null): void {
    const cajas = valor === null || valor === '' ? null : +valor;
    this.recibidas.update((actual) => ({ ...actual, [movimientoId]: cajas }));
  }

  confirmar(): void {
    const despacho = this.seleccionada();
    if (!despacho || this.faltantes().length || this.guardando()) return;
    this.guardando.set(true);
    this.error.set('');
    const partidas = despacho.partidas.map((p) => ({ movimientoId: p.movimientoId, cajasRecibidas: this.recibidas()[p.movimientoId] ?? 0 }));
    this.api.recibir(despacho.solicitudId, partidas, this.motivo().trim()).subscribe({
      next: (recepcion) => {
        this.guardando.set(false);
        this.seleccionada.set(null);
        this.aviso.set(
          `Recepción de ${folioSolicitud(recepcion.solicitudId)} guardada: ${recepcion.cajasRecibidas} cajas ` +
            `(${recepcion.piezasRecibidas.toLocaleString('es-MX')} piezas) entraron al inventario de Farmacia.`,
        );
        this.cargar();
      },
      error: (error) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo guardar la recepción.'));
      },
    });
  }
}
