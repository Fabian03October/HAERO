import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { EscanerCamara } from '../../../shared/escaner-camara/escaner-camara';
import { leerGs1 } from '../../../shared/gs1';
import { HttpErrorResponse } from '@angular/common/http';
import { Medicamento, mensajeDeError } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { BuscadorMedicamento } from '../../buscador-medicamento/buscador-medicamento';
import { FarmaciaApi, LoteFarmacia, Receta, RecetaIntelisis } from '../../farmacia-api';

// Clave indicada en la receta, con lo que se le va entregando (HU28, HU29).
interface ClaveReceta {
  clave: string;
  medicamento: string;
  piezasPorCaja: number | null;
  indicada: number;
  // HU29: si no hay existencia, la clave se marca como no dispensada con su motivo.
  noDispensada: boolean;
  motivo: string;
}

// Caja escaneada para la entrega (HU28, HU30).
interface CajaEscaneada {
  id: number;
  codigo: string;
  gtin: string;
  lote: string;
  caducidad: string;
  clave: string;
  piezas: number;
}

type EstadoClave = 'COMPLETA' | 'PARCIAL' | 'NO_DISPENSADA' | 'SIN_ENTREGAR';

// Hay tres tipos de receta: unas traen nombre y expediente, otras la CURP y otras solo el
// nombre; por eso el paciente se busca de tres formas.
type ModoPaciente = 'EXPEDIENTE' | 'NOMBRE' | 'CURP' | 'FOLIO';

// Paciente leído de Intelisis (solo lectura); cada receta trae unos datos u otros.
interface Paciente {
  nombre: string;
  expediente: string | null;
  curp: string | null;
}

// Formato oficial de la CURP: 18 caracteres.
const PATRON_CURP = /^[A-Z][AEIOUX][A-Z]{2}\d{6}[HM][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[0-9A-Z]\d$/;
// Letras mínimas para buscar por nombre.
const MINIMO_NOMBRE = 3;

// Farmacia · Nueva entrega (HU28 a HU30).
// - El paciente se busca en Intelisis en solo lectura (por expediente, nombre o CURP).
// - Cada caja se escanea: con código GS1 se toma su lote; sin él, el backend asigna el lote
//   que sale primero (FEFO) y bloquea uno posterior o vencido (HU30).
// - Con ?receta=FOLIO se completa una receta parcial (HU29): se cargan sus claves pendientes.
// La clave de cada caja la elige el despachador entre las de la receta, porque el rol
// Farmacia no puede consultar los códigos de barras de Almacén.
@Component({
  selector: 'app-nueva-entrega',
  imports: [FormsModule, Topbar, Sidebar, EscanerCamara, BuscadorMedicamento],
  templateUrl: './nueva-entrega.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css', './nueva-entrega.css'],
})
export class NuevaEntrega {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;
  private readonly api = inject(FarmaciaApi);
  readonly buscandoPaciente = signal(false);
  // Intelisis no respondió: el paciente se captura a mano con los datos de la receta.
  readonly sinIntelisis = signal(false);
  readonly nombreManual = signal('');
  readonly guardando = signal(false);
  // Receta parcial que se está completando (HU29); sus claves no se cambian.
  readonly recetaExistente = signal<Receta | null>(null);
  // Lote que sale primero de cada clave de la receta (HU30 CA1).
  readonly fefo = signal<Record<string, LoteFarmacia | null>>({});

  // ---------- Paciente y receta ----------
  readonly modoPaciente = signal<ModoPaciente>('EXPEDIENTE');
  readonly busquedaPaciente = signal('');
  readonly paciente = signal<Paciente | null>(null);
  // Al buscar por nombre puede haber varios pacientes que se llamen igual.
  readonly coincidencias = signal<Paciente[]>([]);
  readonly folioReceta = signal('');
  readonly avisoPaciente = signal('');

