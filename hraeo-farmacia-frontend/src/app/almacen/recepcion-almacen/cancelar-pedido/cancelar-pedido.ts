import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PedidosAlmacen } from '../../pedidos-almacen';
import { mensajeDeError } from '../../almacen-api';

// Recepción · Cancelar pedido (HU11). Un pedido cancelado ya no puede recibirse;
// el backend rechaza cualquier entrada contra él.
@Component({
  selector: 'app-cancelar-pedido',
  imports: [FormsModule],
  templateUrl: './cancelar-pedido.html',
  styleUrls: ['../recepcion-comun.css', './cancelar-pedido.css'],
})
export class CancelarPedido {
  protected readonly pedidos = inject(PedidosAlmacen);

  mensaje = '';
  tipoMensaje: 'success' | 'error' = 'success';
  // Id del pedido elegido en el select ('' = ninguno).
  pedidoACancelar = '';
  motivoCancelacion = '';
  readonly guardando = signal(false);

  cancelarPedido(): void {
    const pedido = this.pedidos.buscar(Number(this.pedidoACancelar));
    if (!pedido) {
      this.mostrarMensaje('Selecciona un pedido para cancelar.', 'error');
      return;
    }
    const motivo = this.motivoCancelacion.trim();
    if (!motivo) {
      this.mostrarMensaje('Indica el motivo de cancelación.', 'error');
      return;
    }

    this.guardando.set(true);
    this.pedidos.cancelar(pedido.id, motivo).subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarMensaje(`Pedido ${pedido.numero} cancelado (${motivo}). Ya no puede seleccionarse para recepción.`, 'success');
        this.pedidoACancelar = '';
        this.motivoCancelacion = '';
      },
      error: (error) => {
        this.guardando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo cancelar el pedido.'), 'error');
      },
    });
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error'): void {
    this.mensaje = texto;
    this.tipoMensaje = tipo;
  }
}
