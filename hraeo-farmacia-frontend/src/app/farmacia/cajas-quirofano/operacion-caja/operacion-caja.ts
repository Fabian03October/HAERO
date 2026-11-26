import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { EscanerCamara } from '../../../shared/escaner-camara/escaner-camara';
import { MarcaSeguridad } from '../../../shared/marca-seguridad/marca-seguridad';
import { SeguridadMedicamentos } from '../../../shared/seguridad-medicamentos';
import { leerGs1 } from '../../../shared/gs1';
import { mensajeDeError } from '../../../almacen/almacen-api';
import { FarmaciaApi, LoteFarmacia } from '../../farmacia-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import {
  CajaQuirofano, CajasQuirofano, EstadoBajaArea, MESES_CAMBIAR_LOTE_CAJA, MedicoIntelisis, TURNOS, Turno, UsoCaja, alertaLote, lotesEnRiesgo,
} from '../cajas-quirofano';
import { datosVale, descargarValeExcel, descargarValePdf } from '../vale-documento';
import { cargarLogo } from '../../../almacen/despacho/salidas-registradas/comprobante-salidas';
import { diaLocal } from '../../../shared/fechas';

// Medicamento que se está metiendo a la caja. codigo = código de barras escaneado (null si el
// frasco no trae código). lote = '' quiere decir "el que sale primero" (FEFO).
interface PorMeter {
  codigo: string | null;
  clave: string;
  lote: string;
  loteDelCodigo: boolean;
  piezas: number | null;
}

// Renglón de lo utilizado en la cirugía (columna "Utilizado" del vale).
interface Uso extends UsoCaja {
  id: number;
}

// Farmacia · Cajas de anestesia: todo se hace escaneando el código de la caja.
// 1) Armar o reponer: se escanea cada medicamento; si el frasco no trae código se elige del
//    listado y el lote de la lista de Farmacia. 2) Entregar: vale del turno con el médico
//    anestesiólogo. 3) Recibir: lo utilizado por paciente con la indicación médica (PDF); eso se
//    descuenta como entrega de Farmacia y lo que regresó se queda en la caja.
@Component({
  selector: 'app-operacion-caja',
  imports: [FormsModule, DatePipe, Topbar, Sidebar, EscanerCamara, MarcaSeguridad],
  templateUrl: './operacion-caja.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css', '../cajas-quirofano.css'],
})
export class OperacionCaja {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;
  readonly turnos = TURNOS;
  protected readonly cajas = inject(CajasQuirofano);
  private readonly api = inject(FarmaciaApi);
  private readonly seguridad = inject(SeguridadMedicamentos);

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'info' | 'error' | 'warn'>('info');
  readonly ocupado = signal(false);

  // ---------- Caja ----------
  codigoCaja = '';
  readonly camaraCaja = signal(false);
  readonly caja = signal<CajaQuirofano | null>(null);
  readonly renglones = computed(() => this.caja()?.renglones ?? []);
  readonly hayLotes = computed(() => this.renglones().some((r) => r.lotes.length > 0));
  // Lotes de la caja vencidos o por caducar (las cajas pasan tiempo armadas).
  readonly riesgo = computed(() => {
    const caja = this.caja();
    return caja ? lotesEnRiesgo(caja) : [];
  });
  readonly riesgoVencido = computed(() => this.riesgo().some((l) => l.alerta === 'VENCIDO'));
  readonly alerta = alertaLote;
  readonly mesesCambio = MESES_CAMBIAR_LOTE_CAJA;

  // ---------- Armar ----------
  codigoMedicamento = '';
  readonly camaraMedicamento = signal(false);
  readonly porMeter = signal<PorMeter | null>(null);
  readonly existencias = signal<LoteFarmacia[]>([]);
  private readonly campoMedicamento = viewChild<ElementRef<HTMLInputElement>>('campoMedicamento');
  readonly lotesDeFarmacia = computed(() => {
    const clave = this.porMeter()?.clave;
    return clave ? this.existencias().filter((l) => l.clave === clave && l.estado !== 'VENCIDO') : [];
  });

  // ---------- Regresar al estante ----------
  readonly sacarClave = signal('');
  readonly sacarLote = signal('');
  readonly sacarPiezas = signal<number | null>(null);
  readonly sacarMotivo = signal('');
  readonly lotesParaSacar = computed(() => this.renglones().find((r) => r.clave === this.sacarClave())?.lotes ?? []);

