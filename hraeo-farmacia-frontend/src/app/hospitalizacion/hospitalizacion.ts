import { Paginacion, Paginador } from '../shared/paginador/paginador';
import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../auth/session';
import { Topbar } from '../shared/topbar/topbar';
import { ItemMenu, Sidebar } from '../shared/sidebar/sidebar';
import { mensajeDeError } from '../almacen/almacen-api';
import { EscanerCamara } from '../shared/escaner-camara/escaner-camara';
import { leerGs1 } from '../shared/gs1';
import { FARMACIA_MENU } from '../farmacia/farmacia-nav';

import { MarcaSeguridad } from '../shared/marca-seguridad/marca-seguridad';
import { EntregaPrevia, FarmaciaApi } from '../farmacia/farmacia-api';
import { SeguridadMedicamentos } from '../shared/seguridad-medicamentos';
import { CantidadPiezas } from '../shared/cantidad-piezas/cantidad-piezas';
import {
  AreaHospitalizacion,
  CamaCarrito,
  CamaHospitalizacion,
  Carrito,
  CandidatoCarrito,
  DetalleCama,
  EstadoHospitalizacion,
  EscaneoCarrito,
  EstadoLinea,
  EventoHospitalizacion,
  HistorialCama,
  HojaBaja,
  HospitalizacionApi,
  LineaCarrito,
  TipoEvento,
} from './hospitalizacion-api';
import { coincide } from '../shared/buscar';

// Cada cuánto se vuelve a pedir la foto al backend (el backend lee Intelisis cada 5 minutos).
const RECARGA_MS = 60_000;

type Vista = 'CAMAS' | 'LISTA' | 'CARRITO' | 'SURTIDOS';
// Despachar carrito: 1 imprimir indicaciones, 2 surtir, 3 finalizar.
type Paso = 1 | 2 | 3;
type FiltroCamas = '' | 'OCUPADA' | 'LIBRE' | 'NOVEDADES';

// Hospitalización: camas por área con quién está, sus medicamentos y las novedades que HAERO
// detecta en Intelisis (ingresos, egresos, cambios de cama, medicamentos nuevos o suspendidos).
// La usan Farmacia y Supervisión; son datos clínicos bajo las políticas de uso y privacidad, y
// abrir el detalle de una cama queda registrado.
@Component({
  selector: 'app-hospitalizacion',
  imports: [CantidadPiezas, Paginador, MarcaSeguridad, FormsModule, Topbar, Sidebar, EscanerCamara],
  templateUrl: './hospitalizacion.html',
  styleUrls: ['../farmacia/panel-farmacia/panel-farmacia.css', '../farmacia/farmacia-comun.css', './hospitalizacion.css'],
})
export class Hospitalizacion {
  // Por páginas: la lista crece con el tiempo.
  readonly pagHojas = new Paginacion(() => this.hojasFiltradas());

  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  private readonly api = inject(HospitalizacionApi);
  // Piezas por caja de cada clave: un escaneo cuenta hasta una caja completa.
  private readonly catalogo = inject(SeguridadMedicamentos);
  private readonly farmaciaApi = inject(FarmaciaApi);
  // Lo que ya salió de esta clave para el paciente por Quimio o Ventanilla en los últimos 7 días (solo aviso).
  readonly previasCarrito = signal<EntregaPrevia[]>([]);

  // Hospitalización la usa solo Farmacia.
  readonly items: ItemMenu[] = FARMACIA_MENU;
  private readonly router = inject(Router);

  readonly areas = signal<AreaHospitalizacion[]>([]);
  readonly area = signal<string>('');
  readonly camas = signal<CamaHospitalizacion[]>([]);
  readonly novedades = signal<EventoHospitalizacion[]>([]);
  readonly estado = signal<EstadoHospitalizacion | null>(null);
  readonly detalle = signal<DetalleCama | null>(null);
  readonly camaElegida = signal<string | null>(null);

  readonly vista = signal<Vista>('CAMAS');
  readonly filtro = signal<FiltroCamas>('');
  readonly busqueda = signal('');
  readonly cargando = signal(true);
  readonly cargandoDetalle = signal(false);
  readonly error = signal('');

