import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { AlmacenApi, Solicitud, folioSolicitud, mensajeDeError } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';

// Farmacia · Recepción de lo despachado por Almacén (HU26). Usa datos reales: las solicitudes
// de Farmacia que Almacén ya atendió (todo o en parte). Confirmar lo recibido lote por lote
// necesita el endpoint de recepción de Farmacia, que aún no existe; por eso el rol Farmacia
// todavía no ve los lotes despachados ni puede guardar la recepción.
@Component({
  selector: 'app-recepcion-farmacia',
  imports: [FormsModule, DatePipe, Topbar, Sidebar],
  templateUrl: './recepcion-farmacia.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css'],
})
export class RecepcionFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  protected readonly folio = folioSolicitud;
  readonly items = FARMACIA_MENU;

  readonly solicitudes = signal<Solicitud[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly busqueda = signal('');
  readonly seleccionada = signal<Solicitud | null>(null);
  readonly cajasRecibidas = signal<number | null>(null);
  readonly motivo = signal('');

  // Lo que Almacén ya despachó a Farmacia, lo más reciente primero.
  readonly despachadas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.solicitudes()
      .filter((item) => item.cantidadAtendida > 0)
      .filter((item) => !texto || `${folioSolicitud(item.id)} ${item.clave} ${item.nombreGenerico}`.toLowerCase().includes(texto));
  });

  readonly diferencia = computed(() => {
    const solicitud = this.seleccionada();
    const recibidas = this.cajasRecibidas();
    return solicitud && recibidas !== null ? recibidas - solicitud.cantidadAtendida : 0;
  });

  readonly faltantes = computed(() => {
    const faltan: string[] = [];
    const recibidas = this.cajasRecibidas();
    if (recibidas === null || recibidas < 0 || !Number.isInteger(recibidas)) faltan.push('las cajas que llegaron');
    if (this.diferencia() !== 0 && !this.motivo().trim()) faltan.push('el motivo de la diferencia');
    return faltan;
  });

  constructor() {
    inject(AlmacenApi).solicitudesFarmacia().subscribe({
      next: (lista) => {
        this.solicitudes.set([...lista].sort((a, b) => b.fecha.localeCompare(a.fecha)));
        this.cargando.set(false);
      },
      error: (error) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudieron cargar las solicitudes atendidas por Almacén.'));
      },
    });
  }

  elegir(solicitud: Solicitud): void {
    this.seleccionada.set(solicitud);
    // Lo normal es que llegue lo despachado; si no, se corrige y se pide el motivo.
    this.cajasRecibidas.set(solicitud.cantidadAtendida);
    this.motivo.set('');
  }
}