  // ---------- Entregar ----------
  readonly turno = signal<Turno | ''>('');
  readonly sala = signal('');
  readonly medico = signal('');
  // Usuario de Intelisis del médico si se eligió de la lista (anestesiólogos primero).
  readonly medicoUsuario = signal<string | null>(null);
  readonly medicosSugeridos = signal<MedicoIntelisis[]>([]);
  readonly revisado = signal(false);

  // ---------- Recibir ----------
  readonly usos = signal<Uso[]>([]);
  private siguienteUso = 1;
  readonly usoExpediente = signal('');
  readonly usoNombre = signal('');
  readonly usoClave = signal('');
  readonly usoPiezas = signal<number | null>(null);
  readonly buscandoPaciente = signal(false);
  readonly justificacion = signal('');
  // Una indicación médica (PDF) por paciente: expediente → archivo.
  readonly indicaciones = signal<Record<string, File>>({});
  // Checklist de devolución: por clave, 'todo' (regresó todo lo que salió) o las piezas que regresaron; null = sin revisar.
  readonly regreso = signal<Record<string, number | 'todo' | null>>({});
  // Lo que no regresó y NO fue a un paciente (se rompió o se perdió): se da de baja como extravío con su motivo.
  readonly noFueAPaciente = signal<Record<string, boolean>>({});
  readonly motivoFaltante = signal('');
  readonly pacientesUso = computed(() => {
    const vistos = new Map<string, { expediente: string; nombre: string }>();
    for (const u of this.usos()) vistos.set(u.expediente.toUpperCase(), { expediente: u.expediente, nombre: u.nombre });
    return [...vistos.values()];
  });

  readonly resumenVale = computed(() =>
    this.renglones().map((r) => {
      const utilizado = this.usos().filter((u) => u.clave === r.clave).reduce((t, u) => t + u.piezas, 0);
      const devolucion = r.tiene - utilizado;
      const marcado = this.regreso()[r.clave] ?? null;
      // "Completo" = regresó todo lo que salió (no se usó nada).
      const regreso = marcado === 'todo' ? r.tiene : marcado;
      // Lo que no regresó se aplicó a un paciente: falta decir a quién (salvo que se haya roto o perdido).
      const noRegreso = regreso === null ? 0 : Math.max(r.tiene - regreso, 0);
      const extravio = !!this.noFueAPaciente()[r.clave];
      const porAsignar = extravio ? 0 : Math.max(noRegreso - utilizado, 0);
      const faltante = extravio ? Math.max(noRegreso - utilizado, 0) : 0;
      return { ...r, utilizado, devolucion, regreso, revisado: marcado !== null, noRegreso, porAsignar, extravio, faltante };
    }),
  );
  readonly hayFaltantes = computed(() => this.resumenVale().some((r) => r.faltante > 0));

  marcarCompleto(clave: string, completo: boolean): void {
    this.regreso.update((r) => ({ ...r, [clave]: completo ? 'todo' : null }));
  }

  escribirRegreso(clave: string, valor: number | string | null): void {
    const n = valor === null || valor === '' ? null : Number(valor);
    this.regreso.update((r) => ({ ...r, [clave]: n === null || !Number.isFinite(n) ? null : n }));
  }

  marcarTodoCompleto(): void {
    this.regreso.set(Object.fromEntries(this.renglones().map((r) => [r.clave, 'todo' as const])));
    this.noFueAPaciente.set({});
  }

  marcarNoFueAPaciente(clave: string, valor: boolean): void {
    this.noFueAPaciente.update((r) => ({ ...r, [clave]: valor }));
  }

  /** Para elegir el medicamento al registrar a qué paciente se le aplicó: primero lo que falta asignar. */
  readonly porAsignar = computed(() => this.resumenVale().filter((r) => r.porAsignar > 0));

  // Tablero: cajas en quirófano (por regresar) y las que regresaron hoy.
  readonly porRegresar = computed(() =>
    this.cajas.cajas().filter((c) => c.estado === 'ENTREGADA' && c.valeAbierto)
      .sort((a, b) => a.valeAbierto!.entregadoEn.localeCompare(b.valeAbierto!.entregadoEn)));
  readonly regresaronHoy = computed(() =>
    this.cajas.cajas().map((c) => ({ caja: c, vale: c.vales.find((v) => v.recibidoEn?.startsWith(this.hoy)) }))
      .filter((x) => x.vale && x.caja.estado !== 'ENTREGADA'));