  // ---------- Carrito del día ----------
  readonly puedeSurtir = this.session.usuarioActual()?.rol === 'FARMACIA';
  readonly paso = signal<Paso>(1);
  // Paso 1: buscar paciente (cama, expediente o nombre) y elegir a quién imprimir.
  readonly busquedaCarrito = signal('');
  readonly elegidosImprimir = signal<Set<number>>(new Set());
  readonly terminando = signal(false);
  readonly carrito = signal<Carrito | null>(null);
  // HU42: caja con un código que HAERO no conoce; se elige de qué medicamento pendiente es.
  readonly codigoDesconocido = signal<{ codigo: string; lote?: string } | null>(null);
  readonly filtroDesconocido = signal('');
  readonly clavesPendientes = computed(() => {
    const filtro = this.filtroDesconocido().trim().toLowerCase();
    const unicas = new Map<string, string>();
    for (const cama of this.carrito()?.camas ?? []) {
      for (const linea of cama.lineas) {
        if (linea.estado === 'PENDIENTE' && linea.clave && linea.enCatalogo) unicas.set(linea.clave, linea.medicamento ?? linea.clave);
      }
    }
    return [...unicas.entries()]
      .map(([clave, medicamento]) => ({ clave, medicamento }))
      .filter((c) => !filtro || c.clave.toLowerCase().includes(filtro) || c.medicamento.toLowerCase().includes(filtro))
      .sort((a, b) => a.medicamento.localeCompare(b.medicamento));
  });
  codigoEscaneado = '';
  readonly camaraAbierta = signal(false);
  // Caja escaneada (o renglón elegido) esperando que se confirmen paciente y piezas.
  readonly porSurtir = signal<EscaneoCarrito | null>(null);
  readonly candidato = signal<CandidatoCarrito | null>(null);
  readonly piezas = signal<number | null>(null);
  readonly surtiendo = signal(false);
  readonly mensajeCarrito = signal('');
  readonly tipoMensajeCarrito = signal<'info' | 'error' | 'warn'>('info');
  readonly soloPendientes = signal(false);
  // Historial de una cama del carrito (7 días); lo que se surta desde ahí entra al carrito de hoy.
  readonly historial = signal<HistorialCama | null>(null);
  readonly cargandoHistorial = signal<number | null>(null);
  private readonly surtidoDesdeHistorial = signal(false);
  // Casillas marcadas en el historial ("día|clave"; "previos|clave" en la lista de arriba) y lo que falta
  // por surtir (se pide uno tras otro en el paso 2). Un medicamento marcado en dos días se surte una vez.
  readonly seleccionHistorial = signal<Set<string>>(new Set());
  readonly clavesElegidas = computed(() => new Set([...this.seleccionHistorial()].map((llave) => llave.split('|')[1])));
  readonly colaHistorial = signal<LineaCarrito[]>([]);
  // Lo no surtido de días anteriores, una vez por medicamento (desde el día más antiguo en que quedó pendiente).
  readonly pendientesHistorial = computed(() => {
    const porClave = new Map<string, { linea: LineaCarrito; desde: string }>();
    for (const d of this.historial()?.dias ?? []) {
      if (d.dia === this.carrito()?.dia) continue;
      for (const l of d.lineas) {
        if (!l.clave || !this.surtibleDesdeHistorial(d, l)) continue;
        const previo = porClave.get(l.clave);
        porClave.set(l.clave, { linea: previo?.linea ?? l, desde: d.dia });
      }
    }
    return [...porClave.values()];
  });
  private readonly campoCodigo = viewChild<ElementRef<HTMLInputElement>>('campoCodigo');

  readonly camasCarrito = computed(() => {
    const carrito = this.carrito();
    if (!carrito) return [];
    // Los que ya egresaron van en su propio aviso; a ellos ya no se les surte.
    const enCama = carrito.camas.filter((cama) => !cama.egresado);
    if (!this.soloPendientes()) return enCama;
    return enCama
      .map((cama) => ({ ...cama, lineas: cama.lineas.filter((l) => l.estado === 'PENDIENTE' || l.estado === 'SUSPENDIDO') }))
      .filter((cama) => cama.lineas.length);
  });

