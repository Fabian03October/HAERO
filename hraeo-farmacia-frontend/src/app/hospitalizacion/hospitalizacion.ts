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
  HospitalizacionApi,
  LineaCarrito,
  TipoEvento,
} from './hospitalizacion-api';

// Cada cuánto se vuelve a pedir la foto al backend (el backend lee Intelisis cada 5 minutos).
const RECARGA_MS = 60_000;

type Vista = 'CAMAS' | 'LISTA' | 'CARRITO';
type FiltroCamas = '' | 'OCUPADA' | 'LIBRE' | 'NOVEDADES';

// Hospitalización: camas por área con quién está, sus medicamentos y las novedades que HAERO
// detecta en Intelisis (ingresos, egresos, cambios de cama, medicamentos nuevos o suspendidos).
// La usan Farmacia y Supervisión; son datos clínicos bajo las políticas de uso y privacidad, y
// abrir el detalle de una cama queda registrado.
@Component({
  selector: 'app-hospitalizacion',
  imports: [FormsModule, Topbar, Sidebar, EscanerCamara],
  templateUrl: './hospitalizacion.html',
  styleUrls: ['../farmacia/panel-farmacia/panel-farmacia.css', '../farmacia/farmacia-comun.css', './hospitalizacion.css'],
})
export class Hospitalizacion {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  private readonly api = inject(HospitalizacionApi);

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
  // Antes de armar el carrito se pregunta si se imprimen las indicaciones médicas.
  readonly preguntaImprimir = signal(false);
  readonly eligiendoPacientes = signal(false);
  readonly elegidosImprimir = signal<Set<number>>(new Set());
  readonly terminando = signal(false);
  private readonly preguntado = new Set<string>();
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
  private readonly campoCodigo = viewChild<ElementRef<HTMLInputElement>>('campoCodigo');

  readonly camasCarrito = computed(() => {
    const carrito = this.carrito();
    if (!carrito) return [];
    if (!this.soloPendientes()) return carrito.camas;
    return carrito.camas
      .map((cama) => ({ ...cama, lineas: cama.lineas.filter((l) => l.estado === 'PENDIENTE' || l.estado === 'SUSPENDIDO') }))
      .filter((cama) => cama.lineas.length);
  });