  abrirDevolucion(codigo: string): void {
    this.codigoCaja = codigo;
    this.escanearCaja();
    setTimeout(() => document.getElementById('recibir-caja')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 600);
  }
  readonly faltanParaRecibir = computed(() => {
    const faltan: string[] = [];
    if (this.resumenVale().some((r) => r.devolucion < 0)) faltan.push('corregir lo utilizado (es más de lo que salió)');
    const sinRevisar = this.resumenVale().filter((r) => !r.revisado).length;
    if (sinRevisar) faltan.push(`revisar en el checklist ${sinRevisar} medicamento(s)`);
    if (this.resumenVale().some((r) => r.regreso !== null && r.regreso > r.tiene)) faltan.push('corregir lo que regresó (es más de lo que salió)');
    if (this.resumenVale().some((r) => r.regreso !== null && r.utilizado > r.noRegreso)) faltan.push('corregir lo utilizado (es más de lo que no regresó)');
    const sinPaciente = this.porAsignar();
    if (sinPaciente.length) faltan.push(`decir a qué paciente se aplicó: ${sinPaciente.map((r) => `${r.porAsignar} de ${r.clave}`).join(', ')}`);
    if (this.hayFaltantes() && !this.motivoFaltante().trim()) faltan.push('qué pasó con lo que se rompió o se perdió');
    if (this.usos().length) {
      if (!this.justificacion().trim()) faltan.push('la justificación');
      const sinPdf = this.pacientesUso().filter((p) => !this.indicaciones()[p.expediente]);
      if (sinPdf.length) faltan.push(`la indicación médica en PDF de ${sinPdf.map((p) => p.nombre).join(', ')}`);
    }
    return faltan;
  });

  private readonly hoy = diaLocal();

  constructor() {
    this.seguridad.cargar();
    this.cargarBajaDia();
    this.cajas.cargar().subscribe({ error: () => undefined });
  }

  // ---------- Caja ----------
  escanearCaja(): void {
    const codigo = this.codigoCaja.trim().toUpperCase();
    if (!codigo) return this.mostrar('Escanea el código de la caja.', 'error');
    this.ocupado.set(true);
    this.cajas.detalle(codigo).subscribe({
      next: (caja) => {
        this.ocupado.set(false);
        this.codigoCaja = '';
        this.abrir(caja);
        this.mensaje.set('');
      },
      error: (e) => {
        this.ocupado.set(false);
        this.mostrar(mensajeDeError(e, 'No se encontró la caja.'), 'error');
      },
    });
  }

  cajaDesdeCamara(codigo: string): void {
    this.camaraCaja.set(false);
    this.codigoCaja = codigo;
    this.escanearCaja();
  }

  private abrir(caja: CajaQuirofano): void {
    this.caja.set(caja);
    this.porMeter.set(null);
    this.turno.set('');
    this.sala.set(caja.servicio);
    this.medico.set('');
    this.revisado.set(false);
    this.usos.set([]);
    this.justificacion.set('');
    this.indicaciones.set({});
    this.medicoUsuario.set(null);
    this.medicosSugeridos.set([]);
    this.regreso.set({});
    this.noFueAPaciente.set({});
    this.motivoFaltante.set('');
    this.cargarExistencias();
  }

  private cargarExistencias(): void {
    this.api.existencias().subscribe({ next: (lista) => this.existencias.set(lista), error: () => this.existencias.set([]) });
  }

  // ---------- Armar ----------
  escanearMedicamento(): void {
    const caja = this.caja();
    const codigo = this.codigoMedicamento.trim();
    if (!caja || !codigo) return;
    this.codigoMedicamento = '';
    const gs1 = leerGs1(codigo);
    if (gs1?.caducidad && gs1.caducidad < this.hoy) {
      return this.mostrar(`El lote ${gs1.lote ?? ''} está vencido: no se mete a la caja.`, 'error');
    }
    const llave = gs1?.gtin ?? codigo;
    const lote = gs1?.lote ?? '';
    this.cajas.claveDelCodigo(llave).subscribe({
      next: ({ clave }) => {
        if (!this.renglones().some((r) => r.clave === clave)) {
          return this.mostrar(`Ese medicamento (${clave}) no está en el listado de la caja.`, 'error');
        }
        this.nuevoPorMeter(llave, clave, lote);
        this.mostrar('Confirma el lote y las piezas.', 'info');
      },
      error: (e: HttpErrorResponse) => {
        if (e.status !== 404) return this.mostrar(mensajeDeError(e), 'error');
        this.nuevoPorMeter(llave, '', lote);
        this.mostrar('Código nuevo: elige a qué medicamento del listado corresponde; la próxima vez se reconoce solo.', 'info');
      },
    });
  }