  // Lo que llegó después de la hora de corte (surtir si urge) y lo cancelado que ya está en el carrito.
  readonly despuesDelCarrito = computed(() => {
    const carrito = this.carrito();
    if (!carrito) return { nuevos: [], suspendidos: [], egresados: [] };
    const nuevos: { cama: CamaCarrito; linea: LineaCarrito }[] = [];
    const suspendidos: { cama: CamaCarrito; linea: LineaCarrito }[] = [];
    const egresados: { cama: CamaCarrito; linea: LineaCarrito }[] = [];
    for (const cama of carrito.camas) {
      for (const linea of cama.lineas) {
        if (cama.egresado) {
          if (linea.entregadas > 0) egresados.push({ cama, linea });
          continue;
        }
        if (linea.despuesDelCierre) nuevos.push({ cama, linea });
        if (linea.estado === 'SUSPENDIDO') suspendidos.push({ cama, linea });
      }
    }
    return { nuevos, suspendidos, egresados };
  });

  // Paso 1: solo pacientes en cama con indicaciones de medicamento del día (nunca uno sin ellas).
  readonly pacientesDelDia = computed(() => {
    const texto = this.busquedaCarrito();
    return (this.carrito()?.camas ?? [])
      .filter((cama) => !cama.egresado && cama.lineas.some((l) => l.estado !== 'SUSPENDIDO'))
      .filter((cama) => coincide(`${cama.espacio} ${cama.paciente ?? ''} ${cama.expediente ?? ''}`, texto, cama.paciente));
  });

  // Paso 3: lo que hay que decidir antes de finalizar y el total por medicamento.
  readonly porDecidir = computed(() => this.despuesDelCarrito().suspendidos.length + this.despuesDelCarrito().egresados.length);
  readonly totalCarrito = computed(() => {
    const totales = new Map<string, { medicamento: string; piezas: number; pacientes: number }>();
    for (const cama of this.carrito()?.camas ?? []) {
      for (const l of cama.lineas) {
        if (!l.entregadas || l.estado === 'SUSPENDIDO' || cama.egresado) continue;
        const llave = l.clave ?? l.medicamento ?? '';
        const t = totales.get(llave) ?? { medicamento: l.medicamento ?? llave, piezas: 0, pacientes: 0 };
        t.piezas += l.entregadas;
        t.pacientes += 1;
        totales.set(llave, t);
      }
    }
    return [...totales.entries()].map(([clave, t]) => ({ clave, ...t })).sort((a, b) => a.medicamento.localeCompare(b.medicamento));
  });

  // Surtidos del día: hojas por paciente (detalle en HAERO) y bajas por volumen (las de Intelisis).
  readonly diaSurtidos = signal(new Date().toLocaleDateString('en-CA'));
  readonly filtroSurtidos = signal('');
  readonly hojasDia = signal<HojaBaja[]>([]);
  readonly bajasDia = signal<HojaBaja[]>([]);
  readonly hojasFiltradas = computed(() => {
    const texto = this.filtroSurtidos();
    return this.hojasDia().filter((h) => coincide(`${h.cama ?? ''} ${h.paciente ?? ''} ${h.expediente ?? ''}`, texto, h.paciente));
  });

  readonly pendientesDeAyer = computed(() =>
    (this.carrito()?.camas ?? []).reduce((total, cama) => total + cama.lineas.filter((l) => l.pendienteDeAyer).length, 0),
  );

  readonly nombreArea = computed(() => this.areaActual()?.nombre ?? this.area());

  readonly avanceCarrito = computed(() => {
    const carrito = this.carrito();
    return carrito && carrito.lineas ? Math.round((carrito.surtidas / carrito.lineas) * 100) : 0;
  });

  readonly estadoLineaTexto: Record<EstadoLinea, string> = {
    PENDIENTE: 'Pendiente',
    SURTIDO: 'En carrito',
    SUSPENDIDO: 'Cancelado',
  };

  readonly areaActual = computed(() => this.areas().find((a) => a.central === this.area()) ?? null);

  readonly camasFiltradas = computed(() => {
    const texto = this.busqueda().trim();
    return this.camas().filter((cama) => {
      if (this.filtro() === 'OCUPADA' && cama.estado !== 'OCUPADA') return false;
      if (this.filtro() === 'LIBRE' && cama.estado !== 'LIBRE') return false;
      if (this.filtro() === 'NOVEDADES' && !cama.novedades) return false;
      if (!texto) return true;
      const pacientes = cama.pacientes.map((p) => `${p.nombre ?? ''} ${p.expediente ?? ''}`).join(' ');
      return coincide(`${cama.espacio} ${cama.nombre ?? ''} ${pacientes}`, texto)
        || cama.pacientes.some((p) => coincide(p.nombre ?? '', texto, p.nombre));
    });
  });