  readonly modosPaciente: { valor: ModoPaciente; etiqueta: string; placeholder: string }[] = [
    { valor: 'EXPEDIENTE', etiqueta: 'Expediente', placeholder: 'Escribe o escanea el número de expediente' },
    { valor: 'NOMBRE', etiqueta: 'Nombre', placeholder: 'Nombre y apellidos del paciente' },
    { valor: 'CURP', etiqueta: 'CURP', placeholder: 'CURP de 18 caracteres' },
    { valor: 'FOLIO', etiqueta: 'Folio de receta', placeholder: 'Folio interno o folio AAMATES de la receta (6 dígitos)' },
  ];
  // Recetas firmadas del paciente en Intelisis: al elegir una se llenan el folio y las claves.
  readonly recetasIntelisis = signal<RecetaIntelisis[]>([]);
  readonly cargandoRecetas = signal(false);
  readonly placeholderPaciente = computed(() => this.modosPaciente.find((modo) => modo.valor === this.modoPaciente())!.placeholder);

  cantidadIndicada = 0;
  readonly claves = signal<ClaveReceta[]>([]);
  readonly clavesEnReceta = computed(() => this.claves().map((item) => item.clave));
  readonly elegida = signal<Medicamento | null>(null);

  // ---------- Cajas escaneadas ----------
  codigoEscaneado = '';
  readonly camaraAbierta = signal(false);
  readonly cajas = signal<CajaEscaneada[]>([]);
  private siguienteId = 1;
  private readonly campoCodigo = viewChild<ElementRef<HTMLInputElement>>('campoCodigo');

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'info' | 'error' | 'warn'>('info');

  // Cada clave de la receta con lo entregado y su estado (HU29).
  readonly resumen = computed(() =>
    this.claves().map((item) => {
      const entregada = this.cajas()
        .filter((caja) => caja.clave === item.clave)
        .reduce((total, caja) => total + (caja.piezas || 0), 0);
      let estado: EstadoClave = 'SIN_ENTREGAR';
      if (item.noDispensada) estado = 'NO_DISPENSADA';
      else if (entregada >= item.indicada) estado = 'COMPLETA';
      else if (entregada > 0) estado = 'PARCIAL';
      return { ...item, entregada, pendiente: Math.max(0, item.indicada - entregada), estado, excede: entregada > item.indicada };
    }),
  );

  readonly estadoReceta = computed<EstadoClave | null>(() => {
    const lista = this.resumen();
    if (!lista.length) return null;
    if (lista.every((item) => item.estado === 'COMPLETA')) return 'COMPLETA';
    if (lista.every((item) => item.estado === 'NO_DISPENSADA')) return 'NO_DISPENSADA';
    if (lista.some((item) => item.estado === 'COMPLETA' || item.estado === 'PARCIAL')) return 'PARCIAL';
    return 'SIN_ENTREGAR';
  });

  // Lo que falta para poder confirmar, en el orden en que se captura.
  readonly faltantes = computed(() => {
    const faltan: string[] = [];
    if (!this.paciente()) faltan.push('buscar y elegir al paciente por expediente, nombre o CURP');
    if (!this.folioReceta().trim()) faltan.push('el folio de la receta');
    if (!this.claves().length) faltan.push('al menos una clave de la receta');
    if (this.resumen().some((item) => item.excede)) faltan.push('corregir las claves con más piezas de las indicadas');
    if (this.resumen().some((item) => item.noDispensada && !item.motivo.trim())) faltan.push('el motivo de las claves no dispensadas');
    if (this.cajas().some((caja) => !caja.clave || !(caja.piezas > 0))) faltan.push('la clave y las piezas de cada caja');
    if (!this.cajas().length && !this.claves().some((item) => item.noDispensada)) faltan.push('escanear al menos una caja o marcar las claves no dispensadas');
    return faltan;
  });

  readonly estadoTexto: Record<EstadoClave, string> = {
    COMPLETA: 'Completa',
    PARCIAL: 'Parcial',
    NO_DISPENSADA: 'No dispensada',
    SIN_ENTREGAR: 'Sin entregar',
  };

