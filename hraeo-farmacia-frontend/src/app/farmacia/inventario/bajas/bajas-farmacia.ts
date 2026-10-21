import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { FARMACIA_MENU } from '../../farmacia-nav';

type MotivoBaja = 'CADUCIDAD' | 'DANO' | 'EXTRAVIO';

// Lote de Farmacia que se puede dar de baja.
interface LoteBaja {
  clave: string;
  medicamento: string;
  lote: string;
  caducidad: string;
  piezas: number;
}

// Baja ya registrada.
interface BajaRegistrada extends LoteBaja {
  motivo: MotivoBaja;
  detalle: string;
  fecha: string;
  usuario: string;
}

// Farmacia · Caducados y mermas (HU35). Maquetado solo en el frontend: los lotes vencidos
// salen del inventario de Farmacia (HU27) y la baja se guarda cuando exista su endpoint.
// Igual que en la HU19, el sistema sugiere los vencidos pero nunca los da de baja solo.
@Component({
  selector: 'app-bajas-farmacia',
  imports: [FormsModule, Topbar, Sidebar],
  templateUrl: './bajas-farmacia.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css'],
})
export class BajasFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;

  readonly vista = signal<'SUGERIDOS' | 'REGISTRADAS'>('SUGERIDOS');
  readonly vencidos = signal<LoteBaja[]>([]);
  readonly registradas = signal<BajaRegistrada[]>([]);

  readonly seleccionado = signal<LoteBaja | null>(null);
  readonly piezas = signal<number | null>(null);
  readonly motivo = signal<MotivoBaja>('CADUCIDAD');
  readonly detalle = signal('');

  readonly motivos: { valor: MotivoBaja; etiqueta: string }[] = [
    { valor: 'CADUCIDAD', etiqueta: 'Caducidad' },
    { valor: 'DANO', etiqueta: 'Daño' },
    { valor: 'EXTRAVIO', etiqueta: 'Extravío' },
  ];
  readonly motivoTexto: Record<MotivoBaja, string> = { CADUCIDAD: 'Caducidad', DANO: 'Daño', EXTRAVIO: 'Extravío' };

  readonly piezasRegistradas = computed(() => this.registradas().reduce((total, baja) => total + baja.piezas, 0));

  readonly faltantes = computed(() => {
    const faltan: string[] = [];
    const lote = this.seleccionado();
    const piezas = this.piezas();
    if (!lote) return ['elegir un lote'];
    if (!piezas || piezas <= 0 || !Number.isInteger(piezas)) faltan.push('las piezas que se dan de baja');
    else if (piezas > lote.piezas) faltan.push(`no más de ${lote.piezas} piezas`);
    if (this.motivo() !== 'CADUCIDAD' && !this.detalle().trim()) faltan.push('describir el daño o el extravío');
    return faltan;
  });

  elegir(lote: LoteBaja): void {
    this.seleccionado.set(lote);
    this.piezas.set(lote.piezas);
    this.motivo.set('CADUCIDAD');
    this.detalle.set('');
  }

  fecha(valor: string): string {
    const [anio, mes, dia] = valor.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
  }
}
