import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { EscanerCamara } from '../../../shared/escaner-camara/escaner-camara';
import { leerGs1 } from '../../../shared/gs1';
import { Medicamento } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { BuscadorMedicamento } from '../../buscador-medicamento/buscador-medicamento';

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
type ModoPaciente = 'EXPEDIENTE' | 'NOMBRE' | 'CURP';

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

// Farmacia · Nueva entrega (HU28 a HU30). Maquetado solo en el frontend:
// - el catálogo de medicamentos ya existe en el backend y se usa;
// - buscar al paciente en Intelisis, el lote FEFO de Farmacia y guardar la entrega
//   necesitan endpoints de Farmacia que aún no existen; la pantalla lo indica.
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
  ];
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
    if (!this.paciente() && !this.busquedaPaciente().trim()) faltan.push('buscar al paciente por expediente, nombre o CURP');
    if (!this.folioReceta().trim()) faltan.push('el folio de la receta');
    if (!this.claves().length) faltan.push('al menos una clave de la receta');
    if (this.resumen().some((item) => item.excede)) faltan.push('corregir las claves con más piezas de las indicadas');
    if (this.resumen().some((item) => item.noDispensada && !item.motivo.trim())) faltan.push('el motivo de las claves no dispensadas');
    if (this.cajas().some((caja) => !caja.clave || !(caja.piezas > 0))) faltan.push('la clave y las piezas de cada caja');
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
    this.folioReceta.set(inject(ActivatedRoute).snapshot.queryParamMap.get('receta') ?? '');
  }

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
    // HU28 CA1: el paciente se leerá de Intelisis en solo lectura con
    // GET /api/farmacia/pacientes?expediente= | ?nombre= | ?curp=. Por nombre puede haber varios.
    const dato = { EXPEDIENTE: 'expediente', NOMBRE: 'nombre', CURP: 'CURP' }[modo];
    this.avisoPaciente.set(`La búsqueda por ${dato} en Intelisis se conecta cuando exista el endpoint de pacientes de Farmacia.`);
  }

  elegirPaciente(paciente: Paciente): void {
    this.paciente.set(paciente);
    this.coincidencias.set([]);
    this.avisoPaciente.set('');
  }

  cambiarPaciente(): void {
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
      this.mostrarMensaje('Este código no trae lote (no es GS1). Escribe el lote y la caducidad de la caja en el renglón.', 'warn');
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

  limpiarTodo(): void {
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