  constructor() {
    // Desde Recetas parciales (HU29) se llega con el folio para completar esa receta.
    const folio = inject(ActivatedRoute).snapshot.queryParamMap.get('receta');
    if (folio) this.cargarReceta(folio);
  }

  private cargarReceta(folio: string): void {
    this.api.receta(folio).subscribe({
      next: (receta) => {
        this.recetaExistente.set(receta);
        this.folioReceta.set(receta.folio);
        this.paciente.set({ nombre: receta.paciente, expediente: receta.expediente, curp: receta.curp });
        // Solo lo pendiente de cada clave: eso es lo que falta entregar.
        this.claves.set(
          receta.claves
            .filter((clave) => clave.pendiente > 0)
            .map((clave) => ({
              clave: clave.clave,
              medicamento: clave.medicamento,
              piezasPorCaja: clave.piezasPorCaja,
              indicada: clave.pendiente,
              noDispensada: clave.noDispensada,
              motivo: clave.motivo ?? '',
            })),
        );
        this.claves().forEach((clave) => this.cargarFefo(clave.clave));
        this.mostrarMensaje(`Completando la receta ${receta.folio}: se muestran solo las piezas pendientes de cada clave.`, 'info');
      },
      error: (error) => this.mostrarMensaje(mensajeDeError(error, `No se pudo cargar la receta ${folio}.`), 'error'),
    });
  }

  private cargarFefo(clave: string): void {
    this.api.loteFefo(clave).subscribe({
      next: (lote) => this.fefo.update((actual) => ({ ...actual, [clave]: lote })),
      error: () => this.fefo.update((actual) => ({ ...actual, [clave]: null })),
    });
  }

  // Lotes que salen primero de las claves de la receta, para la nota FEFO.
  readonly lotesFefo = computed(() =>
    this.claves().map((item) => ({ clave: item.clave, lote: this.fefo()[item.clave] })),
  );

  cambiarModoPaciente(modo: ModoPaciente): void {
    this.modoPaciente.set(modo);
    this.busquedaPaciente.set('');
    this.coincidencias.set([]);
    this.avisoPaciente.set('');
  }

  cambiarBusquedaPaciente(valor: string): void {
    // La CURP siempre va en mayúsculas.
    this.busquedaPaciente.set(this.modoPaciente() === 'CURP' ? valor.toUpperCase() : valor);
  }

  buscarPaciente(): void {
    const texto = this.busquedaPaciente().trim();
    const modo = this.modoPaciente();
    if (!texto) {
      this.avisoPaciente.set(this.placeholderPaciente() + '.');
      return;
    }
    if (modo === 'CURP' && !PATRON_CURP.test(texto)) {
      this.avisoPaciente.set(`La CURP no tiene el formato correcto (18 caracteres, por ejemplo GOMA850312MOCRRN09). Lleva ${texto.length}.`);
      return;
    }
    if (modo === 'NOMBRE' && texto.replace(/\s/g, '').length < MINIMO_NOMBRE) {
      this.avisoPaciente.set(`Escribe al menos ${MINIMO_NOMBRE} letras del nombre.`);
      return;
    }
    if (modo === 'FOLIO') {
      this.buscarPorFolio(texto);
      return;
    }
    // HU28 CA1: el paciente se lee de Intelisis en solo lectura. Por nombre puede haber varios.
    this.buscandoPaciente.set(true);
    this.avisoPaciente.set('Buscando en Intelisis…');
    this.coincidencias.set([]);
    this.api.buscarPacientes(modo, texto).subscribe({
      next: (lista) => {
        this.buscandoPaciente.set(false);
        if (lista.length === 1) {
          this.elegirPaciente(lista[0]);
        } else if (lista.length > 1) {
          this.coincidencias.set(lista);
          this.avisoPaciente.set(`${lista.length} pacientes coinciden${lista.length === 20 ? ' (se muestran los primeros 20; escribe más datos)' : ''}. Elige el de la receta.`);
        } else {
          this.avisoPaciente.set('No se encontró ningún paciente con ese dato en Intelisis.');
        }
      },
      error: (error) => {
        this.buscandoPaciente.set(false);
        this.sinIntelisis.set(error instanceof HttpErrorResponse && error.status === 503);
        this.avisoPaciente.set(mensajeDeError(error, 'No se pudo buscar al paciente en Intelisis.'));
      },
    });
  }