  readonly filtros = computed(() => {
    const lista = this.camas();
    return [
      { valor: '' as FiltroCamas, etiqueta: 'Todas', total: lista.length },
      { valor: 'OCUPADA' as FiltroCamas, etiqueta: 'Ocupadas', total: lista.filter((c) => c.estado === 'OCUPADA').length },
      { valor: 'LIBRE' as FiltroCamas, etiqueta: 'Libres', total: lista.filter((c) => c.estado === 'LIBRE').length },
      { valor: 'NOVEDADES' as FiltroCamas, etiqueta: 'Con novedades', total: lista.filter((c) => c.novedades > 0).length },
    ];
  });

  readonly tipoTexto: Record<TipoEvento, string> = {
    INGRESO: 'Ingreso',
    EGRESO: 'Egreso',
    CAMBIO_CAMA: 'Cambio de cama',
    MEDICAMENTO_NUEVO: 'Indicación nueva',
    MEDICAMENTO_SUSPENDIDO: 'Medicamento eliminado',
    MEDICAMENTO_MODIFICADO: 'Cambio de dosis',
  };

  constructor() {
    this.catalogo.cargar();
    this.cargarAreas();
    // Se refresca solo para que los avisos aparezcan sin recargar la página.
    const reloj = setInterval(() => this.refrescar(), RECARGA_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
  }

  private cargarAreas(): void {
    this.api.areas().subscribe({
      next: (areas) => {
        this.areas.set(areas);
        // La primera vez se abre el área con más pacientes.
        if (!this.area() && areas.length) {
          const conMas = [...areas].sort((a, b) => b.pacientes - a.pacientes)[0];
          this.elegirArea(conMas.central);
        }
        this.cargando.set(false);
      },
      error: (error) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo cargar Hospitalización.'));
      },
    });
    this.api.estado().subscribe({ next: (estado) => this.estado.set(estado), error: () => this.estado.set(null) });
  }

  private refrescar(): void {
    this.cargarAreas();
    if (this.area()) this.cargarArea(this.area());
  }

  cambiarVista(vista: Vista): void {
    this.vista.set(vista);
    if (vista === 'CARRITO') {
      this.cargarCarrito();
      this.irAPaso(1);
    }
    if (vista === 'SURTIDOS') this.cargarSurtidos();
  }

  irAPaso(paso: Paso): void {
    this.paso.set(paso);
    if (paso === 2) setTimeout(() => this.campoCodigo()?.nativeElement.focus());
  }

  cargarSurtidos(): void {
    if (!this.area()) return;
    const dia = this.diaSurtidos();
    this.api.hojasBaja(this.area(), dia).subscribe({
      next: (hojas) => this.hojasDia.set(hojas),
      error: (error) => this.error.set(mensajeDeError(error, 'No se pudieron cargar los surtidos del día.')),
    });
    this.api.bajasVolumen(this.area(), dia).subscribe({ next: (bajas) => this.bajasDia.set(bajas) });
  }

  // Piezas distintas a la dosis del día: se marca para revisarlo después (no bloquea).
  revisar(l: LineaCarrito): boolean {
    const esperadas = l.piezasDia ?? l.dosis24h;
    return l.entregadas > 0 && !!esperadas && l.entregadas !== esperadas;
  }

  alternarElegido(idIngreso: number): void {
    this.elegidosImprimir.update((actual) => {
      const nuevo = new Set(actual);
      if (nuevo.has(idIngreso)) nuevo.delete(idIngreso);
      else nuevo.add(idIngreso);
      return nuevo;
    });
  }

  // Abre en otra pestaña la hoja "Indicaciones médicas activas" (todas, elegidas o de un paciente).
  imprimirIndicaciones(ingresos: number[] = [], dia?: string): void {
    const url = this.router.serializeUrl(this.router.createUrlTree(['/farmacia/hospitalizacion/imprimir/indicaciones'], {
      queryParams: { central: this.area(), ingresos: ingresos.length ? ingresos.join(',') : null, dia: dia ?? null },
    }));
    window.open(url, '_blank');
  }

  // "Todas" = las que se ven (con la búsqueda aplicada); el backend igual descarta a quien no tiene indicaciones del día.
  imprimirTodas(): void {
    this.imprimirIndicaciones(this.busquedaCarrito().trim() ? this.pacientesDelDia().map((c) => c.idIngreso) : []);
  }

