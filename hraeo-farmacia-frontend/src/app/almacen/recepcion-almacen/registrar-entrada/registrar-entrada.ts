import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { InventarioAlmacen } from '../../inventario-almacen';
import { PedidosAlmacen } from '../../pedidos-almacen';
import { AlmacenApi, UbicacionCantidad, mensajeDeError } from '../../almacen-api';
import { EscanerCamara } from '../../../shared/escaner-camara/escaner-camara';

// Recepción · Registrar entrada (HU10). Recibe un lote contra una clave de un
// pedido activo y lo reparte en una o varias ubicaciones del almacén.
@Component({
  selector: 'app-registrar-entrada',
  imports: [FormsModule, EscanerCamara],
  templateUrl: './registrar-entrada.html',
  styleUrls: ['../recepcion-comun.css', './registrar-entrada.css'],
})
export class RegistrarEntrada {
  protected readonly pedidos = inject(PedidosAlmacen);
  private readonly inventario = inject(InventarioAlmacen);
  private readonly api = inject(AlmacenApi);

  readonly pedidoId = signal<number | null>(null);
  readonly pedido = computed(() => this.pedidos.pedidos().find((item) => item.id === this.pedidoId()));
  readonly cancelado = computed(() => this.pedido()?.estado === 'CANCELADO');

  readonly clave = signal('');
  readonly partida = computed(() => this.pedido()?.partidas.find((item) => item.clave === this.clave()));
  readonly pendientePartida = computed(() => {
    const partida = this.partida();
    return partida ? Math.max(0, partida.cantidadEsperada - partida.cantidadRecibida) : 0;
  });

  // Signals: los mensajes también cambian dentro de respuestas del backend (la app no usa Zone.js).
  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'success' | 'error' | 'info'>('info');
  codigoEscaneado = '';
  // Escáner con la cámara del teléfono mientras no hay lector físico.
  readonly camaraAbierta = signal(false);
  // Código leído que el backend no reconoce; se ofrece asociarlo a la clave elegida.
  readonly codigoSinRegistrar = signal('');
  readonly guardando = signal(false);

  entrada = { lote: '', caducidad: '' };
  ubicaciones: UbicacionCantidad[] = [{ ubicacion: '', cajas: 0 }];

  seleccionarPedido(valor: string | number): void {
    const id = Number(valor) || null;
    this.pedidoId.set(id);
    const pedido = this.pedido();
    // Si el pedido trae una sola clave se elige sola.
    this.clave.set(pedido?.partidas.length === 1 ? pedido.partidas[0].clave : '');
    if (this.cancelado()) this.mostrarMensaje(`El pedido ${pedido!.numero} está cancelado y no puede recibirse.`, 'error');
    else this.mensaje.set('');
  }