  // Sin Intelisis: se usan los datos de la receta tal como vienen.
  usarPacienteManual(): void {
    const nombre = this.nombreManual().trim();
    if (!nombre) {
      this.avisoPaciente.set('Escribe el nombre del paciente como viene en la receta.');
      return;
    }
    const texto = this.busquedaPaciente().trim();
    this.elegirPaciente({
      nombre,
      expediente: this.modoPaciente() === 'EXPEDIENTE' && texto ? texto : null,
      curp: this.modoPaciente() === 'CURP' && texto ? texto : null,
    });
    this.sinIntelisis.set(false);
    this.nombreManual.set('');
  }

  elegirPaciente(paciente: Paciente): void {
    this.paciente.set(paciente);
    this.coincidencias.set([]);
    this.avisoPaciente.set('');
    if (paciente.expediente && !this.recetaExistente()) this.cargarRecetasIntelisis(paciente.expediente);
  }

  private cargarRecetasIntelisis(expediente: string): void {
    this.cargandoRecetas.set(true);
    this.recetasIntelisis.set([]);
    this.api.recetasDelPaciente(expediente).subscribe({
      next: (lista) => {
        this.cargandoRecetas.set(false);
        this.recetasIntelisis.set(lista);
      },
      error: () => this.cargandoRecetas.set(false),
    });
  }

  private buscarPorFolio(folio: string): void {
    this.buscandoPaciente.set(true);
    this.avisoPaciente.set('Buscando la receta en Intelisis…');
    this.api.recetaDeIntelisis(folio).subscribe({
      next: (receta) => {
        this.buscandoPaciente.set(false);
        this.avisoPaciente.set('');
        this.usarRecetaIntelisis(receta);
      },
      error: (error) => {
        this.buscandoPaciente.set(false);
        this.sinIntelisis.set(error instanceof HttpErrorResponse && error.status === 503);
        this.avisoPaciente.set(mensajeDeError(error, 'No se pudo buscar la receta en Intelisis.'));
      },
    });
  }

  // Llena paciente, folio y claves con la receta de Intelisis; si ya está en HAERO, se completa.
  usarRecetaIntelisis(receta: RecetaIntelisis): void {
    if (!receta.folio) return this.mostrarMensaje('Esa receta no tiene folio en Intelisis; captúrala a mano.', 'error');
    if (receta.estadoEnHaero === 'SURTIDA') return this.mostrarMensaje(`La receta ${receta.folio} ya se surtió por completo en Farmacia.`, 'warn');
    if (receta.estadoEnHaero) {
      this.cargarReceta(receta.folio);
      return;
    }
    if (!this.paciente()) this.paciente.set(receta.paciente);
    this.recetasIntelisis.set([]);
    this.folioReceta.set(receta.folio);
    // Si una clave viene en varios renglones, se suma lo indicado.
    const porClave = new Map<string, ClaveReceta>();
    for (const partida of receta.partidas.filter((p) => p.enCatalogo && p.clave)) {
      const actual = porClave.get(partida.clave!);
      if (actual) actual.indicada += partida.cantidad;
      else porClave.set(partida.clave!, { clave: partida.clave!, medicamento: partida.medicamento, piezasPorCaja: partida.piezasPorCaja, indicada: partida.cantidad, noDispensada: false, motivo: '' });
    }
    this.claves.set([...porClave.values()]);
    this.claves().forEach((clave) => this.cargarFefo(clave.clave));
    const sinClave = receta.partidas.filter((p) => !p.enCatalogo).map((p) => p.medicamento);
    this.mostrarMensaje(
      `Receta ${receta.folio} cargada de Intelisis con ${porClave.size} ${porClave.size === 1 ? 'clave' : 'claves'}. Revisa las piezas indicadas.` +
        (sinClave.length ? ` Sin clave del catálogo (agrégalas a mano si se surten): ${sinClave.join(', ')}.` : ''),
      sinClave.length ? 'warn' : 'info',
    );
  }

