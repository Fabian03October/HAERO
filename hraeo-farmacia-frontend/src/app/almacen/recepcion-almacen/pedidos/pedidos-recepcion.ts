import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PedidosAlmacen } from '../../pedidos-almacen';
import { SolicitudesAlmacen } from '../../solicitudes-almacen';
import { mensajeDeError } from '../../almacen-api';

interface PartidaCaptura {
  clave: string;
  cantidadEsperada: number;
}

// Recepción · Pedidos (HU22). Consulta de pedidos y captura manual de uno nuevo.
// Un pedido puede traer varias claves (partidas); el backend valida que cada
// clave exista en el catálogo de medicamentos.
@Component({
  selector: 'app-pedidos-recepcion',
  imports: [FormsModule],
  templateUrl: './pedidos-recepcion.html',
  styleUrls: ['../recepcion-comun.css', './pedidos-recepcion.css'],
})
export class PedidosRecepcion {
  protected readonly pedidos = inject(PedidosAlmacen);
  protected readonly catalogo = inject(SolicitudesAlmacen);

  mensaje = '';
  tipoMensaje: 'success' | 'error' = 'success';
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
        this.mostrarMensaje(`Pedido ${numero} registrado y disponible para recepción.`, 'success');
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

  private mostrarMensaje(texto: string, tipo: 'success' | 'error'): void {
    this.mensaje = texto;
    this.tipoMensaje = tipo;
  }

  private partidaVacia(): PartidaCaptura {
    return { clave: '', cantidadEsperada: 0 };
  }
}