  /** Frasco o ampolleta sin código de barras: se elige del listado y del lote de Farmacia. */
  sinCodigo(): void {
    const unico = this.renglones().filter((r) => r.falta > 0);
    this.nuevoPorMeter(null, unico.length === 1 ? unico[0].clave : '', '');
    this.mostrar('Elige el medicamento del listado y el lote que tomaste del estante.', 'info');
  }

  medicamentoDesdeCamara(codigo: string): void {
    this.camaraMedicamento.set(false);
    this.codigoMedicamento = codigo;
    this.escanearMedicamento();
  }

  private nuevoPorMeter(codigo: string | null, clave: string, lote: string): void {
    // Como en todas las pantallas: lo que falta, hasta una caja completa.
    this.porMeter.set({ codigo, clave, lote, loteDelCodigo: !!lote, piezas: this.cajaHastaLoQueFalta(clave) });
  }

  /** Lo que falta de esa clave, hasta una caja completa; null si la clave no está en el contenido (se escribe a mano). */
  private cajaHastaLoQueFalta(clave: string): number | null {
    const falta = this.faltaDe(clave);
    return falta === null ? null : Math.max(1, Math.min(this.seguridad.piezasPorCaja(clave), falta));
  }

  private faltaDe(clave: string): number | null {
    return this.renglones().find((r) => r.clave === clave)?.falta || null;
  }

  cambiarPorMeter(cambios: Partial<PorMeter>): void {
    this.porMeter.update((actual) => {
      if (!actual) return actual;
      const nuevo = { ...actual, ...cambios };
      if (cambios.clave !== undefined) {
        nuevo.piezas = this.faltaDe(cambios.clave);
        if (!actual.loteDelCodigo) nuevo.lote = '';
      }
      return nuevo;
    });
  }

  meter(): void {
    const caja = this.caja();
    const pieza = this.porMeter();
    if (!caja || !pieza || this.ocupado()) return;
    if (!pieza.clave) return this.mostrar('Elige el medicamento del listado.', 'error');
    if (this.seguridad.marcas(pieza.clave).redFria) return this.mostrar(`${pieza.clave} es de red fría: no se guarda en la caja.`, 'error');
    if (!pieza.piezas || pieza.piezas <= 0 || !Number.isInteger(pieza.piezas)) return this.mostrar('Escribe las piezas que metes.', 'error');
    this.ocupado.set(true);
    this.cajas.armar(caja.codigo, pieza.clave, pieza.lote || null, pieza.piezas, pieza.codigo).subscribe({
      next: (actualizada) => {
        this.ocupado.set(false);
        this.caja.set(actualizada);
        this.porMeter.set(null);
        this.cargarExistencias();
        this.mostrar(actualizada.piezasFaltantes ? `Listo. A la caja le faltan ${actualizada.piezasFaltantes} piezas.` : 'La caja quedó completa: ya se puede entregar.', 'info');
        this.campoMedicamento()?.nativeElement.focus();
      },
      error: (e) => {
        this.ocupado.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudo meter a la caja.'), 'error');
      },
    });
  }

  // ---------- Regresar al estante ----------
  sacar(): void {
    const caja = this.caja();
    const piezas = this.sacarPiezas();
    if (!caja || this.ocupado()) return;
    if (!this.sacarClave() || !this.sacarLote()) return this.mostrar('Elige la clave y el lote que sacas.', 'error');
    if (!piezas || piezas <= 0) return this.mostrar('Escribe las piezas que regresas al estante.', 'error');
    if (!this.sacarMotivo().trim()) return this.mostrar('Escribe por qué las sacas (ej. lote por caducar).', 'error');
    this.ocupado.set(true);
    this.cajas.sacar(caja.codigo, this.sacarClave(), this.sacarLote(), piezas, this.sacarMotivo().trim()).subscribe({
      next: (actualizada) => {
        this.ocupado.set(false);
        this.caja.set(actualizada);
        this.sacarClave.set('');
        this.sacarLote.set('');
        this.sacarPiezas.set(null);
        this.sacarMotivo.set('');
        this.cargarExistencias();
        this.mostrar('Las piezas regresaron al estante de Farmacia.', 'info');
      },
      error: (e) => {
        this.ocupado.set(false);
        this.mostrar(mensajeDeError(e), 'error');
      },
    });
  }

