import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PedidosAlmacen } from '../../pedidos-almacen';

// Recepción · Pedidos cancelados (HU11), solo consulta. La cancelación se registra en
// Intelisis; aquí Almacén ve qué pedidos y partidas NO debe recibir. Registrar entrada
// tampoco deja recibir un pedido cancelado, y cada cancelación nueva deja un aviso.
@Component({
  selector: 'app-cancelar-pedido',
  imports: [DatePipe],
  templateUrl: './cancelar-pedido.html',
  styleUrls: ['../recepcion-comun.css', './cancelar-pedido.css'],
})
export class CancelarPedido {
  protected readonly pedidos = inject(PedidosAlmacen);
  readonly buscar = signal('');

  constructor() {
    this.pedidos.recargar();
  }

  private coincide(texto: string): boolean {
    const filtro = this.buscar().trim().toLowerCase();
    return !filtro || texto.toLowerCase().includes(filtro);
  }

  // Los cancelados más recientes primero.
  readonly cancelados = computed(() =>
    [...this.pedidos.cancelados()]
      .sort((a, b) => (b.fechaCancelacion ?? b.fecha).localeCompare(a.fechaCancelacion ?? a.fecha))
      .filter((pedido) => this.coincide(`${pedido.numero} ${pedido.proveedor} ${pedido.contrato ?? ''} ${pedido.clave} ${pedido.medicamento}`)),
  );

  readonly partidasCanceladas = computed(() =>
    this.pedidos
      .conPartidasCanceladas()
      .flatMap((pedido) =>
        pedido.partidas.filter((partida) => (partida.canceladaIntelisis ?? 0) > 0).map((partida) => ({ pedido, partida })),
      )
      .filter(({ pedido, partida }) => this.coincide(`${pedido.numero} ${pedido.proveedor} ${partida.clave} ${partida.nombreGenerico}`)),
  );
}
