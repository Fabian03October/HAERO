import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { leerGs1 } from '../../../shared/gs1';
import { FARMACIA_MENU } from '../../farmacia-nav';

// Meses hacia adelante para considerar un lote "próximo a caducar", igual que en Almacén.
const MESES_ALERTA_CADUCIDAD = 9;

// Lote en el inventario de Farmacia (HU27). Las piezas son la unidad de Farmacia.
interface LoteFarmacia {
  clave: string;
  medicamento: string;
  piezasPorCaja: number | null;
  lote: string;
  // AAAA-MM-DD
  caducidad: string;
  piezas: number;
}

type EstadoLote = 'FEFO' | 'ESPERA' | 'VENCIDO';
type Filtro = '' | 'FEFO' | 'POR_CADUCAR' | 'VENCIDO';

// Farmacia · Existencias (HU27). Maquetado solo en el frontend: el inventario de Farmacia
// se llena con el alta inicial (HU25) y la recepción (HU26) cuando exista su endpoint.
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

  private readonly hoy = (() => {
    const ahora = new Date();
    return `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(ahora.getDate()).padStart(2, '0')}`;
  })();

  // Lotes en orden de caducidad; por clave, el primero vigente con piezas es el que sale (FEFO).
  readonly lotesConEstado = computed(() => {
    const ordenados = [...this.lotes()].sort((a, b) => a.clave.localeCompare(b.clave) || a.caducidad.localeCompare(b.caducidad));
    const conFefo = new Set<string>();
    return ordenados.map((lote) => {
      let estado: EstadoLote = 'ESPERA';
      if (lote.caducidad < this.hoy) estado = 'VENCIDO';
      else if (lote.piezas > 0 && !conFefo.has(lote.clave)) {
        estado = 'FEFO';
        conFefo.add(lote.clave);
      }
      return {
        ...lote,
        estado,
        porCaducar: estado !== 'VENCIDO' && this.caducaPronto(lote.caducidad),
        cajas: lote.piezasPorCaja ? Math.floor(lote.piezas / lote.piezasPorCaja) : null,
      };
    });
  });

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

  private caducaPronto(caducidad: string): boolean {
    const [anio, mes] = caducidad.split('-').map(Number);
    if (!anio || !mes) return false;
    const hoy = new Date();
    const mesesRestantes = (anio - hoy.getFullYear()) * 12 + (mes - (hoy.getMonth() + 1));
    return mesesRestantes <= MESES_ALERTA_CADUCIDAD;
  }
}
