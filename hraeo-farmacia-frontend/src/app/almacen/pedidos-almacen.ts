import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { AlmacenApi, EstatusPedido, EstatusPedidoIntelisis, PartidaPedido, Pedido as PedidoApi, mensajeDeError } from './almacen-api';

export type EstadoPedido = EstatusPedido;

// Pedido de Intelisis con un resumen de sus partidas para las listas y el Inicio.
// Los pedidos se leen de Intelisis (allá se capturan y se cancelan); HAERO solo
// guarda lo que Almacén recibió físicamente (cajasRecibidas).
export interface Pedido {
  id: number;
  numero: string;
  fecha: string;
  proveedor: string;
  estado: EstadoPedido;
  partidas: PartidaPedido[];
  motivoCancelacion: string | null;
  contrato: string | null;
  fechaEntrega: string | null;
  estatusIntelisis: EstatusPedidoIntelisis | null;
  fechaCancelacion: string | null;
  // Resumen de todas las partidas.
  clave: string;
  medicamento: string;
  cajasEsperadas: number;
  // Recibidas en HAERO (lo que registró Almacén) y lo que dice Intelisis.
  cajasRecibidas: number;
  cajasRecibidasIntelisis: number;
}

export type EstadoTextoPedido = 'Cancelado' | 'Recibido' | 'Parcial' | 'Pendiente';

// Pedidos de Recepción leídos del backend (GET /api/almacen/pedidos). Los comparten
// las páginas Registrar entrada, Pedidos, Pedidos cancelados y el Inicio.
@Injectable({ providedIn: 'root' })
export class PedidosAlmacen {
  private readonly api = inject(AlmacenApi);
  private readonly _pedidos = signal<Pedido[]>([]);

  readonly pedidos = this._pedidos.asReadonly();
  // Por recibir: pendientes en Intelisis (ni concluidos ni cancelados).
  readonly pendientes = computed(() => this._pedidos().filter((pedido) => this.estadoTexto(pedido) === 'Pendiente' || this.estadoTexto(pedido) === 'Parcial'));
  readonly cancelados = computed(() => this._pedidos().filter((pedido) => pedido.estado === 'CANCELADO'));
  // Pedidos vigentes con alguna partida cancelada en Intelisis (esa clave ya no se recibe completa).
  readonly conPartidasCanceladas = computed(() =>
    this._pedidos().filter((pedido) => pedido.estado !== 'CANCELADO' && pedido.partidas.some((partida) => (partida.canceladaIntelisis ?? 0) > 0)),
  );
  readonly cargando = signal(false);
  readonly error = signal('');

  constructor() {
    this.recargar();
  }

  recargar(): void {
    this.cargando.set(true);
    this.leer().subscribe({
      next: () => this.cargando.set(false),
      error: (error) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudieron cargar los pedidos.'));
      },
    });
  }

  leer(): Observable<Pedido[]> {
    return this.api.listarPedidos().pipe(
      // Los más recientes primero.
      map((lista) => lista.map(aPedido).sort((a, b) => b.fecha.localeCompare(a.fecha) || b.id - a.id)),
      tap((pedidos) => {
        this._pedidos.set(pedidos);
        this.error.set('');
      }),
    );
  }

  buscar(id: number): Pedido | undefined {
    return this._pedidos().find((pedido) => pedido.id === id);
  }

  /** El estado lo decide Intelisis; "Parcial" es que Almacén ya recibió algo en HAERO. */
  estadoTexto(pedido: Pedido): EstadoTextoPedido {
    if (pedido.estado === 'CANCELADO' || pedido.estatusIntelisis === 'CANCELADO') return 'Cancelado';
    if (pedido.estatusIntelisis === 'CONCLUIDO' || (pedido.cajasEsperadas > 0 && pedido.cajasRecibidas >= pedido.cajasEsperadas)) return 'Recibido';
    if (pedido.cajasRecibidas > 0) return 'Parcial';
    return 'Pendiente';
  }
}

function aPedido(pedido: PedidoApi): Pedido {
  return {
    id: pedido.id,
    numero: pedido.numeroPedido,
    fecha: pedido.fecha,
    proveedor: pedido.proveedor,
    estado: pedido.estatus,
    partidas: pedido.partidas,
    motivoCancelacion: pedido.motivoCancelacion,
    contrato: pedido.contrato,
    fechaEntrega: pedido.fechaEntrega,
    estatusIntelisis: pedido.estatusIntelisis,
    fechaCancelacion: pedido.fechaCancelacion,
    clave: pedido.partidas.map((partida) => partida.clave).join(', '),
    medicamento: pedido.partidas.map((partida) => partida.nombreGenerico).join(', '),
    cajasEsperadas: pedido.partidas.reduce((total, partida) => total + partida.cantidadEsperada, 0),
    cajasRecibidas: pedido.partidas.reduce((total, partida) => total + partida.cantidadRecibida, 0),
    cajasRecibidasIntelisis: pedido.partidas.reduce((total, partida) => total + (partida.recibidaIntelisis ?? 0), 0),
  };
}