  imprimirElegidos(): void {
    const elegidos = [...this.elegidosImprimir()];
    if (!elegidos.length) return;
    this.imprimirIndicaciones(elegidos);
  }

  // Hojas por paciente (ingreso opcional) o bajas por volumen (volumen = true, folio opcional), del día elegido.
  abrirBajas(opciones: { ingreso?: number | null; volumen?: boolean; folio?: number; dia?: string }): void {
    const url = this.router.serializeUrl(this.router.createUrlTree(['/farmacia/hospitalizacion/imprimir/bajas'], {
      queryParams: {
        central: this.area(),
        dia: opciones.dia ?? null,
        ingreso: opciones.ingreso ?? null,
        volumen: opciones.volumen ? 1 : null,
        folio: opciones.folio ?? null,
      },
    }));
    window.open(url, '_blank');
  }

  private cargarCarrito(): void {
    if (!this.area()) return;
    this.api.carrito(this.area()).subscribe({
      next: (carrito) => this.carrito.set(carrito),
      error: (error) => this.avisarCarrito(mensajeDeError(error, 'No se pudo cargar el carrito del área.'), 'error'),
    });
  }

  // Pistola o cámara: si el código es GS1 se usa su GTIN y su lote.
  escanear(): void {
    let codigo = this.codigoEscaneado.trim();
    if (!codigo) return;
    const gs1 = leerGs1(codigo);
    const lote = gs1?.lote;
    if (gs1?.gtin) codigo = gs1.gtin;
    this.codigoEscaneado = '';
    this.codigoDesconocido.set(null);
    this.api.escanear(this.area(), codigo, lote).subscribe({
      next: (escaneo) => this.prepararSurtido(escaneo, escaneo.candidatos[0]),
      error: (error) => {
        this.porSurtir.set(null);
        if (error?.status === 404) {
          this.filtroDesconocido.set('');
          this.codigoDesconocido.set({ codigo, lote });
          this.avisarCarrito('Esta caja todavía no está registrada. Elige abajo qué medicamento es; la próxima vez se reconoce sola.', 'warn');
          return;
        }
        this.avisarCarrito(mensajeDeError(error, 'No se pudo reconocer la caja.'), 'error');
        this.campoCodigo()?.nativeElement.focus();
      },
    });
  }

  /** HU42: liga el código nuevo al medicamento elegido y sigue como si la caja se hubiera reconocido. */
  asignarCodigo(clave: string): void {
    const desconocido = this.codigoDesconocido();
    if (!desconocido) return;
    this.api.escanear(this.area(), desconocido.codigo, desconocido.lote, clave).subscribe({
      next: (escaneo) => {
        this.codigoDesconocido.set(null);
        this.avisarCarrito(`Código guardado como ${escaneo.medicamento}.`, 'info');
        this.prepararSurtido(escaneo, escaneo.candidatos[0]);
      },
      error: (error) => this.avisarCarrito(mensajeDeError(error, 'No se pudo guardar el código.'), 'error'),
    });
  }

  /** Ingreso, egreso o cambio de cama: quien aparece es quien lo registró, no un médico. */
  esMovimientoCama(tipo: string): boolean {
    return tipo === 'INGRESO' || tipo === 'EGRESO' || tipo === 'CAMBIO_CAMA';
  }

  codigoDesdeCamara(codigo: string): void {
    this.camaraAbierta.set(false);
    this.codigoEscaneado = codigo;
    this.escanear();
  }

  // Sin código de barras: se surte desde el renglón del paciente (lote por FEFO).
  surtirRenglon(cama: CamaCarrito, linea: LineaCarrito): void {
    if (!linea.clave) return;
    const candidato: CandidatoCarrito = {
      idIngreso: cama.idIngreso,
      espacio: cama.espacio,
      paciente: cama.paciente,
      sugeridas: linea.piezasDia ?? linea.dosis24h ?? 0,
      entregadas: linea.entregadas,
    };
    this.prepararSurtido({ clave: linea.clave, medicamento: linea.medicamento ?? linea.clave, lote: null, candidatos: [candidato] }, candidato);
  }

  // ---------- Historial de la cama ----------