  // ---------- Entregar ----------
  entregar(): void {
    const caja = this.caja();
    const turno = this.turno();
    if (!caja || caja.estado !== 'COMPLETA' || this.ocupado()) return;
    if (!turno) return this.mostrar('Elige el turno del vale.', 'error');
    if (!this.medico().trim() || !this.sala().trim()) return this.mostrar('Escribe el médico anestesiólogo y la sala.', 'error');
    if (!this.revisado()) return this.mostrar('Revisa el contenido con el médico y marca la casilla.', 'error');
    this.ocupado.set(true);
    this.cajas.entregar(caja.codigo, turno, this.sala().trim(), this.medico().trim(), this.medicoUsuario()).subscribe({
      next: (actualizada) => {
        this.ocupado.set(false);
        this.abrir(actualizada);
        this.mostrar(`Caja ${actualizada.codigo} entregada a ${actualizada.valeAbierto?.medico}.`, 'info');
      },
      error: (e) => {
        this.ocupado.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudo entregar la caja.'), 'error');
      },
    });
  }

  /** Al escribir el nombre se sugieren médicos de Intelisis; si no está, se queda el nombre escrito. */
  escribirMedico(texto: string): void {
    this.medico.set(texto);
    this.medicoUsuario.set(null);
    if (texto.trim().length < 3) {
      this.medicosSugeridos.set([]);
      return;
    }
    this.cajas.medicos(texto.trim()).subscribe({ next: (lista) => this.medicosSugeridos.set(lista), error: () => this.medicosSugeridos.set([]) });
  }

  elegirMedico(medico: MedicoIntelisis): void {
    this.medico.set(medico.nombre);
    this.medicoUsuario.set(medico.usuario);
    this.medicosSugeridos.set([]);
  }

  // ---------- Recibir ----------
  buscarPaciente(): void {
    const expediente = this.usoExpediente().trim();
    if (!expediente) return this.mostrar('Escribe el expediente del paciente.', 'error');
    this.buscandoPaciente.set(true);
    this.api.buscarPacientes('EXPEDIENTE', expediente).subscribe({
      next: (lista) => {
        this.buscandoPaciente.set(false);
        const paciente = lista.find((p) => p.expediente?.toUpperCase() === expediente.toUpperCase()) ?? lista[0];
        if (!paciente) return this.mostrar(`El expediente ${expediente} no existe en Intelisis.`, 'warn');
        this.usoNombre.set(paciente.nombre);
        this.mensaje.set('');
      },
      error: (e) => {
        this.buscandoPaciente.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudo consultar Intelisis.'), 'error');
      },
    });
  }

  agregarUso(): void {
    const piezas = this.usoPiezas();
    if (!this.usoExpediente().trim() || !this.usoNombre().trim()) return this.mostrar('Busca al paciente por su expediente.', 'error');
    if (!this.usoClave()) return this.mostrar('Elige el medicamento utilizado.', 'error');
    if (!piezas || piezas <= 0 || !Number.isInteger(piezas)) return this.mostrar('Escribe las piezas utilizadas.', 'error');
    this.usos.update((lista) => [
      ...lista,
      { id: this.siguienteUso++, expediente: this.usoExpediente().trim(), nombre: this.usoNombre().trim(), clave: this.usoClave(), piezas },
    ]);
    this.usoClave.set('');
    this.usoPiezas.set(null);
    this.mensaje.set('');
  }

  quitarUso(id: number): void {
    this.usos.update((lista) => lista.filter((u) => u.id !== id));
  }

  otroPaciente(): void {
    this.usoExpediente.set('');
    this.usoNombre.set('');
  }

  elegirArchivo(expediente: string, evento: Event): void {
    const archivo = (evento.target as HTMLInputElement).files?.[0] ?? null;
    this.indicaciones.update((actual) => {
      const nuevo = { ...actual };
      delete nuevo[expediente];
      if (archivo && archivo.type === 'application/pdf') nuevo[expediente] = archivo;
      return nuevo;
    });
    if (archivo && archivo.type !== 'application/pdf') this.mostrar('La indicación médica debe ser un archivo PDF.', 'error');
  }

