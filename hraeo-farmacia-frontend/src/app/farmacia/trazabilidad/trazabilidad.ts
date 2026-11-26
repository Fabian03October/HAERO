import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, forkJoin } from 'rxjs';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { leerGs1 } from '../../shared/gs1';
import { mensajeDeError } from '../../almacen/almacen-api';
import { Paginacion, Paginador } from '../../shared/paginador/paginador';
import { FARMACIA_MENU } from '../farmacia-nav';
import { cargarLogo } from '../../almacen/despacho/salidas-registradas/comprobante-salidas';
import { armarReporteTrazabilidad, armarReporteVarios, conDatosHaero } from './reporte-trazabilidad';
import { FarmaciaApi, LoteTrazable, PasoRecorrido, PeriodoTrazabilidad, Trazabilidad as TrazabilidadApi } from '../farmacia-api';

type Modo = TrazabilidadApi['modo'];

// Entrega ya formateada para la tabla.
interface EntregaFila {
  fecha: string;
  paciente: string;
  expediente: string | null;
  clave: string;
  lote: string;
  piezas: number;
  despachador: string;
  folio: string;
  fuente: 'HAERO' | 'Intelisis';
}

// Etapas que recorre un lote; se llenan con HAERO y, donde HAERO no sabe, con Intelisis.
const ETAPAS: PasoRecorrido[] = [
  { etapa: 'Pedido y contrato', detalle: 'Compra de Intelisis con la que llegó', fecha: null, fuente: null },
  { etapa: 'Entrada a Almacén', detalle: 'Recepción con el registro de Intelisis', fecha: null, fuente: null },
  { etapa: 'Despacho a Farmacia', detalle: 'Salida de Almacén por una solicitud', fecha: null, fuente: null },
  { etapa: 'Recepción en Farmacia', detalle: 'Lo que Farmacia confirmó que llegó', fecha: null, fuente: null },
  { etapa: 'Entregas a pacientes', detalle: 'Dispensaciones de ese lote', fecha: null, fuente: null },
  { etapa: 'Baja en Intelisis', detalle: 'Consumo de Farmacia dado de baja por volumen', fecha: null, fuente: null },
];

// Consultas que caben en un reporte (cada una se vuelve a pedir y lleva al menos una hoja).
const MAXIMO_LOTES_REPORTE = 20;

// Lo que se marcó para el reporte de varias consultas: lotes, expedientes, pacientes o recetas.
interface ElegidoReporte {
  llave: string;
  modo: Modo;
  valor: string;
  clave?: string;
  periodo?: PeriodoTrazabilidad;
  titulo: string;
}

const CURP = /^[A-Z]{4}\d{6}[A-Z0-9]{8}$/i;
// Expediente: solo números (3 a 10) o la forma "XXX-999999" de Intelisis.
const EXPEDIENTE = /^(\d{3,10}|[A-Z]{2,4}-\d{3,10})$/i;

export type Busqueda =
  | { tipo: 'LOTE'; lote: string }
  | { tipo: 'CURP'; valor: string }
  | { tipo: 'TEXTO'; valor: string; pareceExpediente: boolean }
  | { tipo: 'CORTO' };

/** Decide qué se escribió o escaneó: caja (GS1), CURP, o texto libre (medicamento, clave, lote, expediente o nombre). */
export function queBuscar(entrada: string): Busqueda {
  const texto = entrada.trim();
  const gs1 = leerGs1(texto);
  if (gs1?.lote) return { tipo: 'LOTE', lote: gs1.lote };
  if (CURP.test(texto)) return { tipo: 'CURP', valor: texto.toUpperCase() };
  if (texto.length < 3) return { tipo: 'CORTO' };
  return { tipo: 'TEXTO', valor: texto, pareceExpediente: EXPEDIENTE.test(texto) };
}

// Farmacia · Trazabilidad: un solo buscador para medicamento, lote, expediente, paciente o caja
// escaneada. El recorrido junta HAERO con lo que copia la sincronización de Intelisis. Solo
// Farmacia y Supervisión ven los datos de pacientes y cada consulta queda registrada.
@Component({
  selector: 'app-trazabilidad',
  imports: [FormsModule, Topbar, Sidebar, Paginador],
  templateUrl: './trazabilidad.html',
  styleUrls: ['../panel-farmacia/panel-farmacia.css', '../farmacia-comun.css', './trazabilidad.css'],
})
export class Trazabilidad {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;