  verHistorial(cama: CamaCarrito): void {
    if (this.historial()?.idIngreso === cama.idIngreso) {
      this.historial.set(null);
      return;
    }
    this.cargandoHistorial.set(cama.idIngreso);
    this.seleccionHistorial.set(new Set());
    this.colaHistorial.set([]);
    this.api.historial(cama.idIngreso).subscribe({
      next: (historial) => {
        this.cargandoHistorial.set(null);
        this.historial.set(historial);
      },
      error: (error) => {
        this.cargandoHistorial.set(null);
        this.avisarCarrito(mensajeDeError(error, 'No se pudo cargar el historial de la cama.'), 'error');
      },
    });
  }

  /** Se puede surtir: está pendiente (hoy o un día anterior), en el catálogo y no se surtió después. */
  surtibleDesdeHistorial(dia: HistorialCama['dias'][number], l: LineaCarrito): boolean {
    return l.estado === 'PENDIENTE' && l.enCatalogo && !!l.clave && !dia.surtidasDespues.includes(l.clave);
  }

  // "martes 18/11" (al mediodía para que la zona horaria no cambie el día).
  diaLargo(dia: string): string {
    const fecha = new Date(`${dia}T12:00:00`);
    return `${fecha.toLocaleDateString('es-MX', { weekday: 'long' })} ${fecha.toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit' })}`;
  }

  estadoHistorial(d: HistorialCama['dias'][number], l: LineaCarrito): string {
    if (l.estado === 'SURTIDO') return 'Surtido';
    if (l.estado === 'SUSPENDIDO') return 'Cancelado';
    if (!l.enCatalogo) return 'No se surte (sin clave)';
    if (l.clave && d.surtidasDespues.includes(l.clave)) return 'Surtido otro día';
    return d.dia === this.carrito()?.dia ? 'Pendiente hoy' : 'Sin surtir';
  }

  alternarSeleccionHistorial(llave: string): void {
    const nueva = new Set(this.seleccionHistorial());
    if (nueva.has(llave)) nueva.delete(llave);
    else nueva.add(llave);
    this.seleccionHistorial.set(nueva);
  }

  /** Lo elegido en el historial se surte en el paso 2, uno tras otro (cada uno pide sus piezas). */
  surtirSeleccionados(): void {
    const elegidas = this.clavesElegidas();
    if (!elegidas.size) return;
    const porClave = new Map<string, LineaCarrito>();
    for (const d of this.historial()?.dias ?? []) {
      for (const l of d.lineas) {
        if (l.clave && elegidas.has(l.clave) && this.surtibleDesdeHistorial(d, l) && !porClave.has(l.clave)) porClave.set(l.clave, l);
      }
    }
    this.colaHistorial.set([...porClave.values()]);
    this.seleccionHistorial.set(new Set());
    this.irAPaso(2);
    this.siguienteDelHistorial();
  }

  private siguienteDelHistorial(): void {
    const h = this.historial();
    const [siguiente, ...resto] = this.colaHistorial();
    if (!h || !siguiente?.clave) {
      this.colaHistorial.set([]);
      return;
    }
    this.colaHistorial.set(resto);
    const candidato: CandidatoCarrito = { idIngreso: h.idIngreso, espacio: h.espacio, paciente: h.paciente, sugeridas: siguiente.piezasDia ?? siguiente.dosis24h ?? 0, entregadas: 0 };
    this.prepararSurtido({ clave: siguiente.clave, medicamento: siguiente.medicamento ?? siguiente.clave, lote: null, candidatos: [candidato] }, candidato, true);
    // La captura de piezas queda debajo del historial: se lleva la vista hasta ella.
    setTimeout(() => document.querySelector('.confirmar')?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50);
  }

  private prepararSurtido(escaneo: EscaneoCarrito, candidato: CandidatoCarrito, desdeHistorial = false): void {
    this.surtidoDesdeHistorial.set(desdeHistorial);
    this.porSurtir.set(escaneo);
    this.elegirCandidato(candidato);
    this.mensajeCarrito.set('');
  }

