import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { mensajeDeError } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { Baja, FarmaciaApi, LoteFarmacia, MotivoBaja } from '../../farmacia-api';

// Farmacia · Caducados y mermas (HU35). Los lotes vencidos salen del inventario de Farmacia
// (HU27) como sugerencia; un lote vigente dañado o extraviado se elige en "Otro lote". Igual
// que en la HU19, el sistema sugiere los vencidos pero nunca los da de baja solo.
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

  private readonly api = inject(FarmaciaApi);

  readonly vista = signal<'SUGERIDOS' | 'OTROS' | 'REGISTRADAS'>('SUGERIDOS');
  readonly inventario = signal<LoteFarmacia[]>([]);
  readonly registradas = signal<Baja[]>([]);
  readonly vencidos = computed(() => this.inventario().filter((lote) => lote.estado === 'VENCIDO'));
  readonly vigentes = computed(() => this.inventario().filter((lote) => lote.estado !== 'VENCIDO'));
  readonly listaLotes = computed(() => (this.vista() === 'OTROS' ? this.vigentes() : this.vencidos()));
  readonly error = signal('');
  readonly aviso = signal('');
  readonly guardando = signal(false);

  readonly seleccionado = signal<LoteFarmacia | null>(null);
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

  constructor() {
    this.cargar();
  }

  private cargar(): void {
    this.api.existencias().subscribe({
      next: (lotes) => this.inventario.set(lotes),
      error: (error) => this.error.set(mensajeDeError(error, 'No se pudo cargar el inventario de Farmacia.')),
    });
    this.api.bajas().subscribe({
      next: (bajas) => this.registradas.set(bajas),
      error: (error) => this.error.set(mensajeDeError(error, 'No se pudieron cargar las bajas registradas.')),
    });
  }

  elegir(lote: LoteFarmacia): void {
    this.seleccionado.set(lote);
    this.piezas.set(lote.estado === 'VENCIDO' ? lote.piezas : null);
    this.motivo.set(lote.estado === 'VENCIDO' ? 'CADUCIDAD' : 'DANO');
    this.detalle.set('');
    this.aviso.set('');
  }

  darDeBaja(): void {
    const lote = this.seleccionado();
    const piezas = this.piezas();
    if (!lote || !piezas || this.faltantes().length || this.guardando()) return;
    this.guardando.set(true);
    this.error.set('');
    this.api.registrarBaja(lote.id, piezas, this.motivo(), this.detalle().trim()).subscribe({
      next: (baja) => {
        this.guardando.set(false);
        this.seleccionado.set(null);
        this.aviso.set(`Se dieron de baja ${baja.piezas} piezas del lote ${baja.lote} por ${this.motivoTexto[baja.motivo].toLowerCase()}.`);
        this.cargar();
      },
      error: (error) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo registrar la baja.'));
      },
    });
  }

  fecha(valor: string): string {
    const [anio, mes, dia] = valor.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
  }
}
