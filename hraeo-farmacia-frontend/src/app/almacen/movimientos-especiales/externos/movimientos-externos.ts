import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { InventarioAlmacen } from '../../inventario-almacen';
import { MovimientosEspecialesAlmacen, SentidoExterno, TipoExterno } from '../../movimientos-especiales-almacen';
import { SolicitudesAlmacen, estaVencido, formatoCaducidad } from '../../solicitudes-almacen';

// Movimientos especiales · Préstamos y transferencias (HU20). Se registran por
// separado del traspaso a Farmacia y guardan la institución involucrada.
@Component({
  selector: 'app-movimientos-externos',
  imports: [FormsModule, DatePipe],
  templateUrl: './movimientos-externos.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../../despacho/despacho-comun.css', '../movimientos-comun.css'],
})
export class MovimientosExternos {
  protected readonly movimientos = inject(MovimientosEspecialesAlmacen);
  protected readonly solicitudes = inject(SolicitudesAlmacen);
  private readonly inventario = inject(InventarioAlmacen);
  protected readonly formatoCaducidad = formatoCaducidad;

  readonly tipo = signal<TipoExterno>('PRESTAMO');
  readonly sentido = signal<SentidoExterno>('SALIDA');
  readonly clave = signal('');
  institucion = '';
  cajas = 0;
  // Salida: id de la existencia (lote y ubicación) elegida ('' = ninguna).
  existenciaElegida = '';
  readonly guardando = signal(false);
  // Entrada: datos de lo que se recibe.
  entrada = { lote: '', caducidad: '', ubicacion: '' };

  readonly existenciasDeClave = computed(() => {
    return this.inventario
      .lotes()
      .filter((lote) => lote.clave === this.clave().trim() && !estaVencido(lote.caducidad))
      .sort((a, b) => a.caducidad.localeCompare(b.caducidad) || a.ubicacion.localeCompare(b.ubicacion));
  });

  readonly filtroTipo = signal<'' | TipoExterno>('');
  readonly historial = computed(() => {
    const tipo = this.filtroTipo();
    return tipo ? this.movimientos.externos().filter((item) => item.tipo === tipo) : this.movimientos.externos();
  });

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'success' | 'error'>('success');

  cambiarClave(valor: string): void {
    this.clave.set(valor);
    this.existenciaElegida = '';
  }

  async registrar(): Promise<void> {
    const clave = this.clave().trim();
    if (clave && !this.solicitudes.clavesConocidas().includes(clave)) {
      this.mostrarMensaje('La clave no existe en el catálogo.', 'error');
      return;
    }
    this.guardando.set(true);
    const error = await this.movimientos.registrarExterno({
      tipo: this.tipo(),
      sentido: this.sentido(),
      institucion: this.institucion,
      clave,
      existenciaId: this.existenciaElegida ? Number(this.existenciaElegida) : undefined,
      lote: this.entrada.lote,
      caducidad: this.entrada.caducidad,
      ubicacion: this.entrada.ubicacion,
      cajas: Number(this.cajas),
    });
    this.guardando.set(false);
    if (error) {
      this.mostrarMensaje(error, 'error');
      return;
    }
    const verbo = this.sentido() === 'SALIDA' ? 'salieron hacia' : 'se recibieron de';
    this.mostrarMensaje(`${this.tipo() === 'PRESTAMO' ? 'Préstamo' : 'Transferencia'} registrado: ${this.cajas} cajas ${verbo} ${this.institucion.trim()}.`, 'success');
    this.cajas = 0;
    this.existenciaElegida = '';
    this.entrada = { lote: '', caducidad: '', ubicacion: '' };
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
