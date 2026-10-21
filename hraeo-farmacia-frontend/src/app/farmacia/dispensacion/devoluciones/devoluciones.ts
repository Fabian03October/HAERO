import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { leerGs1 } from '../../../shared/gs1';
import { FARMACIA_MENU } from '../../farmacia-nav';

// Partida de una entrega que se puede devolver (HU34).
interface PartidaEntregada {
  clave: string;
  medicamento: string;
  lote: string;
  entregada: number;
  // Lo que se captura en esta devolución.
  devolver: number;
  danado: boolean;
}

// Entrega encontrada por folio, expediente o lote.
interface EntregaEncontrada {
  folio: string;
  receta: string;
  expediente: string;
  paciente: string;
  fecha: string;
  partidas: PartidaEntregada[];
}

// Devolución ya registrada en el día.
interface DevolucionRegistrada {
  folioEntrega: string;
  clave: string;
  lote: string;
  piezas: number;
  danado: boolean;
  hora: string;
}

// Farmacia · Devoluciones (HU34). Maquetado solo en el frontend: buscar la entrega y
// registrar la devolución necesitan endpoints de Farmacia que aún no existen.
@Component({
  selector: 'app-devoluciones',
  imports: [FormsModule, Topbar, Sidebar],
  templateUrl: './devoluciones.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css'],
})
export class Devoluciones {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;

  readonly busqueda = signal('');
  readonly aviso = signal('');
  readonly entrega = signal<EntregaEncontrada | null>(null);
  readonly devolucionesHoy = signal<DevolucionRegistrada[]>([]);
  readonly motivo = signal('');

  readonly partidas = computed(() =>
    (this.entrega()?.partidas ?? []).map((partida) => ({ ...partida, excede: partida.devolver > partida.entregada })),
  );

  readonly faltantes = computed(() => {
    const faltan: string[] = [];
    const partidas = this.partidas();
    if (!partidas.some((partida) => partida.devolver > 0)) faltan.push('las piezas que se devuelven');
    if (partidas.some((partida) => partida.excede)) faltan.push('corregir las partidas con más piezas de las entregadas');
    if (!this.motivo().trim()) faltan.push('el motivo de la devolución');
    return faltan;
  });

  buscar(): void {
    const texto = this.busqueda().trim();
    if (!texto) {
      this.aviso.set('Escribe el folio de la entrega, el expediente o escanea la caja devuelta.');
      return;
    }
    const gs1 = leerGs1(texto);
    if (gs1?.lote) this.busqueda.set(gs1.lote);
    // HU34 CA1: la entrega se buscará por folio, expediente o lote en el backend de Farmacia.
    this.aviso.set('La búsqueda de entregas se conecta cuando exista el endpoint de dispensaciones de Farmacia.');
  }

  cambiarPartida(indice: number, cambios: Partial<PartidaEntregada>): void {
    this.entrega.update((actual) =>
      actual ? { ...actual, partidas: actual.partidas.map((partida, i) => (i === indice ? { ...partida, ...cambios } : partida)) } : actual,
    );
  }
}