  // Lo que llegó después de la hora de corte (surtir si urge) y lo cancelado que ya está en el carrito.
  readonly despuesDelCarrito = computed(() => {
    const carrito = this.carrito();
    if (!carrito) return { nuevos: [], suspendidos: [] };
    const nuevos: { cama: CamaCarrito; linea: LineaCarrito }[] = [];
    const suspendidos: { cama: CamaCarrito; linea: LineaCarrito }[] = [];
    for (const cama of carrito.camas) {
      for (const linea of cama.lineas) {
        if (linea.despuesDelCierre) nuevos.push({ cama, linea });
        if (linea.estado === 'SUSPENDIDO') suspendidos.push({ cama, linea });
      }
    }
    return { nuevos, suspendidos };
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
    const texto = this.busqueda().trim().toLowerCase();
    return this.camas().filter((cama) => {
      if (this.filtro() === 'OCUPADA' && cama.estado !== 'OCUPADA') return false;
      if (this.filtro() === 'LIBRE' && cama.estado !== 'LIBRE') return false;
      if (this.filtro() === 'NOVEDADES' && !cama.novedades) return false;
      if (!texto) return true;
      const pacientes = cama.pacientes.map((p) => `${p.nombre ?? ''} ${p.expediente ?? ''}`).join(' ');
      return `${cama.espacio} ${cama.nombre ?? ''} ${pacientes}`.toLowerCase().includes(texto);
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
      this.preguntarImpresion();
      setTimeout(() => this.campoCodigo()?.nativeElement.focus());
    }
  }

  // Una vez por área y día: "¿Deseas imprimir las indicaciones médicas?".
  private preguntarImpresion(): void {
    const llave = `${this.area()}|${new Date().toDateString()}`;
    if (!this.puedeSurtir || this.preguntado.has(llave)) return;
    this.preguntado.add(llave);
    this.eligiendoPacientes.set(false);
    this.elegidosImprimir.set(new Set());
    this.preguntaImprimir.set(true);
  }

  cerrarPregunta(): void {
    this.preguntaImprimir.set(false);
  }

  elegirPacientesImprimir(): void {
    this.eligiendoPacientes.set(true);
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
  imprimirIndicaciones(ingresos: number[] = []): void {
    const url = this.router.serializeUrl(this.router.createUrlTree(['/farmacia/hospitalizacion/imprimir/indicaciones'], {
      queryParams: { central: this.area(), ingresos: ingresos.length ? ingresos.join(',') : null },
    }));
    window.open(url, '_blank');
    this.preguntaImprimir.set(false);
  }

  imprimirElegidos(): void {
    const elegidos = [...this.elegidosImprimir()];
    if (!elegidos.length) return;
    this.imprimirIndicaciones(elegidos);
  }

  // Pacientes ocupando camas del área, para elegir a quién imprimir.
  readonly pacientesArea = computed(() =>
    this.camas()
      .filter((c) => c.pacientes.length)
      .map((c) => ({ idIngreso: c.pacientes[0].idIngreso, cama: c.espacio, nombre: c.pacientes[0].nombre })),
  );

  private abrirHojasBaja(ingreso?: number): void {
    const url = this.router.serializeUrl(this.router.createUrlTree(['/farmacia/hospitalizacion/imprimir/bajas'], {
      queryParams: { central: this.area(), ingreso: ingreso ?? null },
    }));
    window.open(url, '_blank');
  }

  reimprimirBajas(): void {
    this.abrirHojasBaja();
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
      sugeridas: linea.dosis24h ?? 0,
      entregadas: linea.entregadas,
    };
    this.prepararSurtido({ clave: linea.clave, medicamento: linea.medicamento ?? linea.clave, lote: null, candidatos: [candidato] }, candidato);
  }

  private prepararSurtido(escaneo: EscaneoCarrito, candidato: CandidatoCarrito): void {
    this.porSurtir.set(escaneo);
    this.elegirCandidato(candidato);
    this.mensajeCarrito.set('');
  }

  elegirCandidato(candidato: CandidatoCarrito): void {
    this.candidato.set(candidato);
    // La cantidad la captura el despachador (como en ventanilla).
    this.piezas.set(null);
    setTimeout(() => (document.getElementById('piezas-carrito') as HTMLInputElement | null)?.focus());
  }

  cancelarSurtido(): void {
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
    this.api.surtir(candidato.idIngreso, escaneo.clave, piezas, escaneo.lote ?? undefined).subscribe({
      next: (surtido) => {
        this.surtiendo.set(false);
        const lotes = surtido.partidas.map((p) => `${p.lote} (${p.entregada})`).join(', ');
        this.avisarCarrito(`Surtido a cama ${candidato.espacio}: ${piezas} pzs de ${escaneo.medicamento}. Lote ${lotes}.`, 'info');
        this.porSurtir.set(null);
        this.candidato.set(null);
        this.cargarCarrito();
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

  // Termina el carrito: si algo se canceló ya metido, pregunta; luego imprime la hoja de baja de cada paciente.
  terminarCarrito(confirmarCancelados = false): void {
    if (this.terminando()) return;
    this.terminando.set(true);
    this.api.terminar(this.area(), confirmarCancelados).subscribe({
      next: (hojas) => {
        this.terminando.set(false);
        this.cargarCarrito();
        if (!hojas.length) {
          this.avisarCarrito('Carrito terminado. No se surtió nada, así que no hay hojas de baja.', 'warn');
          return;
        }
        this.avisarCarrito(`Carrito terminado: ${hojas.length} ${hojas.length === 1 ? 'hoja' : 'hojas'} de baja (folios ${hojas[0].folio}–${hojas[hojas.length - 1].folio}).`, 'info');
        this.abrirHojasBaja();
      },
      error: (error) => {
        this.terminando.set(false);
        if (error?.status === 409 && !confirmarCancelados) {
          if (confirm(mensajeDeError(error, 'Hay medicamentos cancelados en el carrito.') + '\n\n¿Terminar así de todos modos?')) {
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
    this.cargarArea(central);
  }

  private cargarArea(central: string): void {
    if (this.vista() === 'CARRITO') this.cargarCarrito();
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
