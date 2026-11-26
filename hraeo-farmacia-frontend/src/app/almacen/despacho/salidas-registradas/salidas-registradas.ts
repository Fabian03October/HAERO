import { Paginacion, Paginador } from '../../../shared/paginador/paginador';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MovimientoSalida, SolicitudesAlmacen, TIPO_SALIDA_TEXTO, fechaLocal, formatoCaducidad } from '../../solicitudes-almacen';
import { Session } from '../../../auth/session';
import { AlmacenApi, folioPedido } from '../../almacen-api';
import { firstValueFrom } from 'rxjs';
import { Notificaciones } from '../../../shared/notificaciones/notificaciones';

// Despacho · Salidas registradas (HU14 CA5). Cada despacho deja un movimiento
// con fecha, usuario, lote, cantidad y ubicación de origen. Se consulta por día:
// abre en el día de hoy y se puede elegir cualquier fecha anterior. Las salidas
// marcadas se descargan como comprobante en PDF (quién pidió, quién despachó y cuándo).
@Component({
  selector: 'app-salidas-registradas',
  imports: [Paginador, FormsModule, DatePipe],
  templateUrl: './salidas-registradas.html',
  styleUrls: ['../../recepcion-almacen/recepcion-comun.css', '../despacho-comun.css', './salidas-registradas.css'],
})
export class SalidasRegistradas {
  // Por páginas: la lista crece con el tiempo.
  private readonly api = inject(AlmacenApi);
  readonly pagSalidas = new Paginacion(() => this.salidas());

  protected readonly solicitudes = inject(SolicitudesAlmacen);
  private readonly session = inject(Session);
  private readonly notificaciones = inject(Notificaciones);
  protected readonly formatoCaducidad = formatoCaducidad;
  protected readonly tipoTexto = TIPO_SALIDA_TEXTO;

  readonly hoy = fechaLocal().slice(0, 10);
  readonly dia = signal(this.hoy);
  readonly esHoy = computed(() => this.dia() === this.hoy);
  readonly diaTexto = computed(() => {
    const [anio, mes, dia] = this.dia().split('-').map(Number);
    const texto = new Date(anio, mes - 1, dia).toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  });

  // Día más reciente con movimientos antes del elegido, para saltar a él si el día está vacío.
  readonly diaAnteriorConSalidas = computed(() => this.solicitudes.salidas().map((salida) => salida.fecha.slice(0, 10)).filter((dia) => dia < this.dia()).sort().pop() ?? '');

  readonly filtro = signal('');
  readonly salidas = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    const salidas = this.solicitudes.salidas().filter((salida) => salida.fecha.startsWith(this.dia()));
    if (!texto) return salidas;
    return salidas.filter((salida) => `${salida.folio} ${salida.clave} ${salida.lote} ${salida.ubicacion} ${salida.usuario} ${salida.institucion ?? ''}`.toLowerCase().includes(texto));
  });
  readonly totalCajas = computed(() => this.salidas().reduce((total, salida) => total + salida.cajas, 0));

  // Salidas marcadas para el PDF, por id de movimiento. Se conservan al cambiar de día.
  readonly elegidas = signal<ReadonlySet<number>>(new Set());
  readonly todasElegidas = computed(() => this.salidas().length > 0 && this.salidas().every((salida) => this.elegidas().has(salida.id)));
  readonly generando = signal(false);

  alternar(salida: MovimientoSalida): void {
    const elegidas = new Set(this.elegidas());
    if (!elegidas.delete(salida.id)) elegidas.add(salida.id);
    this.elegidas.set(elegidas);
  }

  alternarTodas(marcar: boolean): void {
    const elegidas = new Set(this.elegidas());
    for (const salida of this.salidas()) {
      if (marcar) elegidas.add(salida.id);
      else elegidas.delete(salida.id);
    }
    this.elegidas.set(elegidas);
  }

  quitarMarcas(): void {
    this.elegidas.set(new Set());
  }

  /** HU39: si todo lo marcado es de un mismo pedido, su vale en una sola hoja. */
  readonly pedidoElegido = computed(() => {
    const pedidos = new Set(this.solicitudes.salidas().filter((s) => this.elegidas().has(s.id)).map((s) => s.pedidoId));
    const [unico] = [...pedidos];
    return pedidos.size === 1 && unico ? unico : null;
  });

  async descargarVale(formato: 'pdf' | 'excel' = 'pdf'): Promise<void> {
    const pedidoId = this.pedidoElegido();
    if (!pedidoId || this.generando()) return;
    this.generando.set(true);
    try {
      const { cargarLogo, armarValePedido, descargarValePedido } = await import('./comprobante-salidas');
      const vale = await firstValueFrom(this.api.valePedido(pedidoId));
      const quien = this.session.usuarioActual()?.nombreCompleto ?? '';
      if (formato === 'pdf') {
        descargarValePedido(folioPedido(pedidoId), vale, quien, await cargarLogo());
      } else {
        const { descargarExcel } = await import('../../../shared/reporte-excel');
        await descargarExcel(armarValePedido(folioPedido(pedidoId), vale, quien, await cargarLogo()), `vale-${folioPedido(pedidoId)}-${new Date().toLocaleDateString('en-CA')}`);
      }
    } catch {
      this.notificaciones.error('No se pudo generar el vale. Intenta de nuevo.');
    } finally {
      this.generando.set(false);
    }
  }

  async descargarPdf(formato: 'pdf' | 'excel' = 'pdf'): Promise<void> {
    const historial = this.solicitudes.salidas();
    const elegidas = historial.filter((salida) => this.elegidas().has(salida.id));
    if (!elegidas.length || this.generando()) return;
    this.generando.set(true);
    try {
      // La librería de PDF se carga solo cuando se usa.
      const { cargarLogo, armarComprobante } = await import('./comprobante-salidas');
      const { doc, nombre } = armarComprobante(elegidas, historial, this.session.usuarioActual()?.nombreCompleto ?? '', await cargarLogo());
      if (formato === 'pdf') {
        doc.save(nombre);
      } else {
        const { descargarExcel } = await import('../../../shared/reporte-excel');
        await descargarExcel(doc, nombre.replace(/\.pdf$/, ''));
      }
    } catch {
      this.notificaciones.error('No se pudo generar el PDF. Intenta de nuevo.');
    } finally {
      this.generando.set(false);
    }
  }

  cambiarDia(dias: number): void {
    const [anio, mes, dia] = this.dia().split('-').map(Number);
    const fecha = fechaLocal(new Date(anio, mes - 1, dia + dias, 12)).slice(0, 10);
    this.dia.set(fecha > this.hoy ? this.hoy : fecha);
  }

  elegirDia(valor: string): void {
    // Si se borra la fecha del selector se vuelve a hoy.
    this.dia.set(!valor || valor > this.hoy ? this.hoy : valor);
  }
}