  cambiarPaciente(): void {
    if (this.recetaExistente()) return;
    this.recetasIntelisis.set([]);
    this.paciente.set(null);
    this.busquedaPaciente.set('');
  }

  // ---------- Claves de la receta ----------
  elegirMedicamento(medicamento: Medicamento): void {
    this.elegida.set(medicamento);
  }

  agregarClave(): void {
    const medicamento = this.elegida();
    if (!medicamento) {
      this.mostrarMensaje('Busca y elige la clave de la receta.', 'error');
      return;
    }
    if (this.recetaExistente()) {
      this.mostrarMensaje('Al completar una receta no se agregan claves nuevas.', 'error');
      return;
    }
    if (!Number.isInteger(this.cantidadIndicada) || this.cantidadIndicada <= 0) {
      this.mostrarMensaje('La cantidad indicada debe ser un número entero de piezas mayor a cero.', 'error');
      return;
    }
    this.claves.update((lista) => [
      ...lista,
      {
        clave: medicamento.clave,
        medicamento: medicamento.nombreGenerico,
        piezasPorCaja: medicamento.piezasPorCaja,
        indicada: this.cantidadIndicada,
        noDispensada: false,
        motivo: '',
      },
    ]);
    this.cargarFefo(medicamento.clave);
    this.elegida.set(null);
    this.cantidadIndicada = 0;
    this.mensaje.set('');
  }

  quitarClave(clave: string): void {
    this.claves.update((lista) => lista.filter((item) => item.clave !== clave));
    // Las cajas de esa clave se quedan para asignarlas a otra.
    this.cajas.update((lista) => lista.map((caja) => (caja.clave === clave ? { ...caja, clave: '' } : caja)));
  }

  marcarNoDispensada(clave: string, valor: boolean): void {
    this.claves.update((lista) => lista.map((item) => (item.clave === clave ? { ...item, noDispensada: valor, motivo: valor ? item.motivo : '' } : item)));
    if (valor) this.cajas.update((lista) => lista.filter((caja) => caja.clave !== clave));
  }

  cambiarMotivo(clave: string, motivo: string): void {
    this.claves.update((lista) => lista.map((item) => (item.clave === clave ? { ...item, motivo } : item)));
  }

  // ---------- Cajas ----------
  escanear(): void {
    const codigo = this.codigoEscaneado.trim();
    if (!codigo) {
      this.mostrarMensaje('Escanea o captura el código de barras de la caja.', 'error');
      return;
    }
    const gs1 = leerGs1(codigo);
    if (!gs1?.lote) {
      this.mostrarMensaje('Este código no trae lote (no es GS1): al guardar se toma el lote que sale primero (FEFO). Si conoces el lote, escríbelo en el renglón.', 'warn');
    } else if (gs1.caducidad && gs1.caducidad < this.hoy()) {
      // HU30 CA4: un lote vencido no se entrega.
      this.mostrarMensaje(`El lote ${gs1.lote} venció el ${this.fecha(gs1.caducidad)}. No se puede entregar; apártalo para darlo de baja (HU35).`, 'error');
      this.limpiarCodigo();
      return;
    } else {
      this.mensaje.set('');
    }

    // Con una sola clave pendiente, la caja se le asigna sola.
    const pendientes = this.resumen().filter((item) => item.estado !== 'COMPLETA' && item.estado !== 'NO_DISPENSADA');
    this.cajas.update((lista) => [
      ...lista,
      {
        id: this.siguienteId++,
        codigo,
        gtin: gs1?.gtin ?? codigo,
        lote: gs1?.lote ?? '',
        caducidad: gs1?.caducidad ?? '',
        clave: pendientes.length === 1 ? pendientes[0].clave : '',
        piezas: 0,
      },
    ]);
    this.limpiarCodigo();
  }

