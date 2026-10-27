import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { mensajeDeError } from '../../almacen/almacen-api';
import { FARMACIA_MENU } from '../farmacia-nav';
import { CorteDia, EstadoDia, FarmaciaApi, RenglonResumen, RenglonVerificacion, ResultadoVerificacion } from '../farmacia-api';

// Farmacia · Baja en Intelisis (HU31, HU32). El resumen del día sale del backend (entregas menos
// devoluciones; las bajas aparte); el responsable lo captura en Intelisis, marca aquí el día y
// después verifica contra lo que HAERO lee del espejo de Intelisis.
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

  private readonly api = inject(FarmaciaApi);

  readonly dia = signal(this.hoy());
  readonly corte = signal<CorteDia | null>(null);
  readonly estadoDia = computed<EstadoDia>(() => this.corte()?.estado ?? 'PENDIENTE');
  readonly folioIntelisis = signal('');
  readonly resumen = computed(() => this.corte()?.consumo ?? []);
  readonly devolucionesPosteriores = computed(() => this.corte()?.devoluciones ?? []);
  readonly bajas = computed(() => this.corte()?.bajas ?? []);
  readonly verificacion = signal<RenglonVerificacion[]>([]);
  readonly sincronizadoEn = signal<string | null>(null);
  readonly diasPendientes = computed(() => this.corte()?.diasPendientes ?? []);
  readonly diasPendientesTexto = computed(() => this.diasPendientes().map((dia) => this.fecha(dia)).join(', '));
  readonly hayMovimientos = computed(() => this.resumen().length + this.devolucionesPosteriores().length + this.bajas().length > 0);
  readonly trabajando = signal(false);
  readonly error = signal('');
  readonly aviso = signal('');

  readonly estadoTexto: Record<EstadoDia, string> = {
    PENDIENTE: 'Pendiente de dar de baja',
    DADO_DE_BAJA: 'Dado de baja · por verificar',
    VERIFICADO: 'Verificado',
  };
  readonly motivoTexto: Record<string, string> = { CADUCIDAD: 'Caducidad', DANO: 'Daño', EXTRAVIO: 'Extravío' };
  readonly resultadoTexto: Record<ResultadoVerificacion, string> = { CUADRA: 'Cuadra', FALTA: 'Falta dar de baja', DE_MAS: 'Se dio de más' };

  readonly totales = computed(() => ({
    claves: this.corte()?.totalClaves ?? 0,
    piezas: this.corte()?.totalPiezas ?? 0,
    pacientes: this.corte()?.totalPacientes ?? 0,
  }));

  readonly verificacionConResultado = computed(() => this.verificacion());
  readonly conDiferencia = computed(() => this.verificacion().filter((renglon) => renglon.resultado !== 'CUADRA').length);

  constructor() {
    this.cargar();
  }

  cambiarDia(dia: string): void {
    this.dia.set(dia);
    this.verificacion.set([]);
    this.aviso.set('');
    this.cargar();
  }

  private cargar(): void {
    if (!this.dia()) return;
    this.error.set('');
    this.api.corte(this.dia()).subscribe({
      next: (corte) => {
        this.corte.set(corte);
        this.folioIntelisis.set(corte.folioIntelisis ?? '');
      },
      error: (error) => this.error.set(mensajeDeError(error, 'No se pudo cargar el resumen del día.')),
    });
  }

  // CA4: el responsable ya capturó la baja en Intelisis y lo marca aquí.
  marcarDadoDeBaja(): void {
    if (this.trabajando()) return;
    this.trabajando.set(true);
    this.api.marcarDadoDeBaja(this.dia(), this.folioIntelisis().trim()).subscribe({
      next: (corte) => {
        this.trabajando.set(false);
        this.corte.set(corte);
        this.aviso.set(`Día ${this.fecha(corte.dia)} marcado como dado de baja en Intelisis. Cuando Intelisis se sincronice, verifícalo.`);
      },
      error: (error) => {
        this.trabajando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo marcar el día.'));
      },
    });
  }

  // HU32: compara contra lo que Intelisis tiene dado de baja (del espejo de la sincronización).
  verificar(): void {
    if (this.trabajando()) return;
    this.trabajando.set(true);
    this.api.verificar(this.dia()).subscribe({
      next: (resultado) => {
        this.trabajando.set(false);
        this.verificacion.set(resultado.renglones);
        this.sincronizadoEn.set(resultado.sincronizadoEn);
        this.corte.update((corte) => (corte ? { ...corte, estado: resultado.estado } : corte));
      },
      error: (error) => {
        this.trabajando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo verificar contra Intelisis.'));
      },
    });
  }

  descargarExcel(): void {
    this.api.csvDelCorte(this.dia()).subscribe({
      next: (archivo) => {
        const url = URL.createObjectURL(archivo);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `farmacia-intelisis-${this.dia()}.csv`;
        enlace.click();
        URL.revokeObjectURL(url);
      },
      error: (error) => this.error.set(mensajeDeError(error, 'No se pudo descargar el resumen.')),
    });
  }

  imprimir(): void {
    window.print();
  }

  fechaHora(valor: string | null): string {
    return valor ? new Date(valor).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' }) : '—';
  }

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
