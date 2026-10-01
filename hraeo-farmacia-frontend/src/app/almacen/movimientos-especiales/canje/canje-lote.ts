import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MESES_PARA_CANJE, MovimientosEspecialesAlmacen } from '../../movimientos-especiales-almacen';
import { SolicitudesAlmacen, formatoCaducidad } from '../../solicitudes-almacen';

// Movimientos especiales · Canje de lote (HU18). El lote próximo a caducar sale
// del inventario y el nuevo entra ligado a él, como la carta de canje.
@Component({
  selector: 'app-canje-lote',
  imports: [FormsModule, DatePipe, RouterLink],
  templateUrl: './canje-lote.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../../despacho/despacho-comun.css', '../movimientos-comun.css'],
})
export class CanjeLote {
  protected readonly movimientos = inject(MovimientosEspecialesAlmacen);
  private readonly solicitudes = inject(SolicitudesAlmacen);
  protected readonly formatoCaducidad = formatoCaducidad;
  protected readonly mesesParaCanje = MESES_PARA_CANJE;

  // Id del lote de origen en el backend ('' = ninguno).
  readonly origenSeleccionado = signal('');
  readonly origen = computed(() => this.movimientos.lotesParaCanje().find((lote) => String(lote.loteId) === this.origenSeleccionado()));
  readonly guardando = signal(false);

  nuevo = { lote: '', caducidad: '', cajas: 0, ubicacion: '' };
  // Se activa al intentar confirmar, para marcar en rojo los campos vacíos.
  intentado = false;

  // La caducidad del lote nuevo tiene que ser posterior a la del lote que sustituye.
  readonly caducidadMinima = computed(() => {
    const origen = this.origen();
    if (!origen) return '';
    const [anio, mes] = origen.caducidad.split('-').map(Number);
    const siguiente = new Date(anio, mes, 1);
    return `${siguiente.getFullYear()}-${String(siguiente.getMonth() + 1).padStart(2, '0')}`;
  });

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'success' | 'error'>('success');

  constructor() {
    // Se propone el lote que caduca antes en cuanto llega el inventario; se puede cambiar en la lista.
    effect(() => {
      const primero = this.movimientos.lotesParaCanje()[0];
      if (primero && !untracked(() => this.origen())) untracked(() => this.elegirOrigen(String(primero.loteId)));
    });
  }

  medicamento(clave: string): string {
    return this.solicitudes.nombreMedicamento(clave);
  }

  elegirOrigen(valor: string): void {
    this.origenSeleccionado.set(valor);
    this.intentado = false;
    const origen = this.origen();
    // Por lo general el proveedor repone las mismas cajas en la misma ubicación.
    this.nuevo = { lote: '', caducidad: '', cajas: origen?.cajas ?? 0, ubicacion: origen?.ubicaciones[0]?.ubicacion ?? '' };
  }

  async registrar(): Promise<void> {
    const origen = this.origen();
    if (!origen) {
      this.mostrarMensaje('Elige el lote que se va a canjear.', 'error');
      return;
    }
    this.intentado = true;
    const nuevo = { ...this.nuevo };
    this.guardando.set(true);
    const error = await this.movimientos.registrarCanje({
      loteOrigenId: origen.loteId,
      loteNuevo: nuevo.lote,
      caducidadNueva: nuevo.caducidad,
      cajasNuevas: Number(nuevo.cajas),
      ubicacion: nuevo.ubicacion,
    });
    this.guardando.set(false);
    if (error) {
      this.mostrarMensaje(error, 'error');
      return;
    }
    this.mostrarMensaje(`Canje registrado: ${origen.lote} salió del inventario y ${nuevo.lote.trim()} quedó disponible en ${nuevo.ubicacion.trim()}.`, 'success');
    this.intentado = false;
    // El inventario se vuelve a leer y el effect propone el siguiente lote canjeable.
    this.origenSeleccionado.set('');
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