  codigoDesdeCamara(codigo: string): void {
    this.camaraAbierta.set(false);
    this.codigoEscaneado = codigo;
    this.escanear();
  }

  actualizarCaja(id: number, cambios: Partial<CajaEscaneada>): void {
    this.cajas.update((lista) => lista.map((caja) => (caja.id === id ? { ...caja, ...cambios } : caja)));
  }

  quitarCaja(id: number): void {
    this.cajas.update((lista) => lista.filter((caja) => caja.id !== id));
  }

  // HU28: guarda la entrega. El backend asigna los lotes, descuenta el inventario y deja la
  // receta surtida, parcial o no dispensada.
  confirmar(): void {
    const paciente = this.paciente();
    if (!paciente || this.faltantes().length || this.guardando()) return;
    this.guardando.set(true);
    const completando = !!this.recetaExistente();
    this.api
      .registrarEntrega({
        folioReceta: this.folioReceta().trim(),
        paciente: completando ? undefined : paciente,
        claves: this.claves()
          .filter((item) => !completando || item.noDispensada)
          .map((item) => ({
            clave: item.clave,
            piezasIndicadas: completando ? undefined : item.indicada,
            noDispensada: item.noDispensada,
            motivo: item.motivo.trim() || undefined,
          })),
        cajas: this.cajas().map((caja) => ({ clave: caja.clave, lote: caja.lote.trim() || undefined, piezas: caja.piezas })),
      })
      .subscribe({
        next: (resultado) => {
          this.guardando.set(false);
          const lotes = resultado.partidas.map((p) => `${p.lote} (${p.entregada} pzs)`).join(', ');
          const estado = { SURTIDA: 'surtida', PARCIAL: 'parcial', NO_DISPENSADA: 'no dispensada' }[resultado.receta.estado];
          this.limpiarTodo();
          this.mostrarMensaje(
            `${resultado.folio ? 'Entrega ' + resultado.folio + ' guardada' : 'Receta guardada'}. Receta ${resultado.receta.folio}: ${estado}.` +
              (lotes ? ` Lotes entregados: ${lotes}.` : ''),
            'info',
          );
        },
        error: (error) => {
          this.guardando.set(false);
          this.mostrarMensaje(mensajeDeError(error, 'No se pudo guardar la entrega.'), 'error');
        },
      });
  }

  limpiarTodo(): void {
    this.recetaExistente.set(null);
    this.recetasIntelisis.set([]);
    this.fefo.set({});
    this.busquedaPaciente.set('');
    this.paciente.set(null);
    this.coincidencias.set([]);
    this.folioReceta.set('');
    this.avisoPaciente.set('');
    this.claves.set([]);
    this.cajas.set([]);
    this.elegida.set(null);
    this.cantidadIndicada = 0;
    this.mensaje.set('');
  }

  fechaHora(valor: string): string {
    return new Date(valor).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' });
  }

  fecha(valor: string): string {
    if (!valor) return '—';
    const [anio, mes, dia] = valor.split('-');
    return `${dia}/${mes}/${anio}`;
  }

  private hoy(): string {
    const ahora = new Date();
    return `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(ahora.getDate()).padStart(2, '0')}`;
  }

  private limpiarCodigo(): void {
    this.codigoEscaneado = '';
    this.campoCodigo()?.nativeElement.focus();
  }

  private mostrarMensaje(texto: string, tipo: 'info' | 'error' | 'warn'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