  escanearCodigo(): void {
    const codigo = this.codigoEscaneado.trim();
    this.codigoSinRegistrar.set('');
    if (!codigo) {
      this.mostrarMensaje('Captura o escanea un código de barras.', 'error');
      return;
    }

    this.api.resolverCodigo(codigo).subscribe({
      next: (producto) => {
        const pedido = this.pedidos
          .pendientes()
          .find((item) => item.proveedor.toLowerCase() === producto.proveedor.toLowerCase() && item.partidas.some((partida) => partida.clave === producto.clave && partida.cantidadRecibida < partida.cantidadEsperada));
        if (!pedido) {
          this.mostrarMensaje(`Código reconocido (${producto.clave} · ${producto.nombre} · ${producto.proveedor}), pero no hay un pedido activo pendiente para esa clave y proveedor.`, 'error');
          return;
        }
        this.pedidoId.set(pedido.id);
        this.clave.set(producto.clave);
        this.mostrarMensaje(`Código reconocido: ${producto.clave} · ${producto.nombre} · ${producto.proveedor}. Pedido ${pedido.numero}.`, 'success');
      },
      error: (error) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.codigoSinRegistrar.set(codigo);
          this.mostrarMensaje(
            this.partida()
              ? `Código no registrado. Puedes asociarlo a la clave ${this.clave()} y al proveedor ${this.pedido()!.proveedor}.`
              : 'Código no registrado. Elige el pedido y la clave de esta caja para asociarlo.',
            'error',
          );
          return;
        }
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo consultar el código.'), 'error');
      },
    });
  }

  asociarCodigo(): void {
    const pedido = this.pedido();
    const codigo = this.codigoSinRegistrar();
    if (!pedido || !this.partida() || !codigo) return;
    this.api.asociarCodigo({ codigo, clave: this.clave(), proveedor: pedido.proveedor }).subscribe({
      next: () => {
        this.codigoSinRegistrar.set('');
        this.mostrarMensaje(`Código ${codigo} asociado a ${this.clave()} · ${pedido.proveedor}. La próxima vez se reconocerá al escanear.`, 'success');
      },
      error: (error) => this.mostrarMensaje(mensajeDeError(error, 'No se pudo asociar el código.'), 'error'),
    });
  }

  agregarUbicacion(): void {
    this.ubicaciones = [...this.ubicaciones, { ubicacion: '', cajas: 0 }];
  }

  quitarUbicacion(indice: number): void {
    this.ubicaciones = this.ubicaciones.filter((_, i) => i !== indice);
  }

  totalCajas(): number {
    return this.ubicaciones.reduce((total, fila) => total + (Number(fila.cajas) || 0), 0);
  }

  registrarEntrada(): void {
    const pedido = this.pedido();
    if (!pedido) {
      this.mostrarMensaje('Selecciona un pedido activo.', 'error');
      return;
    }
    if (pedido.estado === 'CANCELADO') {
      this.mostrarMensaje('Este pedido está cancelado. No se permite registrar la entrada.', 'error');
      return;
    }
    if (!this.partida()) {
      this.mostrarMensaje('Elige la clave del pedido que estás recibiendo.', 'error');
      return;
    }
    const ubicaciones = this.ubicaciones.map((fila) => ({ ubicacion: fila.ubicacion.trim(), cajas: Number(fila.cajas) }));
    if (!this.entrada.lote.trim() || !this.entrada.caducidad || ubicaciones.some((fila) => !fila.ubicacion || !Number.isInteger(fila.cajas) || fila.cajas <= 0)) {
      this.mostrarMensaje('Completa lote, caducidad y, en cada ubicación, el nombre y las cajas (entero mayor a cero).', 'error');
      return;
    }

    const total = ubicaciones.reduce((suma, fila) => suma + fila.cajas, 0);
    if (total > this.pendientePartida()) {
      this.mostrarMensaje(`La entrada supera las cajas pendientes de la clave ${this.clave()} (${this.pendientePartida()}).`, 'error');
      return;
    }

    const lote = this.entrada.lote.trim();
    this.guardando.set(true);
    this.api
      .registrarEntrada({
        pedidoId: pedido.id,
        clave: this.clave(),
        proveedor: pedido.proveedor,
        numeroLote: lote,
        caducidad: ultimoDiaDelMes(this.entrada.caducidad),
        ubicaciones,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.mostrarMensaje(`Entrada registrada: lote ${lote}, ${total} cajas en ${ubicaciones.map((fila) => fila.ubicacion).join(', ')}.`, 'success');
          this.entrada = { lote: '', caducidad: '' };
          this.ubicaciones = [{ ubicacion: '', cajas: 0 }];
          this.pedidos.recargar();
          this.inventario.recargar();
        },
        error: (error) => {
          this.guardando.set(false);
          this.mostrarMensaje(mensajeDeError(error, 'No se pudo registrar la entrada.'), 'error');
        },
      });
  }

  /** El escáner de cámara entrega el código igual que si lo hubiera tecleado el lector. */
  codigoDesdeCamara(codigo: string): void {
    this.camaraAbierta.set(false);
    this.codigoEscaneado = codigo;
    this.escanearCodigo();
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error' | 'info'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}

// La caja trae solo mes y año (AAAA-MM); el backend guarda una fecha completa,
// así que se usa el último día de ese mes.
function ultimoDiaDelMes(mes: string): string {
  const [anio, numeroMes] = mes.split('-').map(Number);
  const dia = new Date(anio, numeroMes, 0).getDate();
  return `${mes}-${String(dia).padStart(2, '0')}`;
}
