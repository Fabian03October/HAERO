import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SolicitudesAlmacen } from '../../solicitudes-almacen';
import { AlmacenApi, CrearMedicamentoRequest, mensajeDeError } from '../../almacen-api';

// Recepción · Catálogo de medicamentos. Pedidos, entradas, carga inicial y
// solicitudes de Farmacia solo aceptan claves que estén dadas de alta aquí.
@Component({
  selector: 'app-catalogo-medicamentos',
  imports: [FormsModule],
  templateUrl: './catalogo-medicamentos.html',
  styleUrls: ['../recepcion-comun.css', '../pedidos/pedidos-recepcion.css'],
})
export class CatalogoMedicamentos {
  protected readonly datos = inject(SolicitudesAlmacen);
  private readonly api = inject(AlmacenApi);

  mensaje = '';
  tipoMensaje: 'success' | 'error' = 'success';
  readonly guardando = signal(false);
  nuevo: CrearMedicamentoRequest = this.vacio();

  readonly filtro = signal('');
  readonly medicamentos = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    const lista = [...this.datos.catalogo()].sort((a, b) => a.clave.localeCompare(b.clave));
    if (!texto) return lista;
    return lista.filter((item) => `${item.clave} ${item.nombreGenerico} ${item.presentacion}`.toLowerCase().includes(texto));
  });

  registrar(): void {
    const datos: CrearMedicamentoRequest = {
      clave: this.nuevo.clave.trim(),
      nombreGenerico: this.nuevo.nombreGenerico.trim(),
      presentacion: this.nuevo.presentacion.trim(),
      piezasPorCaja: this.nuevo.piezasPorCaja ? Number(this.nuevo.piezasPorCaja) : null,
      descripcion: this.nuevo.descripcion?.trim() || null,
    };
    if (!datos.clave || !datos.nombreGenerico || !datos.presentacion) {
      this.mostrarMensaje('Completa clave, nombre genérico y presentación.', 'error');
      return;
    }
    if (datos.piezasPorCaja !== null && (!Number.isInteger(datos.piezasPorCaja) || datos.piezasPorCaja! <= 0)) {
      this.mostrarMensaje('Las piezas por caja deben ser un número entero mayor a cero.', 'error');
      return;
    }

    this.guardando.set(true);
    this.api.crearMedicamento(datos).subscribe({
      next: (medicamento) => {
        this.guardando.set(false);
        this.mostrarMensaje(`Medicamento ${medicamento.clave} · ${medicamento.nombreGenerico} agregado al catálogo.`, 'success');
        this.nuevo = this.vacio();
        this.datos.recargar();
      },
      error: (error) => {
        this.guardando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo registrar el medicamento.'), 'error');
      },
    });
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error'): void {
    this.mensaje = texto;
    this.tipoMensaje = tipo;
  }

  private vacio(): CrearMedicamentoRequest {
    return { clave: '', nombreGenerico: '', presentacion: '', piezasPorCaja: null, descripcion: '' };
  }
}