  readonly busqueda = signal('');
  // Periodo opcional de las entregas a pacientes (no aplica a recetas, que ya tienen su ventana).
  readonly desde = signal('');
  readonly hasta = signal('');
  readonly aviso = signal('');
  readonly buscando = signal(false);
  // Lo último que se buscó como texto libre, para ofrecer "buscar como expediente / paciente".
  readonly textoLibre = signal('');
  readonly lotes = signal<LoteTrazable[] | null>(null);
  readonly pagLotes = new Paginacion(() => this.lotes() ?? []);
  readonly loteActual = signal('');
  readonly modo = signal<Modo | null>(null);
  readonly titulo = signal('');
  readonly recorrido = signal<PasoRecorrido[]>(ETAPAS);
  // Lotes marcados para el reporte de varios lotes; se conservan entre búsquedas (para juntar varios medicamentos).
  readonly seleccion = signal<Map<string, ElegidoReporte>>(new Map());
  readonly generandoReporte = signal(false);
  // Última respuesta completa, para las tablas de Intelisis y el reporte PDF.
  readonly resultado = signal<TrazabilidadApi | null>(null);
  // La línea de tiempo solo tiene sentido con pasos registrados en HAERO; si todo viene de Intelisis, van tablas.
  readonly mostrarLinea = computed(() => {
    const resultado = this.resultado();
    return !!resultado && conDatosHaero(resultado);
  });
  readonly pasosConDatos = computed(() => this.recorrido().filter((p) => p.fuente));
  readonly entregas = signal<EntregaFila[]>([]);
  // Un lote puede tener cientos de entregas (las de Intelisis llegan hasta 2,000).
  readonly pagEntregas = new Paginacion(() => this.entregas());
  private readonly api = inject(FarmaciaApi);

  buscar(): void {
    const busqueda = queBuscar(this.busqueda());
    this.aviso.set('');
    if (busqueda.tipo === 'CORTO') {
      this.aviso.set('Escribe al menos 3 letras o números, o escanea la caja.');
    } else if (busqueda.tipo === 'LOTE') {
      this.busqueda.set(busqueda.lote);
      this.verLote(busqueda.lote);
    } else if (busqueda.tipo === 'CURP') {
      this.verPaciente(busqueda.valor);
    } else {
      this.buscarTexto(busqueda.valor, busqueda.pareceExpediente);
    }
  }

  /** Texto libre: primero los lotes (medicamento, clave o lote); si no hay, expediente o nombre del paciente. */
  private buscarTexto(texto: string, pareceExpediente: boolean): void {
    this.textoLibre.set(texto);
    this.buscando.set(true);
    this.api.trazabilidadLotes(texto).subscribe({
      next: (lotes) => {
        this.buscando.set(false);
        // El lote escrito, con o sin el sufijo de Intelisis ("6020174" o "6020174 SCC").
        const t = texto.toUpperCase();
        const mismoLote = lotes.filter((l) => {
          const lote = l.lote.toUpperCase();
          return t === lote || t.startsWith(lote + ' ') || t.startsWith(lote + '/');
        });
        if (mismoLote.length === 1) {
          this.lotes.set(null);
          this.verLote(mismoLote[0].lote, mismoLote[0].clave);
        } else if (pareceExpediente) {
          // Un número puede ser expediente o el inicio de un lote: se busca el expediente y los lotes quedan abajo.
          this.lotes.set(lotes.length ? lotes : null);
          this.pagLotes.reiniciar();
          this.verExpedienteOReceta(texto);
        } else if (lotes.length) {
          this.lotes.set(lotes);
          this.pagLotes.reiniciar();
          this.limpiarResultado();
        } else {
          this.lotes.set(null);
          this.verPaciente(texto);
        }
      },
      error: (error) => {
        this.buscando.set(false);
        this.aviso.set(mensajeDeError(error, 'No se pudo buscar.'));
      },
    });
  }

  verLote(lote: string, clave?: string): void {
    this.loteActual.set(lote + (clave ? '|' + clave : ''));
    this.consultar(this.api.trazabilidadPorLote(lote, clave, this.periodo()), `Lote ${lote}${clave ? ' · ' + clave : ''}`);
  }

  verExpediente(expediente: string): void {
    this.consultar(this.api.trazabilidadPorExpediente(expediente, this.periodo()), `Expediente ${expediente}`);
  }

  verReceta(folio: string): void {
    this.consultar(this.api.trazabilidadPorReceta(folio), `Receta ${folio}`);
  }

