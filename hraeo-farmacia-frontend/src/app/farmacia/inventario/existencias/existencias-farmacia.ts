import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { leerGs1 } from '../../../shared/gs1';
import { mensajeDeError } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { FarmaciaApi, LoteFarmacia } from '../../farmacia-api';

// Meses hacia adelante para considerar un lote "próximo a caducar", igual que en Almacén.
const MESES_ALERTA_CADUCIDAD = 9;

type Filtro = '' | 'FEFO' | 'POR_CADUCAR' | 'VENCIDO';

// Farmacia · Existencias (HU27). El inventario de Farmacia se llena con el alta inicial (HU25)
// y la recepción (HU26); el backend calcula el lote que sale primero, los por caducar y los vencidos.
@Component({
  selector: 'app-existencias-farmacia',
  imports: [FormsModule, Topbar, Sidebar],
  templateUrl: './existencias-farmacia.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css', './existencias-farmacia.css'],
})
export class ExistenciasFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;
  readonly mesesAlerta = MESES_ALERTA_CADUCIDAD;

  readonly lotes = signal<LoteFarmacia[]>([]);
  readonly busqueda = signal('');
  readonly filtro = signal<Filtro>('');
  readonly avisoBusqueda = signal('');
  readonly cargando = signal(true);
  readonly error = signal('');

  // Lotes en orden de clave y caducidad, con el estado que calcula el backend.
  readonly lotesConEstado = computed(() =>
    [...this.lotes()].sort((a, b) => a.clave.localeCompare(b.clave) || a.caducidad.localeCompare(b.caducidad)),
  );

  constructor() {
    inject(FarmaciaApi).existencias().subscribe({
      next: (lotes) => {
        this.lotes.set(lotes);
        this.cargando.set(false);
      },
      error: (error) => {
        this.error.set(mensajeDeError(error, 'No se pudo cargar el inventario de Farmacia.'));
        this.cargando.set(false);
      },
    });
  }

  // Los vencidos se muestran pero no cuentan como disponibles (HU27 CA3).
  readonly totales = computed(() => {
    const lista = this.lotesConEstado();
    const disponibles = lista.filter((lote) => lote.estado !== 'VENCIDO');
    return {
      piezas: disponibles.reduce((total, lote) => total + lote.piezas, 0),
      lotes: disponibles.length,
      claves: new Set(disponibles.map((lote) => lote.clave)).size,
      porCaducar: lista.filter((lote) => lote.porCaducar).length,
      vencidos: lista.filter((lote) => lote.estado === 'VENCIDO').length,
    };
  });

  readonly filtros = computed(() => {
    const lista = this.lotesConEstado();
    return [
      { valor: '' as Filtro, etiqueta: 'Todos', total: lista.length },
      { valor: 'FEFO' as Filtro, etiqueta: 'Salen primero', total: lista.filter((lote) => lote.estado === 'FEFO').length },
      { valor: 'POR_CADUCAR' as Filtro, etiqueta: 'Por caducar', total: lista.filter((lote) => lote.porCaducar).length },
      { valor: 'VENCIDO' as Filtro, etiqueta: 'Vencidos', total: lista.filter((lote) => lote.estado === 'VENCIDO').length },
    ];
  });

  readonly lotesFiltrados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.lotesConEstado().filter((lote) => {
      if (this.filtro() === 'FEFO' && lote.estado !== 'FEFO') return false;
      if (this.filtro() === 'POR_CADUCAR' && !lote.porCaducar) return false;
      if (this.filtro() === 'VENCIDO' && lote.estado !== 'VENCIDO') return false;
      if (!texto) return true;
      return `${lote.clave} ${lote.medicamento} ${lote.lote}`.toLowerCase().includes(texto);
    });
  });

  // Con la pistola o el teclado: si el código es GS1 se busca por su lote.
  buscarEscaneado(): void {
    const gs1 = leerGs1(this.busqueda());
    if (gs1?.lote) {
      this.busqueda.set(gs1.lote);
      this.avisoBusqueda.set(`Código GS1 leído: se busca el lote ${gs1.lote}.`);
    } else {
      this.avisoBusqueda.set('');
    }
  }

  fecha(valor: string): string {
    const [anio, mes, dia] = valor.split('-');
    return `${dia}/${mes}/${anio}`;
  }

  numero(valor: number): string {
    return valor.toLocaleString('es-MX');
  }
}
