import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Pedido, PedidosAlmacen } from '../../pedidos-almacen';
import { SolicitudesAlmacen } from '../../solicitudes-almacen';
import { mensajeDeError } from '../../almacen-api';
import { Notificaciones } from '../../../shared/notificaciones/notificaciones';

type FiltroPedidos = 'TODOS' | 'POR_RECIBIR' | 'RECIBIDOS' | 'CANCELADOS';

interface PartidaCaptura {
  clave: string;
  cantidadEsperada: number;
}

// Recepción · Pedidos (HU22). Consulta de pedidos y captura manual de uno nuevo.
// Un pedido puede traer varias claves (partidas); el backend valida que cada
// clave exista en el catálogo de medicamentos.
@Component({
  selector: 'app-pedidos-recepcion',
  imports: [FormsModule, DatePipe],
  host: { '(document:keydown.escape)': 'cerrarDetalle()' },
  templateUrl: './pedidos-recepcion.html',
  styleUrls: ['../recepcion-comun.css', './pedidos-recepcion.css'],
})
export class PedidosRecepcion {
  protected readonly pedidos = inject(PedidosAlmacen);
  private readonly notificaciones = inject(Notificaciones);
  protected readonly catalogo = inject(SolicitudesAlmacen);

  mensaje = '';
  tipoMensaje: 'success' | 'error' = 'success';

  // Lista de pedidos: los más recientes primero (así llegan del servicio),
  // filtrables por estado y con búsqueda por número, proveedor, clave o medicamento.
  readonly filtroEstado = signal<FiltroPedidos>('TODOS');
  readonly busqueda = signal('');

  // Pedido abierto en el detalle (botón "Ver").
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

  readonly filtros = computed(() => {
    const todos = this.pedidos.pedidos();
    const contar = (filtro: FiltroPedidos) => todos.filter((pedido) => this.cumpleEstado(pedido, filtro)).length;
    return [
      { valor: 'TODOS' as const, etiqueta: 'Todos', total: todos.length },
      { valor: 'POR_RECIBIR' as const, etiqueta: 'Por recibir', total: contar('POR_RECIBIR') },
      { valor: 'RECIBIDOS' as const, etiqueta: 'Recibidos', total: contar('RECIBIDOS') },
      { valor: 'CANCELADOS' as const, etiqueta: 'Cancelados', total: contar('CANCELADOS') },
    ];
  });

  readonly pedidosFiltrados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.pedidos.pedidos().filter((pedido) => {
      if (!this.cumpleEstado(pedido, this.filtroEstado())) return false;
      if (!texto) return true;
      const partidas = pedido.partidas.map((partida) => `${partida.clave} ${partida.nombreGenerico}`).join(' ');
      // La fecha se busca como se ve en la tabla (06/10/2026) y como AAAA-MM-DD.
      const [anio, mes, dia] = pedido.fecha.slice(0, 10).split('-');
      return `${pedido.numero} ${dia}/${mes}/${anio} ${pedido.fecha} ${pedido.proveedor} ${partidas}`.toLowerCase().includes(texto);
    });
  });

  private cumpleEstado(pedido: Pedido, filtro: FiltroPedidos): boolean {
    const estado = this.pedidos.estadoTexto(pedido);
    if (filtro === 'POR_RECIBIR') return estado === 'Pendiente' || estado === 'Parcial';
    if (filtro === 'RECIBIDOS') return estado === 'Recibido';
    if (filtro === 'CANCELADOS') return estado === 'Cancelado';
    return true;
  }
  readonly guardando = signal(false);

  numero = '';
  fecha = new Date().toLocaleDateString('en-CA');
  proveedor = '';
  partidas: PartidaCaptura[] = [this.partidaVacia()];

  agregarPartida(): void {
    this.partidas = [...this.partidas, this.partidaVacia()];
  }

  quitarPartida(indice: number): void {
    this.partidas = this.partidas.filter((_, i) => i !== indice);
  }

  registrarPedido(): void {
    const partidas = this.partidas
      .map((partida) => ({ clave: partida.clave.trim(), cantidadEsperada: Number(partida.cantidadEsperada) }))
      .filter((partida) => partida.clave || partida.cantidadEsperada);

    if (!this.numero.trim() || !this.fecha || !this.proveedor.trim()) {
      this.mostrarMensaje('Completa número, fecha y proveedor.', 'error');
      return;
    }
    if (!partidas.length) {
      this.mostrarMensaje('Agrega al menos una clave con sus cajas esperadas.', 'error');
      return;
    }
    if (partidas.some((partida) => !partida.clave || !Number.isInteger(partida.cantidadEsperada) || partida.cantidadEsperada <= 0)) {
      this.mostrarMensaje('Cada partida necesita una clave y cajas esperadas (entero mayor a cero).', 'error');
      return;
    }
    if (new Set(partidas.map((partida) => partida.clave)).size !== partidas.length) {
      this.mostrarMensaje('Hay claves repetidas en el pedido. Junta las cajas en una sola partida.', 'error');
      return;
    }
    if (this.pedidos.existe(this.numero)) {
      this.mostrarMensaje('Ya existe un pedido con ese número.', 'error');
      return;
    }

    const numero = this.numero.trim();
    this.guardando.set(true);
    this.pedidos.registrar({ numeroPedido: numero, fecha: this.fecha, proveedor: this.proveedor.trim(), partidas }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarMensaje(`Pedido ${numero} registrado y disponible para recepción.`, 'success', 'Pedido registrado');
        this.numero = '';
        this.proveedor = '';
        this.partidas = [this.partidaVacia()];
      },
      error: (error) => {
        this.guardando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo registrar el pedido.'), 'error');
      },
    });
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error', titulo = 'Listo'): void {
    // Lo que sí se hizo se avisa con un popup; errores e indicaciones se quedan junto al formulario.
    if (tipo === 'success') {
      this.mensaje = '';
      this.notificaciones.exito(titulo, texto);
      return;
    }
    this.mensaje = texto;
    this.tipoMensaje = tipo;
  }

  private partidaVacia(): PartidaCaptura {
    return { clave: '', cantidadEsperada: 0 };
  }
}