  elegirCandidato(candidato: CandidatoCarrito): void {
    this.candidato.set(candidato);
    this.previasCarrito.set([]);
    const expediente = this.carrito()?.camas.find((c) => c.idIngreso === candidato.idIngreso)?.expediente;
    const clave = this.porSurtir()?.clave;
    if (expediente && clave) {
      this.farmaciaApi.entregasPrevias(expediente, [clave]).subscribe({
        next: (previas) => this.previasCarrito.set(previas.filter((p) => p.area !== 'Hospitalización')),
      });
    }
    // Sugerencia: lo que falta del día, hasta una caja completa. Si la indicación no permite calcularlo
    // (por ejemplo "Unidosis" sin frecuencia), el campo queda vacío para que el despachador anote lo que entrega.
    const porCaja = this.catalogo.piezasPorCaja(this.porSurtir()?.clave);
    const falta = candidato.sugeridas - candidato.entregadas;
    this.piezas.set(candidato.sugeridas > 0 && falta > 0 ? Math.min(porCaja, falta) : null);
    setTimeout(() => (document.getElementById('piezas-carrito') as HTMLInputElement | null)?.focus());
  }

  cancelarSurtido(): void {
    // Cancelar uno del historial pasa al siguiente elegido.
    if (this.surtidoDesdeHistorial() && this.colaHistorial().length) {
      this.siguienteDelHistorial();
      return;
    }
    this.porSurtir.set(null);
    this.candidato.set(null);
    this.campoCodigo()?.nativeElement.focus();
  }

  confirmarSurtido(): void {
    const escaneo = this.porSurtir();
    const candidato = this.candidato();
    const piezas = this.piezas();
    if (!escaneo || !candidato || !piezas || piezas <= 0 || this.surtiendo()) return;
    this.surtiendo.set(true);
    this.api.surtir(candidato.idIngreso, escaneo.clave, piezas, escaneo.lote ?? undefined, this.surtidoDesdeHistorial()).subscribe({
      next: (surtido) => {
        this.surtiendo.set(false);
        const lotes = surtido.partidas.map((p) => `${p.lote} (${p.entregada})`).join(', ');
        this.avisarCarrito(`Surtido a cama ${candidato.espacio}: ${piezas} pzs de ${escaneo.medicamento}. Lote ${lotes}.`, 'info');
        this.porSurtir.set(null);
        this.candidato.set(null);
        this.cargarCarrito();
        // Si venía del historial, se refresca para que ya no se ofrezca otra vez.
        const h = this.historial();
        if (this.surtidoDesdeHistorial() && h) {
          this.api.historial(h.idIngreso).subscribe({ next: (nuevo) => this.historial.set(nuevo) });
          this.siguienteDelHistorial();
        }
        this.campoCodigo()?.nativeElement.focus();
      },
      error: (error) => {
        this.surtiendo.set(false);
        this.avisarCarrito(mensajeDeError(error, 'No se pudo surtir.'), 'error');
      },
    });
  }

  // Saca del carrito lo que se metió (por ejemplo, porque el médico lo canceló): regresa al inventario.
  sacarDelCarrito(cama: CamaCarrito, linea: LineaCarrito): void {
    if (!linea.clave) return;
    this.api.quitar(cama.idIngreso, linea.clave, linea.estado === 'SUSPENDIDO' ? 'Cancelado por el médico' : 'Se sacó del carrito').subscribe({
      next: (carrito) => {
        this.carrito.set(carrito);
        this.avisarCarrito(`Se sacó del carrito ${linea.medicamento} (cama ${cama.espacio}); regresó al inventario.`, 'info');
      },
      error: (error) => this.avisarCarrito(mensajeDeError(error, 'No se pudo sacar del carrito.'), 'error'),
    });
  }

  // Finaliza el despacho (del día o de urgencia): si quedó algo cancelado o de un egresado, pregunta
  // si ya se aplicó; luego imprime la baja por volumen que se captura en Intelisis.
  terminarCarrito(confirmarCancelados = false): void {
    if (this.terminando()) return;
    this.terminando.set(true);
    // Si el carrito del día ya se había terminado, esto es una urgencia: se suma a la misma baja.
    const urgencia = !!this.carrito()?.cerradoEn;
    this.api.terminar(this.area(), confirmarCancelados).subscribe({
      next: (bajas) => {
        this.terminando.set(false);
        this.cargarCarrito();
        this.irAPaso(1);
        if (!bajas.length) {
          this.avisarCarrito('Carrito terminado. No hubo surtidos nuevos, así que no hay nada que sumar a la baja.', 'warn');
          return;
        }
        const baja = bajas[0];
        this.avisarCarrito(urgencia
          ? `Urgencia sumada a la baja del día ${baja.observaciones}: ahora ${baja.totalPiezas} pzs. Si ya la capturaste en Intelisis, agrega solo lo surtido de urgencia.`
          : `Carrito terminado: baja por volumen ${baja.observaciones} (${baja.totalPiezas} pzs). Captúrala en Intelisis con ese folio en Observaciones.`, 'info');
        this.abrirBajas({ volumen: true, folio: bajas[0].folio });
      },
      error: (error) => {
        this.terminando.set(false);
        if (error?.status === 409 && !confirmarCancelados) {
          if (confirm(mensajeDeError(error, 'Hay medicamentos por decidir en el carrito.') +
            '\n\nLo que no saques del carrito cuenta como YA APLICADO y se queda en la baja. ¿Finalizar así?')) {
            this.terminarCarrito(true);
          }
          return;
        }
        this.avisarCarrito(mensajeDeError(error, 'No se pudo terminar el carrito.'), 'error');
      },
    });
  }

