import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { InventarioAlmacen } from '../../inventario-almacen';
import { LoteAgrupado, MovimientosEspecialesAlmacen } from '../../movimientos-especiales-almacen';
import { SolicitudesAlmacen, formatoCaducidad } from '../../solicitudes-almacen';

// Movimientos especiales · Caducados (HU19). El sistema sugiere los lotes vencidos,
// pero solo se mueven cuando el despachador confirma (CA4).
@Component({
  selector: 'app-caducados',
  imports: [DatePipe],
  templateUrl: './caducados.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../../despacho/despacho-comun.css', '../movimientos-comun.css', './caducados.css'],
})
export class Caducados {
  protected readonly movimientos = inject(MovimientosEspecialesAlmacen);
  private readonly inventario = inject(InventarioAlmacen);
  private readonly solicitudes = inject(SolicitudesAlmacen);
  protected readonly formatoCaducidad = formatoCaducidad;

  // Lote que espera confirmación (id del lote).
  readonly porConfirmar = signal('');

  // Disponible = inventario sin vencidos; así se ve la diferencia con el apartado.
  readonly cajasDisponibles = computed(() => this.inventario.totalCajas() - this.movimientos.vencidosPorApartar().reduce((total, lote) => total + lote.cajas, 0));

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'success' | 'error'>('success');

  medicamento(clave: string): string {
    return this.solicitudes.nombreMedicamento(clave);
  }

  readonly guardando = signal(false);

  llave(lote: LoteAgrupado): string {
    return String(lote.loteId);
  }

  async confirmar(lote: LoteAgrupado): Promise<void> {
    this.guardando.set(true);
    const error = await this.movimientos.moverACaducados(lote.loteId);
    this.guardando.set(false);
    this.porConfirmar.set('');
    if (error) {
      this.mostrarMensaje(error, 'error');
      return;
    }
    this.mostrarMensaje(`Lote ${lote.lote} movido a caducados: ${lote.cajas} cajas dejan de contar como existencia disponible.`, 'success');
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
