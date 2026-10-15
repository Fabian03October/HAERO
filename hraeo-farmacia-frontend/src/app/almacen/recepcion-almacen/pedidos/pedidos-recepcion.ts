import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { DIAS_PEDIDO_VIGENTE, Pedido, PedidosAlmacen } from '../../pedidos-almacen';

// AHORA: pendientes con entrega reciente o próxima (lo que de verdad se espera). POR_RECIBIR:
// todo lo que Intelisis tiene pendiente del último año, aunque sea viejo.
type FiltroPedidos = 'AHORA' | 'POR_RECIBIR' | 'RECIBIDOS' | 'CANCELADOS' | 'TODOS';
const FILTROS: FiltroPedidos[] = ['AHORA', 'POR_RECIBIR', 'RECIBIDOS', 'CANCELADOS', 'TODOS'];

// Se pintan de a poco: Intelisis trae cientos de pedidos del último año.
const PEDIDOS_POR_TANDA = 100;

// Recepción · Pedidos (HU22), solo consulta. Los pedidos se leen de Intelisis, con
// su contrato y entrega comprometida; ahí se capturan y se cancelan. HAERO muestra
// aparte lo que Almacén recibió físicamente contra cada pedido.
@Component({
  selector: 'app-pedidos-recepcion',
  imports: [FormsModule, DatePipe],
  host: { '(document:keydown.escape)': 'cerrarDetalle()' },
  templateUrl: './pedidos-recepcion.html',
  styleUrls: ['../recepcion-comun.css', './pedidos-recepcion.css'],
})
export class PedidosRecepcion {
  protected readonly pedidos = inject(PedidosAlmacen);
  protected readonly diasVigente = DIAS_PEDIDO_VIGENTE;

  constructor() {
    this.pedidos.recargar();
  }

  // Se puede abrir en un filtro desde otra pantalla (?filtro=AHORA, como el cuadro del Inicio).
  private readonly filtroInicial = inject(ActivatedRoute).snapshot.queryParamMap.get('filtro') as FiltroPedidos | null;
  readonly filtroEstado = signal<FiltroPedidos>(this.filtroInicial && FILTROS.includes(this.filtroInicial) ? this.filtroInicial : 'AHORA');
  readonly busqueda = signal('');
  readonly limite = signal(PEDIDOS_POR_TANDA);

  // Pedido abierto en el detalle.
  readonly detalleId = signal<number | null>(null);
  readonly detalle = computed(() => this.pedidos.pedidos().find((pedido) => pedido.id === this.detalleId()) ?? null);

  verDetalle(pedido: Pedido): void {
    this.detalleId.set(pedido.id);
  }

  cerrarDetalle(): void {
    this.detalleId.set(null);
  }

  avance(pedido: Pedido): number {
    return pedido.cajasEsperadas ? Math.min(100, Math.round((pedido.cajasRecibidas / pedido.cajasEsperadas) * 100)) : 0;
  }

  /** La entrega comprometida ya pasó y el pedido sigue por recibir. */
  vencido(pedido: Pedido): boolean {
    const estado = this.pedidos.estadoTexto(pedido);
    return !!pedido.fechaEntrega && (estado === 'Pendiente' || estado === 'Parcial') && pedido.fechaEntrega < new Date().toLocaleDateString('en-CA');
  }

  readonly filtros = computed(() => {
    const todos = this.pedidos.pedidos();
    const contar = (filtro: FiltroPedidos) => todos.filter((pedido) => this.cumpleEstado(pedido, filtro)).length;
    return [
      { valor: 'AHORA' as const, etiqueta: 'Por recibir ahora', total: contar('AHORA') },
      { valor: 'POR_RECIBIR' as const, etiqueta: 'Pendientes en Intelisis (todos)', total: contar('POR_RECIBIR') },
      { valor: 'RECIBIDOS' as const, etiqueta: 'Recibidos', total: contar('RECIBIDOS') },
      { valor: 'CANCELADOS' as const, etiqueta: 'Cancelados', total: contar('CANCELADOS') },
      { valor: 'TODOS' as const, etiqueta: 'Todos', total: todos.length },
    ];
  });

  readonly pedidosFiltrados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.pedidos.pedidos().filter((pedido) => {
      if (!this.cumpleEstado(pedido, this.filtroEstado())) return false;
      if (!texto) return true;
      const partidas = pedido.partidas.map((partida) => `${partida.clave} ${partida.nombreGenerico} ${partida.cucop ?? ''}`).join(' ');
      // La fecha se busca como se ve en la tabla (06/10/2026) y como AAAA-MM-DD.
      const [anio, mes, dia] = pedido.fecha.slice(0, 10).split('-');
      return `${pedido.numero} ${dia}/${mes}/${anio} ${pedido.fecha} ${pedido.proveedor} ${pedido.contrato ?? ''} ${partidas}`.toLowerCase().includes(texto);
    });
  });

  readonly visibles = computed(() => this.pedidosFiltrados().slice(0, this.limite()));

  elegirFiltro(filtro: FiltroPedidos): void {
    this.filtroEstado.set(filtro);
    this.limite.set(PEDIDOS_POR_TANDA);
  }

  buscar(texto: string): void {
    this.busqueda.set(texto);
    this.limite.set(PEDIDOS_POR_TANDA);
  }

  mostrarMas(): void {
    this.limite.update((limite) => limite + PEDIDOS_POR_TANDA);
  }

  private cumpleEstado(pedido: Pedido, filtro: FiltroPedidos): boolean {
    const estado = this.pedidos.estadoTexto(pedido);
    if (filtro === 'AHORA') return this.pedidos.esPorRecibirAhora(pedido);
    if (filtro === 'POR_RECIBIR') return estado === 'Pendiente' || estado === 'Parcial';
    if (filtro === 'RECIBIDOS') return estado === 'Recibido';
    if (filtro === 'CANCELADOS') return estado === 'Cancelado';
    return true;
  }
}
