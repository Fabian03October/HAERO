import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { FARMACIA_MENU } from '../farmacia-nav';

// Lo entregado en el día por clave de Intelisis y lote (HU31). Ya resta las devoluciones (HU34).
interface RenglonResumen {
  claveIntelisis: string;
  medicamento: string;
  lote: string;
  piezas: number;
  piezasPorCaja: number | null;
  pacientes: number;
}

// Baja por caducidad, daño o extravío del día (HU35): va aparte porque no es consumo de pacientes.
interface RenglonBaja {
  claveIntelisis: string;
  lote: string;
  piezas: number;
  motivo: string;
}

type ResultadoVerificacion = 'CUADRA' | 'FALTA' | 'DE_MAS';

// Comparación de lo entregado contra lo dado de baja en Intelisis (HU32).
interface RenglonVerificacion {
  claveIntelisis: string;
  lote: string;
  haero: number;
  intelisis: number;
}

// Estado del día respecto a la baja en Intelisis.
type EstadoDia = 'PENDIENTE' | 'DADO_DE_BAJA' | 'VERIFICADO';

// Farmacia · Baja en Intelisis (HU31, HU32). Maquetado solo en el frontend: el resumen del
// día, marcar el día como dado de baja y la verificación necesitan endpoints de Farmacia.
// HAERO no escribe en Intelisis: la baja por volumen la captura una persona con este resumen.
@Component({
  selector: 'app-baja-intelisis',
  imports: [FormsModule, Topbar, Sidebar],
  templateUrl: './baja-intelisis.html',
  styleUrls: ['../panel-farmacia/panel-farmacia.css', '../farmacia-comun.css'],
})
export class BajaIntelisis {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;

  readonly dia = signal(this.hoy());
  readonly estadoDia = signal<EstadoDia>('PENDIENTE');
  readonly folioIntelisis = signal('');
  readonly resumen = signal<RenglonResumen[]>([]);
  readonly bajas = signal<RenglonBaja[]>([]);
  readonly verificacion = signal<RenglonVerificacion[]>([]);
  readonly diasPendientes = signal<string[]>([]);
  readonly diasPendientesTexto = computed(() => this.diasPendientes().map((dia) => this.fecha(dia)).join(', '));

  readonly estadoTexto: Record<EstadoDia, string> = {
    PENDIENTE: 'Pendiente de dar de baja',
    DADO_DE_BAJA: 'Dado de baja · por verificar',
    VERIFICADO: 'Verificado',
  };
  readonly resultadoTexto: Record<ResultadoVerificacion, string> = { CUADRA: 'Cuadra', FALTA: 'Falta dar de baja', DE_MAS: 'Se dio de más' };

  readonly totales = computed(() => ({
    claves: new Set(this.resumen().map((renglon) => renglon.claveIntelisis)).size,
    piezas: this.resumen().reduce((total, renglon) => total + renglon.piezas, 0),
    pacientes: this.resumen().reduce((total, renglon) => total + renglon.pacientes, 0),
  }));

  readonly verificacionConResultado = computed(() =>
    this.verificacion().map((renglon) => {
      const diferencia = renglon.haero - renglon.intelisis;
      const resultado: ResultadoVerificacion = diferencia === 0 ? 'CUADRA' : diferencia > 0 ? 'FALTA' : 'DE_MAS';
      return { ...renglon, diferencia, resultado };
    }),
  );

  readonly conDiferencia = computed(() => this.verificacionConResultado().filter((renglon) => renglon.resultado !== 'CUADRA').length);

  cajas(renglon: RenglonResumen): string {
    if (!renglon.piezasPorCaja) return '—';
    const cajas = renglon.piezas / renglon.piezasPorCaja;
    return Number.isInteger(cajas) ? String(cajas) : cajas.toFixed(1);
  }

  fecha(valor: string): string {
    const [anio, mes, dia] = valor.split('-');
    return `${dia}/${mes}/${anio}`;
  }

  private hoy(): string {
    const ahora = new Date();
    return `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(ahora.getDate()).padStart(2, '0')}`;
  }
}
