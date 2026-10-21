import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { leerGs1 } from '../../shared/gs1';
import { FARMACIA_MENU } from '../farmacia-nav';

type Modo = 'LOTE' | 'EXPEDIENTE';

// Paso del recorrido de un lote, del pedido al paciente.
interface PasoRecorrido {
  etapa: string;
  fecha: string | null;
  detalle: string;
}

// Entrega de un lote a un paciente (o, por expediente, de un paciente a un lote).
interface EntregaPaciente {
  fecha: string;
  paciente: string;
  expediente: string;
  clave: string;
  lote: string;
  piezas: number;
  despachador: string;
  folio: string;
}

// Etapas que recorre un lote; se llenan con los movimientos de Almacén y de Farmacia.
const ETAPAS = [
  { etapa: 'Pedido y contrato', detalle: 'Pedido de Intelisis con el que se compró (HU22)' },
  { etapa: 'Entrada a Almacén', detalle: 'Recepción con el registro de Intelisis (HU10)' },
  { etapa: 'Despacho a Farmacia', detalle: 'Salida de Almacén por una solicitud (HU14)' },
  { etapa: 'Recepción en Farmacia', detalle: 'Lo que Farmacia confirmó que llegó (HU26)' },
  { etapa: 'Entregas a pacientes', detalle: 'Dispensaciones de ese lote (HU28)' },
  { etapa: 'Baja en Intelisis', detalle: 'Día en que se dio de baja por volumen (HU31)' },
];

// Farmacia · Trazabilidad de lote a paciente (HU33). Maquetado solo en el frontend: el
// recorrido y las entregas necesitan endpoints de Farmacia. Solo Farmacia y Supervisión ven
// los datos de pacientes y cada consulta quedará registrada con usuario, fecha y hora.
@Component({
  selector: 'app-trazabilidad',
  imports: [FormsModule, Topbar, Sidebar],
  templateUrl: './trazabilidad.html',
  styleUrls: ['../panel-farmacia/panel-farmacia.css', '../farmacia-comun.css', './trazabilidad.css'],
})
export class Trazabilidad {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;

  readonly modo = signal<Modo>('LOTE');
  readonly busqueda = signal('');
  readonly aviso = signal('');
  readonly recorrido = signal<PasoRecorrido[]>(ETAPAS.map((paso) => ({ ...paso, fecha: null })));
  readonly entregas = signal<EntregaPaciente[]>([]);

  cambiarModo(modo: Modo): void {
    this.modo.set(modo);
    this.busqueda.set('');
    this.aviso.set('');
  }

  buscar(): void {
    const texto = this.busqueda().trim();
    if (!texto) {
      this.aviso.set(this.modo() === 'LOTE' ? 'Escribe o escanea el lote.' : 'Escribe o escanea el expediente.');
      return;
    }
    // Con la pistola: si el código es GS1 se busca por su lote.
    const gs1 = this.modo() === 'LOTE' ? leerGs1(texto) : null;
    if (gs1?.lote) this.busqueda.set(gs1.lote);
    this.aviso.set('La consulta de trazabilidad se conecta cuando existan los endpoints de trazabilidad de Farmacia.');
  }
}