  recibir(): void {
    const caja = this.caja();
    if (!caja || caja.estado !== 'ENTREGADA' || this.faltanParaRecibir().length || this.ocupado()) return;
    const usos = this.usos().map(({ expediente, nombre, clave, piezas }) => ({ expediente, nombre, clave, piezas }));
    const total = usos.reduce((t, u) => t + u.piezas, 0);
    this.ocupado.set(true);
    // Solo los PDF de pacientes que siguen en la lista de lo utilizado.
    const pacientes = new Set(this.pacientesUso().map((p) => p.expediente));
    const indicaciones = Object.fromEntries(Object.entries(this.indicaciones()).filter(([expediente]) => pacientes.has(expediente)));
    const devueltos = this.resumenVale().map((r) => ({ clave: r.clave, piezas: r.regreso ?? 0 }));
    const faltaron = this.resumenVale().filter((r) => r.faltante > 0).map((r) => `${r.clave} (${r.faltante})`);
    this.cajas.recibir(caja.codigo, usos, this.justificacion().trim(), indicaciones, devueltos, this.hayFaltantes() ? this.motivoFaltante().trim() : '').subscribe({
      next: (actualizada) => {
        this.ocupado.set(false);
        this.abrir(actualizada);
        this.cargarBajaDia();
        this.cajas.cargar().subscribe();
        this.mostrar(
          (total
            ? `Caja recibida. Se registraron ${total} piezas utilizadas como entrega de Farmacia; repón la caja antes de volver a entregarla.`
            : 'Caja recibida completa.')
            + (faltaron.length ? ` No regresó: ${faltaron.join(', ')}; quedó como extravío y hay que reponerlo.` : ''),
          faltaron.length ? 'error' : 'info',
        );
      },
      error: (e) => {
        this.ocupado.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudo recibir la caja.'), 'error');
      },
    });
  }

  verIndicacion(valeId: number, expediente: string): void {
    this.cajas.indicacion(valeId, expediente).subscribe({
      next: (pdf) => window.open(URL.createObjectURL(pdf), '_blank'),
      error: (e) => this.mostrar(mensajeDeError(e, 'No se pudo abrir la indicación médica.'), 'error'),
    });
  }

  /** Abre el vale para imprimir: el de una salida o, sin vale, uno en blanco con lo que trae la caja. */
  imprimirVale(valeId?: number): void {
    const caja = this.caja();
    if (!caja) return;
    const parametros = new URLSearchParams({ caja: caja.codigo, ...(valeId ? { vale: String(valeId) } : {}) });
    window.open(`/farmacia/quirofano/vale?${parametros}`, '_blank');
  }

  async valePdf(valeId?: number): Promise<void> {
    const caja = this.caja();
    if (!caja) return;
    descargarValePdf(datosVale(caja, caja.vales.find((v) => v.id === valeId) ?? null), await cargarLogo());
  }

  valeExcel(valeId?: number): void {
    const caja = this.caja();
    if (caja) descargarValeExcel(datosVale(caja, caja.vales.find((v) => v.id === valeId) ?? null));
  }

  // ---------- Baja del día (como en Hospitalización) ----------
  readonly bajaDia = signal<EstadoBajaArea | null>(null);
  readonly terminando = signal(false);

  cargarBajaDia(): void {
    this.cajas.estadoBaja('QUIROFANO').subscribe({ next: (estado) => this.bajaDia.set(estado), error: () => this.bajaDia.set(null) });
  }

  terminarDia(): void {
    if (this.terminando()) return;
    this.terminando.set(true);
    this.cajas.terminarDia('QUIROFANO').subscribe({
      next: (hojas) => {
        this.terminando.set(false);
        this.cargarBajaDia();
        if (hojas[0]) this.imprimirBaja(hojas[0].folio);
      },
      error: (e) => {
        this.terminando.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudo generar la baja del día.'), 'error');
      },
    });
  }

  imprimirBaja(folio?: number | null): void {
    const parametros = new URLSearchParams({ central: 'QUIROFANO', volumen: '1', ...(folio ? { folio: String(folio) } : {}) });
    window.open(`/farmacia/hospitalizacion/imprimir/bajas?${parametros}`, '_blank');
  }

  imprimirHojasPacientes(): void {
    window.open('/farmacia/hospitalizacion/imprimir/bajas?central=QUIROFANO', '_blank');
  }

  turnoEtiqueta(turno: string): string {
    return this.turnos.find((t) => t.valor === turno)?.etiqueta ?? turno;
  }

  private mostrar(texto: string, tipo: 'info' | 'error' | 'warn'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