  /** Un número: primero como expediente; si no trae entregas, como folio de receta. */
  private verExpedienteOReceta(numero: string): void {
    this.buscando.set(true);
    this.api.trazabilidadPorExpediente(numero, this.periodo()).subscribe({
      next: (porExpediente) => {
        if (porExpediente.entregas.length) {
          this.buscando.set(false);
          this.mostrar(porExpediente, `Expediente ${numero}`);
          return;
        }
        this.api.trazabilidadPorReceta(numero).subscribe({
          next: (porReceta) => {
            this.buscando.set(false);
            this.mostrar(porReceta, `Receta ${porReceta.valor}`);
          },
          error: () => {
            this.buscando.set(false);
            this.mostrar(porExpediente, `Expediente ${numero}`);
            this.aviso.set(`No hay entregas para el expediente ${numero} ni una receta con ese folio en Intelisis.`);
          },
        });
      },
      error: (error) => {
        this.buscando.set(false);
        this.aviso.set(mensajeDeError(error, 'No se pudo consultar la trazabilidad.'));
      },
    });
  }

  verPaciente(paciente: string): void {
    this.consultar(this.api.trazabilidadPorPaciente(paciente, this.periodo()), paciente);
  }

  /** Desde la tabla de entregas: del paciente a sus lotes. */
  verPacienteDe(entrega: EntregaFila): void {
    // Con dos expedientes ("X / Y": registrado dos veces en Intelisis) se busca por el nombre.
    if (entrega.expediente && !entrega.expediente.includes('/')) this.verExpediente(entrega.expediente);
    else this.verPaciente(entrega.paciente);
  }

  esLoteActual(l: LoteTrazable): boolean {
    return this.loteActual() === l.lote + '|' + l.clave;
  }

  /** Periodo escrito en la pantalla, solo con las fechas que se llenaron. */
  private periodo(): PeriodoTrazabilidad {
    return { ...(this.desde() ? { desde: this.desde() } : {}), ...(this.hasta() ? { hasta: this.hasta() } : {}) };
  }

  /** " · del 01/11/2026 al 24/11/2026" para el título y el PDF. */
  private textoPeriodo(p: PeriodoTrazabilidad): string {
    const f = (d: string) => d.split('-').reverse().join('/');
    if (p.desde && p.hasta) return ` · del ${f(p.desde)} al ${f(p.hasta)}`;
    if (p.desde) return ` · desde el ${f(p.desde)}`;
    return p.hasta ? ` · hasta el ${f(p.hasta)}` : '';
  }

  private consultar(consulta: Observable<TrazabilidadApi>, titulo: string): void {
    this.buscando.set(true);
    this.aviso.set('');
    consulta.subscribe({
      next: (resultado) => {
        this.buscando.set(false);
        this.mostrar(resultado, titulo);
      },
      error: (error) => {
        this.buscando.set(false);
        this.limpiarResultado();
        this.aviso.set(mensajeDeError(error, 'No se pudo consultar la trazabilidad.'));
      },
    });
  }

  private mostrar(resultado: TrazabilidadApi, titulo: string): void {
    this.resultado.set(resultado);
    this.modo.set(resultado.modo);
    this.titulo.set(resultado.modo === 'RECETA' ? titulo : titulo + this.textoPeriodo(this.periodo()));
    // Por expediente o paciente no hay recorrido de un lote: se quedan las etapas vacías.
    this.recorrido.set(
      resultado.recorrido.length
        ? resultado.recorrido.map((paso) => ({ ...paso, fecha: paso.fecha ? this.fechaHora(paso.fecha) : null }))
        : ETAPAS,
    );
    this.entregas.set(resultado.entregas.map((entrega) => ({ ...entrega, fecha: entrega.fecha ? this.fechaHora(entrega.fecha) : '—' })));
    this.pagEntregas.reiniciar();
    const avisos = [resultado.aviso ?? ''];
    if (!resultado.entregas.length) {
      avisos.push(resultado.modo === 'LOTE' ? 'No hay entregas a pacientes de este lote en HAERO ni en Intelisis.' : 'No hay entregas registradas para esa búsqueda.');
    }
    this.aviso.set(avisos.filter(Boolean).join(' '));
  }

  private limpiarResultado(): void {
    this.resultado.set(null);
    this.modo.set(null);
    this.titulo.set('');
    this.loteActual.set('');
    this.recorrido.set(ETAPAS);
    this.entregas.set([]);
  }

  private llaveLote(l: LoteTrazable): string {
    return 'LOTE|' + l.clave + '|' + l.lote.toUpperCase();
  }

  estaSeleccionado(l: LoteTrazable): boolean {
    return this.seleccion().has(this.llaveLote(l));
  }

  /** Casilla de la lista de lotes. */
  alternarSeleccion(l: LoteTrazable): void {
    this.alternar({
      llave: this.llaveLote(l), modo: 'LOTE', valor: l.lote, clave: l.clave,
      titulo: `Lote ${l.lote} · ${l.clave}${l.medicamento ? ' · ' + l.medicamento : ''}`,
    });
  }