  ahora(): string {
    return new Date().toLocaleString('es-MX', { dateStyle: 'long', timeStyle: 'short' });
  }

  private avisarCarrito(texto: string, tipo: 'info' | 'error' | 'warn'): void {
    this.mensajeCarrito.set(texto);
    this.tipoMensajeCarrito.set(tipo);
  }

  elegirArea(central: string): void {
    this.area.set(central);
    this.camaElegida.set(null);
    this.detalle.set(null);
    this.filtro.set('');
    this.busqueda.set('');
    this.carrito.set(null);
    this.porSurtir.set(null);
    this.mensajeCarrito.set('');
    this.busquedaCarrito.set('');
    this.elegidosImprimir.set(new Set());
    this.paso.set(1);
    this.cargarArea(central);
  }

  private cargarArea(central: string): void {
    if (this.vista() === 'CARRITO') this.cargarCarrito();
    if (this.vista() === 'SURTIDOS') this.cargarSurtidos();
    this.api.camas(central).subscribe({
      next: (camas) => this.camas.set(camas),
      error: (error) => this.error.set(mensajeDeError(error, 'No se pudieron cargar las camas del área.')),
    });
    this.api.novedades(central).subscribe({ next: (lista) => this.novedades.set(lista) });
  }

  // Abre el detalle de la cama y apaga su aviso para este usuario.
  elegirCama(espacio: string): void {
    this.camaElegida.set(espacio);
    this.cargandoDetalle.set(true);
    this.api.detalle(espacio).subscribe({
      next: (detalle) => {
        this.cargandoDetalle.set(false);
        this.detalle.set(detalle);
        // En pantallas angostas el detalle queda debajo de las camas: se baja hasta él.
        if (window.innerWidth <= 1100) {
          setTimeout(() => document.querySelector('.lateral-hosp')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
        }
        if (detalle.cama.novedades > 0) {
          this.api.marcarVisto(espacio).subscribe({
            next: () => {
              this.camas.update((lista) => lista.map((c) => (c.espacio === espacio ? { ...c, novedades: 0 } : c)));
              this.novedades.update((lista) => lista.map((e) => (e.espacio === espacio ? { ...e, visto: true } : e)));
              this.areas.update((lista) =>
                lista.map((a) => (a.central === detalle.cama.central ? { ...a, conNovedades: Math.max(0, a.conNovedades - 1) } : a)),
              );
            },
          });
        }
      },
      error: (error) => {
        this.cargandoDetalle.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo abrir la cama.'));
      },
    });
  }

  cerrarDetalle(): void {
    this.camaElegida.set(null);
    this.detalle.set(null);
  }

  // ---------- Formato ----------

  primerPaciente(cama: CamaHospitalizacion) {
    return cama.pacientes[0] ?? null;
  }

  hora(valor: string | null): string {
    if (!valor) return '—';
    return new Date(valor).toLocaleString('es-MX', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false });
  }

  hace(valor: string | null): string {
    if (!valor) return 'sin lectura';
    const minutos = Math.round((Date.now() - new Date(valor).getTime()) / 60000);
    if (minutos < 1) return 'hace un momento';
    if (minutos < 60) return `hace ${minutos} min`;
    const horas = Math.round(minutos / 60);
    return `hace ${horas} h`;
  }

  dias(valor: number | null): string {
    if (valor === null) return '';
    return valor === 0 ? 'Ingresó hoy' : valor === 1 ? '1 día' : `${valor} días`;
  }
}
