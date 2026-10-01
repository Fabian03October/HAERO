import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { AlmacenApi, CrearPedidoRequest, EstatusPedido, PartidaPedido, Pedido as PedidoApi, mensajeDeError } from './almacen-api';

export type EstadoPedido = EstatusPedido;

// Pedido del backend con un resumen de sus partidas para las listas y el Inicio.
export interface Pedido {
  id: number;
  numero: string;
  fecha: string;
  proveedor: string;
  estado: EstadoPedido;
  partidas: PartidaPedido[];
  motivoCancelacion: string | null;
  // Resumen de todas las partidas.
  clave: string;
  medicamento: string;
  cajasEsperadas: number;
  cajasRecibidas: number;
}

// Pedidos de Recepción leídos del backend (GET /api/almacen/pedidos). Los comparten
// las páginas Registrar entrada, Pedidos, Cancelar pedido y el Inicio.
@Injectable({ providedIn: 'root' })
export class PedidosAlmacen {
  private readonly api = inject(AlmacenApi);
  private readonly _pedidos = signal<Pedido[]>([]);

  readonly pedidos = this._pedidos.asReadonly();
  readonly pendientes = computed(() => this._pedidos().filter((pedido) => pedido.estado === 'ACTIVO' && pedido.cajasRecibidas < pedido.cajasEsperadas));
  readonly cancelados = computed(() => this._pedidos().filter((pedido) => pedido.estado === 'CANCELADO'));
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

  existe(numero: string): boolean {
    return this._pedidos().some((pedido) => pedido.numero.toLowerCase() === numero.trim().toLowerCase());
  }

  registrar(datos: CrearPedidoRequest): Observable<Pedido> {
    return this.api.crearPedido(datos).pipe(
      map(aPedido),
      tap(() => this.recargar()),
    );
  }

  cancelar(id: number, motivo: string): Observable<Pedido> {
    return this.api.cancelarPedido(id, motivo).pipe(
      map(aPedido),
      tap(() => this.recargar()),
    );
  }

  estadoTexto(pedido: Pedido): 'Cancelado' | 'Recibido' | 'Parcial' | 'Pendiente' {
    if (pedido.estado === 'CANCELADO') return 'Cancelado';
    if (pedido.cajasRecibidas >= pedido.cajasEsperadas) return 'Recibido';
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
    clave: pedido.partidas.map((partida) => partida.clave).join(', '),
    medicamento: pedido.partidas.map((partida) => partida.nombreGenerico).join(', '),
    cajasEsperadas: pedido.partidas.reduce((total, partida) => total + partida.cantidadEsperada, 0),
    cajasRecibidas: pedido.partidas.reduce((total, partida) => total + partida.cantidadRecibida, 0),
  };
}