  /** "Agregar al reporte" con lo que se está viendo: un lote, un expediente, un paciente o una receta. */
  agregarConsultaActual(): void {
    const e = this.consultaActual();
    if (e && !this.seleccion().has(e.llave)) this.alternar(e);
  }

  consultaActualAgregada(): boolean {
    const e = this.consultaActual();
    return !!e && this.seleccion().has(e.llave);
  }

  private consultaActual(): ElegidoReporte | null {
    const resultado = this.resultado();
    if (!resultado) return null;
    const clave = resultado.modo === 'LOTE' ? this.loteActual().split('|')[1] : undefined;
    const periodo = resultado.modo === 'RECETA' ? {} : this.periodo();
    const base = resultado.modo === 'LOTE' && clave ? `LOTE|${clave}|${resultado.valor.toUpperCase()}` : `${resultado.modo}||${resultado.valor.toUpperCase()}`;
    const llave = `${base}|${periodo.desde ?? ''}|${periodo.hasta ?? ''}`;
    return { llave, modo: resultado.modo, valor: resultado.valor, clave, periodo, titulo: this.titulo() };
  }

  quitarDelReporte(e: ElegidoReporte): void {
    this.alternar(e);
  }

  private alternar(e: ElegidoReporte): void {
    const nueva = new Map(this.seleccion());
    if (nueva.has(e.llave)) nueva.delete(e.llave);
    else if (nueva.size >= MAXIMO_LOTES_REPORTE) {
      this.aviso.set(`El reporte admite hasta ${MAXIMO_LOTES_REPORTE} consultas.`);
      return;
    } else nueva.set(e.llave, e);
    this.seleccion.set(nueva);
  }

  limpiarSeleccion(): void {
    this.seleccion.set(new Map());
  }

  private consultaDe(e: ElegidoReporte): Observable<TrazabilidadApi> {
    switch (e.modo) {
      case 'LOTE':
        return this.api.trazabilidadPorLote(e.valor, e.clave, e.periodo);
      case 'EXPEDIENTE':
        return this.api.trazabilidadPorExpediente(e.valor, e.periodo);
      case 'RECETA':
        return this.api.trazabilidadPorReceta(e.valor);
      default:
        return this.api.trazabilidadPorPaciente(e.valor, e.periodo);
    }
  }

  /** Vuelve a consultar cada elemento marcado y arma un solo PDF con todos. */
  reporteVarios(formato: 'pdf' | 'excel' = 'pdf'): void {
    const elegidos = [...this.seleccion().values()];
    if (!elegidos.length) return;
    this.generandoReporte.set(true);
    forkJoin(elegidos.map((e) => this.consultaDe(e))).subscribe({
      next: async (resultados) => {
        const { doc, nombre } = armarReporteVarios(resultados.map((resultado, i) => ({ resultado, titulo: elegidos[i].titulo })),
          this.session.usuarioActual()?.nombreCompleto ?? '', await cargarLogo());
        await this.guardar(doc, nombre, formato);
        this.generandoReporte.set(false);
      },
      error: (error) => {
        this.generandoReporte.set(false);
        this.aviso.set(mensajeDeError(error, 'No se pudo armar el reporte.'));
      },
    });
  }

  async descargarReporte(formato: 'pdf' | 'excel' = 'pdf'): Promise<void> {
    const resultado = this.resultado();
    if (!resultado) return;
    const { doc, nombre } = armarReporteTrazabilidad(resultado, this.titulo(), this.session.usuarioActual()?.nombreCompleto ?? '', await cargarLogo());
    await this.guardar(doc, nombre, formato);
  }

  /** El mismo reporte en PDF o en Excel (mismos datos y colores). */
  private async guardar(doc: { save(nombre: string): unknown }, nombre: string, formato: 'pdf' | 'excel'): Promise<void> {
    if (formato === 'pdf') {
      doc.save(nombre);
      return;
    }
    const { descargarExcel } = await import('../../shared/reporte-excel');
    await descargarExcel(doc, nombre.replace(/\.pdf$/, ''));
  }

  fechaCorta(valor: string | null): string {
    return valor ? new Date(valor.length === 10 ? valor + 'T12:00:00' : valor).toLocaleDateString('es-MX', { dateStyle: 'short' }) : '—';
  }

  private fechaHora(valor: string): string {
    // Las fechas sin hora (solo día) vienen de Intelisis a las 00:00: se muestra solo el día.
    const fecha = new Date(valor);
    return valor.endsWith('T00:00:00') || valor.endsWith('T00:00')
      ? fecha.toLocaleDateString('es-MX', { dateStyle: 'short' })
      : fecha.toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' });
  }
}
