import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { mensajeDeError } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { ClaveReceta, FarmaciaApi, Receta } from '../../farmacia-api';

type EstadoReceta = 'PARCIAL' | 'NO_DISPENSADA';
type RecetaPendiente = Receta & { estado: EstadoReceta };

// Farmacia · Recetas parciales y no dispensadas (HU29): las recetas que quedaron con algo
// pendiente o sin surtir, con lo entregado y lo pendiente de cada clave.
@Component({
  selector: 'app-recetas-parciales',
  imports: [FormsModule, RouterLink, Topbar, Sidebar],
  templateUrl: './recetas-parciales.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css', './recetas-parciales.css'],
})
export class RecetasParciales {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;

  readonly recetas = signal<RecetaPendiente[]>([]);
  readonly filtroEstado = signal<EstadoReceta | ''>('');
  readonly busqueda = signal('');
  readonly seleccionada = signal<RecetaPendiente | null>(null);
  readonly cargando = signal(true);
  readonly error = signal('');

  constructor() {
    inject(FarmaciaApi).recetas().subscribe({
      next: (lista) => {
        this.recetas.set(lista as RecetaPendiente[]);
        this.cargando.set(false);
      },
      error: (error) => {
        this.error.set(mensajeDeError(error, 'No se pudieron cargar las recetas.'));
        this.cargando.set(false);
      },
    });
  }

  readonly estadoTexto: Record<EstadoReceta, string> = {
    PARCIAL: 'Parcial',
    NO_DISPENSADA: 'No dispensada',
  };

  readonly filtros = computed(() => {
    const lista = this.recetas();
    const contar = (estado: EstadoReceta) => lista.filter((receta) => receta.estado === estado).length;
    return [
      { valor: '' as const, etiqueta: 'Todas', total: lista.length },
      { valor: 'PARCIAL' as const, etiqueta: 'Parciales', total: contar('PARCIAL') },
      { valor: 'NO_DISPENSADA' as const, etiqueta: 'No dispensadas', total: contar('NO_DISPENSADA') },
    ];
  });

  readonly recetasFiltradas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.recetas().filter((receta) => {
      if (this.filtroEstado() && receta.estado !== this.filtroEstado()) return false;
      if (!texto) return true;
      const claves = receta.claves.map((item) => `${item.clave} ${item.medicamento}`).join(' ');
      return `${receta.folio} ${receta.expediente ?? ''} ${receta.paciente} ${claves}`.toLowerCase().includes(texto);
    });
  });

  // Claves que más faltan por surtir: muestra el desabasto que vive el paciente.
  readonly clavesMasPendientes = computed(() => {
    const porClave = new Map<string, { clave: string; medicamento: string; piezas: number; recetas: number }>();
    for (const receta of this.recetas()) {
      for (const item of receta.claves) {
        const pendiente = item.indicada - item.entregada;
        if (pendiente <= 0) continue;
        const actual = porClave.get(item.clave) ?? { clave: item.clave, medicamento: item.medicamento, piezas: 0, recetas: 0 };
        actual.piezas += pendiente;
        actual.recetas += 1;
        porClave.set(item.clave, actual);
      }
    }
    return [...porClave.values()].sort((a, b) => b.piezas - a.piezas).slice(0, 5);
  });

  pendiente(item: ClaveReceta): number {
    return Math.max(0, item.indicada - item.entregada);
  }

  fecha(valor: string): string {
    return new Date(valor).toLocaleString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false });
  }
}
