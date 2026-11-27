import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { EscanerCamara } from '../../shared/escaner-camara/escaner-camara';
import { MarcaSeguridad } from '../../shared/marca-seguridad/marca-seguridad';
import { DatosGs1, leerGs1 } from '../../shared/gs1';
import { SeguridadMedicamentos } from '../../shared/seguridad-medicamentos';
import { pitido } from '../../shared/pitido';
import { Medicamento, mensajeDeError } from '../../almacen/almacen-api';
import { BuscadorMedicamento } from '../buscador-medicamento/buscador-medicamento';
import { EntregaPrevia,
  ClaveSugerida,
  EstadoQuimio,
  FarmaciaApi,
  FiltroQuimio,
  LoteFarmacia,
  MedicamentoEnNota,
  Paciente,
  QuimioRegistrada,
  SesionesSolicitud,
  ResumenQuimio,
  SolicitudQuimio,
  SolicitudQuimioDetalle,
} from '../farmacia-api';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { FARMACIA_MENU } from '../farmacia-nav';
import { CajasQuirofano, EstadoBajaArea } from '../cajas-quirofano/cajas-quirofano';
import { Paginacion, Paginador } from '../../shared/paginador/paginador';
import { diaLocal } from '../../shared/fechas';
import { CantidadPiezas } from '../../shared/cantidad-piezas/cantidad-piezas';

type ModoBusqueda = 'NOMBRE' | 'CURP';
type Opcion = 'PRINCIPAL' | 'ALTERNATIVA';

// Medicamento de la indicación ya pasado a clave, con su alternativa si el médico la escribió
// ("dar A; si no hay, dar B"). lote = '' quiere decir "el que sale primero" (FEFO).
interface RenglonQuimio {
  id: number;
  textoMedico: string;
  principal: { clave: string; nombre: string };
  alternativa: { clave: string; nombre: string } | null;
  dosis: string;
  piezas: number;
  usar: Opcion;
  lote: string;
  // Si se surte la alternativa habiendo existencia de la principal.
  motivoAlternativa: string;
  // Opcionales: dosis (ej. 280 mg) contra lo que trae cada frasco (ej. 300 mg).
  dosisCantidad: number | null;
  dosisUnidad: string;
  contenidoPorPieza: number | null;
  destinoSobrante: 'MERMA' | 'RESGUARDO' | null;
  // Escaneo continuo: una entrada por pieza escaneada (codigo = el de la caja, fefo = la caja no traía lote).
  escaneos: { lote: string; caducidad: string; codigo: string; fefo: boolean }[];
  // No se puede escanear (sin código o sin lector): se usa "lote" (vacío = el que caduca primero).
  sinEscaneo: boolean;
  // De qué renglón de la nota salió (fármaco|volumen|solución|alternativa): dos bolsas de cloruro de sodio de
  // distinto volumen comparten claves, así que se distinguen por esto.
  deNota?: string;
  // No se surte (sin existencia, o no se maneja y el paciente lo compra): se entrega lo demás.
  noSurtido: boolean;
  motivoNoSurtido: string;
}

// Avance de la solicitud abierta guardado en la pestaña (se recupera al recargar).
interface BorradorQuimio {
  guardado: number;
  folioIntelisis: string | null;
  capturaManual: boolean;
  folio: string;
  expediente: string;
  nombreSolicitud: string;
  notaMedico: string;
  paciente: Paciente | null;
  expedienteCorregido: boolean;
  justificacionPaciente: string;
  renglones: RenglonQuimio[];
}

// Avisos flotantes: ok = listo (verde), info = aviso (azul), warn = revisar (ámbar), error = no se pudo (rojo).
type TipoAviso = 'ok' | 'info' | 'warn' | 'error';
interface Aviso {
  id: number;
  tipo: TipoAviso;
  titulo: string;
  texto: string;
  accion?: { texto: string; ruta: string };
}

const mismoLote = (a: string, b: string) => a.trim().toUpperCase() === b.trim().toUpperCase();

export const UNIDADES_DOSIS = ['mg', 'mcg', 'g', 'UI', 'ml'];

// Unidades de peso convertibles entre sí (en mg). UI y ml no se convierten.
const EN_MG: Record<string, number> = { mcg: 0.001, mg: 1, g: 1000 };

// Quita acentos, signos y espacios de más para comparar nombres de paciente.
function palabras(nombre: string): string[] {
  return nombre
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toUpperCase()
    .replace(/[^A-Z ]/g, ' ')
    .split(/\s+/)
    .filter((p) => p.length > 1);
}

// Clave elegida para un renglón (del buscador, de una sugerencia o del ciclo anterior).
type ClaveElegida = Pick<Medicamento, 'clave' | 'nombreGenerico'>;

// Farmacia · Quimioterapia. La solicitud de procedimiento a Oncología Médica se lee de Intelisis
// (cada 5 minutos): al elegirla o escribir el folio de la hoja se llenan solos el paciente, el
// diagnóstico, el médico y la nota tal como la escribió. La nota no trae claves: SICOMED sugiere las
// del catálogo y Farmacia las confirma (o repite las del ciclo anterior del paciente). Se surte cada
// frasco (escaneado o eligiendo el lote) y se registra a nombre de quien surte.
// Se descuenta como una entrega normal de Farmacia. Intelisis solo se consulta.
@Component({
  selector: 'app-quimioterapia',
  imports: [CantidadPiezas, FormsModule, DatePipe, Topbar, Sidebar, EscanerCamara, MarcaSeguridad, BuscadorMedicamento, Paginador],
  templateUrl: './quimioterapia.html',
  styleUrls: ['../panel-farmacia/panel-farmacia.css', '../farmacia-comun.css', './quimioterapia.css'],
})
export class Quimioterapia {
  // Por páginas: quimioterapias surtidas (por día pueden ser muchas).
  readonly pagRecientes = new Paginacion(() => this.recientes());
  // Por páginas: la lista de solicitudes puede traer hasta 200.
  readonly pagSolicitudes = new Paginacion(() => this.solicitudes());

  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;
  private readonly api = inject(FarmaciaApi);

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<TipoAviso>('info');

  // ---------- Avisos flotantes (arriba a la derecha) ----------
  // Se apilan; los de éxito e información se cierran solos, los errores se quedan hasta cerrarlos.
  readonly avisos = signal<Aviso[]>([]);
  private siguienteAviso = 1;

  cerrarAviso(id: number): void {
    this.avisos.update((lista) => lista.filter((a) => a.id !== id));
  }

  private readonly router = inject(Router);

  ejecutarAccion(aviso: Aviso): void {
    this.cerrarAviso(aviso.id);
    if (aviso.accion) this.router.navigateByUrl(aviso.accion.ruta);
  }

  // ---------- 0. Solicitudes de Oncología en Intelisis ----------
  readonly estado = signal<EstadoQuimio | null>(null);
  readonly filtro = signal<FiltroQuimio>('POR_SURTIR');
  readonly buscarSolicitud = signal('');
  // Filtros nuevos: rango de fechas (la requerida) y médico.
  readonly desde = signal('');
  readonly hasta = signal('');
  readonly medico = signal('');
  readonly medicos = signal<string[]>([]);
  readonly resumen = signal<ResumenQuimio | null>(null);

  /** Total de cada pastilla de filtro (del resumen del día). */
  totalFiltro(valor: FiltroQuimio): number | null {
    const r = this.resumen();
    if (!r) return null;
    return valor === 'POR_SURTIR' ? r.porSurtir : valor === 'HOY' ? r.paraHoy : valor === 'CON_PENDIENTES' ? r.conPendientes : null;
  }

  /** Filtros que no son el de estado, para mostrarlos como etiquetas que se quitan con ×. */
  readonly filtrosActivos = computed(() => {
    const activos: { etiqueta: string; quitar: () => void }[] = [];
    const fecha = (v: string) => v.split('-').reverse().join('/');
    if (this.buscarSolicitud().trim()) activos.push({ etiqueta: `“${this.buscarSolicitud().trim()}”`, quitar: () => this.quitarFiltro('buscar') });
    if (this.desde()) activos.push({ etiqueta: `Desde ${fecha(this.desde())}`, quitar: () => this.quitarFiltro('desde') });
    if (this.hasta()) activos.push({ etiqueta: `Hasta ${fecha(this.hasta())}`, quitar: () => this.quitarFiltro('hasta') });
    if (this.medico()) activos.push({ etiqueta: this.medico(), quitar: () => this.quitarFiltro('medico') });
    return activos;
  });

  quitarFiltro(cual: 'buscar' | 'desde' | 'hasta' | 'medico' | 'todos'): void {
    if (cual === 'buscar' || cual === 'todos') this.buscarSolicitud.set('');
    if (cual === 'desde' || cual === 'todos') this.desde.set('');
    if (cual === 'hasta' || cual === 'todos') this.hasta.set('');
    if (cual === 'medico' || cual === 'todos') this.medico.set('');
    this.cargarSolicitudes();
  }

  cambiarFecha(cual: 'desde' | 'hasta', valor: string): void {
    (cual === 'desde' ? this.desde : this.hasta).set(valor ?? '');
    this.cargarSolicitudes();
  }

  cambiarMedico(valor: string): void {
    this.medico.set(valor ?? '');
    this.cargarSolicitudes();
  }

  private cargarResumen(): void {
    this.api.resumenQuimio().subscribe({ next: (r) => this.resumen.set(r), error: () => this.resumen.set(null) });
  }

  /** Nombre del medicamento de la nota como se lee (RITUXIMAB → Rituximab). */
  nombreBonito(nombre: string): string {
    return nombre.toLowerCase().replace(/(^|\s)\S/g, (l) => l.toUpperCase());
  }
  readonly solicitudes = signal<SolicitudQuimio[]>([]);
  readonly cargandoLista = signal(false);
  readonly detalle = signal<SolicitudQuimioDetalle | null>(null);
  readonly cargandoDetalle = signal(false);
  // Sin Intelisis (o una hoja que no aparece): se captura la solicitud a mano, como antes.
  readonly capturaManual = signal(false);
  // Farmacia dice que el paciente de la solicitud no es el correcto y busca otro.
  readonly corrigiendo = signal(false);
  readonly filtros: { valor: FiltroQuimio; etiqueta: string }[] = [
    { valor: 'POR_SURTIR', etiqueta: 'Por surtir' },
    { valor: 'HOY', etiqueta: 'Para hoy' },
    { valor: 'CON_PENDIENTES', etiqueta: 'Con pendientes' },
    { valor: 'SURTIDAS', etiqueta: 'Ya surtidas' },
    { valor: 'TODAS', etiqueta: 'Todas' },
  ];

  // ---------- 1. Solicitud y paciente ----------
  readonly folio = signal('');
  readonly expediente = signal('');
  // Lo que ya salió de estas claves para el paciente por Hospitalización o Ventanilla en los últimos 7 días (solo aviso).
  readonly previasQuimio = signal<EntregaPrevia[]>([]);
  readonly nombreSolicitud = signal('');
  readonly buscando = signal(false);
  readonly noExiste = signal(false);
  readonly paciente = signal<Paciente | null>(null);
  readonly modoBusqueda = signal<ModoBusqueda>('NOMBRE');
  readonly textoBusqueda = signal('');
  readonly coincidencias = signal<Paciente[]>([]);
  // Cuando el nombre no coincide y Farmacia confirma que sí es el paciente.
  readonly justificacionPaciente = signal('');
  readonly expedienteCorregido = signal(false);

  readonly nombreCoincide = computed(() => {
    const paciente = this.paciente();
    if (!paciente || !this.nombreSolicitud().trim()) return null;
    const solicitud = palabras(this.nombreSolicitud());
    const intelisis = new Set(palabras(paciente.nombre));
    const comunes = solicitud.filter((p) => intelisis.has(p)).length;
    // Coincide si al menos dos palabras (nombre y apellido) están en ambos.
    return comunes >= Math.min(2, solicitud.length);
  });

  // Sesiones ya surtidas de la misma solicitud: todas deben ser del mismo paciente.
  readonly sesiones = signal<SesionesSolicitud | null>(null);
  readonly otroPacienteEnSolicitud = computed(() => {
    const previas = this.sesiones();
    const paciente = this.paciente();
    return !!previas?.pacienteExpediente && !!paciente?.expediente && previas.pacienteExpediente.toUpperCase() !== paciente.expediente.toUpperCase();
  });

  // Si Intelisis no responde: se confirma con el expediente físico y se justifica.
  readonly intelisisCaido = signal(false);
  readonly sinIntelisis = signal(false);
  readonly nombreManual = signal('');
  readonly justificacionSinIntelisis = signal('');

  readonly pacienteListo = computed(() => {
    if (!this.paciente() || !this.folio().trim() || this.otroPacienteEnSolicitud()) return false;
    if (this.sinIntelisis() && !this.justificacionSinIntelisis().trim()) return false;
    if (this.expedienteCorregido()) return true;
    if (this.nombreCoincide() === false) return !!this.justificacionPaciente().trim();
    return this.nombreCoincide() === true || this.sinIntelisis();
  });

  // ---------- 2. Indicación del médico ----------
  readonly notaMedico = signal('');
  readonly textoRenglon = signal('');
  readonly principal = signal<ClaveElegida | null>(null);
  readonly alternativa = signal<ClaveElegida | null>(null);
  readonly buscandoAlternativa = signal(false);
  readonly dosis = signal('');
  readonly piezas = signal<number | null>(null);
  readonly unidades = UNIDADES_DOSIS;
  readonly dosisCantidad = signal<number | null>(null);
  readonly dosisUnidad = signal('mg');
  readonly contenidoPorPieza = signal<number | null>(null);
  // Frascos que se necesitan para la dosis (si se capturó la dosis y lo que trae cada frasco).
  readonly piezasSugeridas = computed(() => {
    const cantidad = this.dosisCantidad();
    const porPieza = this.contenidoPorPieza();
    return cantidad && porPieza && cantidad > 0 && porPieza > 0 ? Math.ceil(cantidad / porPieza) : null;
  });
  readonly renglones = signal<RenglonQuimio[]>([]);
  // Tabla de 1 clic: si no se pueden calcular las piezas de la clave elegida, se piden en su renglón.
  readonly pendientePiezas = signal<{ farmaco: string; clave: string } | null>(null);
  readonly piezasFila = signal<number | null>(null);
  // El formulario completo (buscar otra clave, alternativa, dosis a mano) queda escondido hasta que se pide.
  readonly formularioAbierto = signal(false);
  private siguienteId = 1;
  readonly clavesEnLista = computed(() => this.renglones().flatMap((r) => [r.principal.clave, ...(r.alternativa ? [r.alternativa.clave] : [])]));

  // ---------- 3. Surtido ----------
  codigoFrasco = '';
  readonly camara = signal(false);
  // Lo último que pasó al escanear (se pinta en grande con su color) y el código nuevo por asignar.
  readonly ultimoEscaneo = signal<{ tipo: 'ok' | 'completo' | 'error' | 'aviso'; texto: string } | null>(null);
  readonly codigoSinClave = signal<{ codigo: string; gs1: DatosGs1 | null } | null>(null);
  // Caja escaneada que ya eligió su medicamento de la nota pero espera que se escriban las piezas.
  private readonly escaneoEnEspera = signal<{ codigo: string; gs1: DatosGs1 | null; clave: string } | null>(null);
  // Código que SICOMED no conoce, escaneado antes de elegir clave: se liga a la clave que se elija en la nota.
  private readonly codigoNuevoEnEspera = signal<{ codigo: string; gs1: DatosGs1 | null } | null>(null);
  // Código de caja → clave (null = SICOMED no lo conoce), para no consultar dos veces la misma caja.
  private readonly codigosConocidos = new Map<string, string | null>();
  readonly existencias = signal<LoteFarmacia[]>([]);

  readonly guardando = signal(false);
  readonly registrada = signal<QuimioRegistrada | null>(null);
  readonly recientes = signal<QuimioRegistrada[]>([]);

  readonly faltanParaEntregar = computed(() => {
    const faltan: string[] = [];
    if (!this.pacienteListo()) faltan.push('verificar al paciente');
    if (!this.renglones().length) faltan.push('transcribir la indicación');
    for (const r of this.renglones()) {
      if (r.noSurtido) {
        if (!r.motivoNoSurtido.trim()) faltan.push(`por qué no se surte ${this.claveSurtida(r)}`);
        continue;
      }
      const entregada = this.entregadoSinDevolver().get(this.claveSurtida(r));
      if (entregada) faltan.push(`quitar ${this.claveSurtida(r)}: ya se entregó en la sesión ${entregada.sesion} y no se ha devuelto`);
      if (this.pideMotivoAlternativa(r) && !r.motivoAlternativa.trim()) faltan.push(`el motivo de surtir la alternativa de ${r.principal.clave}`);
      if ((this.sobrante(r) ?? 0) > 0 && !r.destinoSobrante) faltan.push(`qué se hace con el sobrante de ${this.claveSurtida(r)}`);
      if (this.sobrante(r) !== null && this.sobrante(r)! < 0) faltan.push(`más piezas de ${this.claveSurtida(r)} (no alcanza la dosis)`);
      if (!r.sinEscaneo && r.escaneos.length < r.piezas) faltan.push(`escanear ${r.piezas - r.escaneos.length} pieza(s) de ${this.claveSurtida(r)}`);
      if (!r.sinEscaneo && r.escaneos.length > r.piezas) faltan.push(`quitar ${r.escaneos.length - r.piezas} pieza(s) escaneadas de más de ${this.claveSurtida(r)}`);
    }
    return faltan;
  });

  // ---------- Reprogramar (el paciente no puede pasar el día programado) ----------
  readonly motivosReprogramar = ['Paciente no se presentó', 'Paciente no apto hoy (indicación médica)', 'Falta medicamento'];
  readonly reprogramando = signal<number | null>(null);
  readonly fechaReprogramar = signal('');
  readonly motivoReprogramar = signal('');
  readonly guardandoReprogramar = signal(false);
  readonly hoyDia = diaLocal();

  abrirReprogramar(s: SolicitudQuimio): void {
    this.reprogramando.set(s.folio);
    this.fechaReprogramar.set('');
    this.motivoReprogramar.set('');
  }

  guardarReprogramacion(s: SolicitudQuimio): void {
    if (this.guardandoReprogramar()) return;
    this.guardandoReprogramar.set(true);
    this.api.reprogramarQuimio(s.folio, this.fechaReprogramar(), this.motivoReprogramar().trim()).subscribe({
      next: (r) => {
        this.guardandoReprogramar.set(false);
        this.reprogramando.set(null);
        const dia = (r.fechaRequerida ?? '').slice(0, 10).split('-').reverse().join('/');
        this.mostrar(`Solicitud ${s.folio} reprogramada al ${dia}. Notifica a Oncología Médica del cambio.`, 'info');
        this.cargarSolicitudes();
      },
      error: (e) => {
        this.guardandoReprogramar.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudo reprogramar.'), 'error');
      },
    });
  }

  // ---------- Baja del día (como en Hospitalización) ----------
  private readonly bajas = inject(CajasQuirofano);
  // Piezas por caja de cada clave: un escaneo cuenta hasta una caja completa.
  private readonly catalogo = inject(SeguridadMedicamentos);
  readonly bajaDia = signal<EstadoBajaArea | null>(null);
  readonly terminando = signal(false);

  cargarBajaDia(): void {
    this.bajas.estadoBaja('QUIMIO').subscribe({ next: (estado) => this.bajaDia.set(estado), error: () => this.bajaDia.set(null) });
  }

  terminarDia(): void {
    if (this.terminando()) return;
    this.terminando.set(true);
    this.bajas.terminarDia('QUIMIO').subscribe({
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
    const parametros = new URLSearchParams({ central: 'QUIMIO', volumen: '1', ...(folio ? { folio: String(folio) } : {}) });
    window.open(`/farmacia/hospitalizacion/imprimir/bajas?${parametros}`, '_blank');
  }

  // Hojas de solicitud para surtir: "Su hoja", todas las de la lista o las elegidas.
  readonly elegidasImprimir = signal<Set<number>>(new Set());
  // "Las de hoy": las de la lista que se requieren hoy (como el carrito de Hospitalización: lo del día).
  readonly deHoy = computed(() => this.solicitudes().filter((s) => (s.fechaRequerida ?? '').startsWith(this.hoyDia)));

  alternarElegida(folio: number): void {
    this.elegidasImprimir.update((actual) => {
      const nuevo = new Set(actual);
      if (!nuevo.delete(folio)) nuevo.add(folio);
      return nuevo;
    });
  }

  imprimirSolicitudes(cuales: Iterable<number | SolicitudQuimio>): void {
    const folios = [...cuales].map((c) => (typeof c === 'number' ? c : c.folio));
    if (!folios.length) return;
    window.open(`/farmacia/quimioterapia/imprimir?folios=${folios.join(',')}`, '_blank');
  }

  imprimirHojasPacientes(): void {
    window.open('/farmacia/hospitalizacion/imprimir/bajas?central=QUIMIO', '_blank');
  }

  constructor() {
    this.catalogo.cargar();
    effect(() => {
      const expediente = this.expediente().trim();
      const claves = [...new Set(this.renglones().map((r) => this.claveSurtida(r)))];
      untracked(() => {
        if (!expediente || !claves.length) return this.previasQuimio.set([]);
        this.api.entregasPrevias(expediente, claves).subscribe({
          next: (previas) => this.previasQuimio.set(previas.filter((p) => p.area !== 'Quimioterapia')),
          error: () => this.previasQuimio.set([]),
        });
      });
    });
    this.cargarEstado();
    this.cargarSolicitudes();
    this.cargarExistencias();
    this.cargarRecientes();
    this.cargarBajaDia();
    this.cargarResumen();
    this.api.medicosQuimio().subscribe({ next: (lista) => this.medicos.set(lista), error: () => this.medicos.set([]) });
    this.restaurarBorrador();
    // Cada cambio de la solicitud abierta se guarda en la pestaña para no perderlo al recargar.
    effect(() => this.guardarBorrador());
  }

  // ---------- Avance guardado en la pestaña ----------
  // Si se recarga la página, se vuelve a abrir la misma solicitud con lo que ya se llevaba
  // (paciente, medicamentos, piezas y lo escaneado). Se guarda en sessionStorage: es solo de esta
  // pestaña y se borra al cerrarla (trae datos de pacientes). Al recargar se
  // recupera la solicitud con lo que se llevaba.
  private restaurando: BorradorQuimio | null = null;

  private claveBorrador(): string {
    return `hraeo.quimio.borrador.${this.session.usuarioActual()?.nombreUsuario ?? ''}`;
  }

  // Solicitud que se dejó a medias para volver a la lista; se retoma con "Continuar".
  readonly enPausa = signal<BorradorQuimio | null>(null);

  /** Regresa a la lista sin perder lo que se lleva de esta solicitud. */
  volverALista(): void {
    const b = this.construirBorrador();
    const hayAvance = b.renglones.length > 0 || (!!b.paciente && this.capturaManual());
    if (hayAvance) this.enPausa.set(b);
    this.limpiar();
    this.mensaje.set('');
    this.cargarSolicitudes();
  }

  continuarPausada(): void {
    const b = this.enPausa();
    if (!b) return;
    this.enPausa.set(null);
    if (b.folioIntelisis) {
      this.restaurando = b;
      this.abrirSolicitud(b.folioIntelisis);
    } else {
      this.capturaManual.set(true);
      this.folio.set(b.folio);
      this.expediente.set(b.expediente);
      this.nombreSolicitud.set(b.nombreSolicitud);
      this.notaMedico.set(b.notaMedico);
      this.aplicarBorrador(b);
    }
  }

  descartarPausada(): void {
    this.enPausa.set(null);
  }

  piezasEscaneadas(b: BorradorQuimio): number {
    return b.renglones.reduce((t, r) => t + (r.escaneos?.length ?? 0), 0);
  }

  private construirBorrador(): BorradorQuimio {
    return {
      guardado: Date.now(),
      folioIntelisis: this.detalle() ? String(this.detalle()!.solicitud.folio) : null,
      capturaManual: this.capturaManual(),
      folio: this.folio(),
      expediente: this.expediente(),
      nombreSolicitud: this.nombreSolicitud(),
      notaMedico: this.notaMedico(),
      paciente: this.paciente(),
      expedienteCorregido: this.expedienteCorregido(),
      justificacionPaciente: this.justificacionPaciente(),
      renglones: this.renglones(),
    };
  }

  private guardarBorrador(): void {
    const borrador = this.construirBorrador();
    const pausa = this.enPausa();
    if (this.restaurando) return;
    try {
      if (!borrador.folioIntelisis && !borrador.capturaManual) sessionStorage.removeItem(this.claveBorrador());
      else sessionStorage.setItem(this.claveBorrador(), JSON.stringify(borrador));
      if (pausa) sessionStorage.setItem(`${this.claveBorrador()}.pausa`, JSON.stringify(pausa));
      else sessionStorage.removeItem(`${this.claveBorrador()}.pausa`);
    } catch {
      // Sin almacenamiento (modo privado o bloqueado): la pantalla funciona igual, solo no se recupera al recargar.
    }
  }

  private leerBorrador(clave: string): BorradorQuimio | null {
    try {
      const texto = sessionStorage.getItem(clave);
      const b = texto ? (JSON.parse(texto) as BorradorQuimio) : null;
      // Un avance de hace más de 12 horas ya no se recupera (otro turno).
      return b && Date.now() - b.guardado <= 12 * 60 * 60 * 1000 ? b : null;
    } catch {
      return null;
    }
  }

  private restaurarBorrador(): void {
    // Al entrar siempre se ve la lista completa: lo que estaba abierto queda "a medias" para Continuar.
    const abierta = this.leerBorrador(this.claveBorrador());
    this.enPausa.set(abierta && abierta.renglones?.length ? abierta : this.leerBorrador(`${this.claveBorrador()}.pausa`));
  }

  private aplicarBorrador(b: BorradorQuimio): void {
    this.restaurando = null;
    if (b.paciente) {
      this.paciente.set(b.paciente);
      this.noExiste.set(false);
    }
    this.expedienteCorregido.set(b.expedienteCorregido);
    this.justificacionPaciente.set(b.justificacionPaciente);
    // Lo guardado con una versión anterior de la pantalla puede no traer los campos nuevos.
    const renglones = (b.renglones ?? []).map((r) => ({ ...r, escaneos: r.escaneos ?? [], sinEscaneo: !!r.sinEscaneo, noSurtido: !!r.noSurtido, motivoNoSurtido: r.motivoNoSurtido ?? '' }));
    this.renglones.set(renglones);
    this.siguienteId = Math.max(0, ...renglones.map((r) => r.id)) + 1;
    if (renglones.length) {
      const escaneadas = renglones.reduce((t, r) => t + r.escaneos.length, 0);
      this.mostrar(`Se recuperó lo que llevabas de la solicitud ${b.folio}: ${renglones.length} medicamento(s) y ${escaneadas} pieza(s) escaneadas.`, 'info');
    }
  }

  // ---------- 0. Solicitudes de Oncología en Intelisis ----------
  private cargarEstado(): void {
    this.api.estadoQuimio().subscribe({ next: (e) => this.estado.set(e), error: () => this.estado.set(null) });
  }

  cargarSolicitudes(): void {
    this.cargandoLista.set(true);
    this.pagSolicitudes.reiniciar();
    this.cargarResumen();
    this.api.solicitudesQuimio(this.filtro(), this.buscarSolicitud(), this.desde(), this.hasta(), this.medico()).subscribe({
      next: (lista) => {
        this.cargandoLista.set(false);
        this.solicitudes.set(lista);
        // Una hoja vieja puede ya no estar en la lista: con el folio se busca directo en Intelisis.
        const texto = this.buscarSolicitud().trim();
        if (!lista.length && /^\d{1,9}$/.test(texto)) this.abrirSolicitud(texto);
      },
      error: (e) => {
        this.cargandoLista.set(false);
        this.solicitudes.set([]);
        this.mostrar(mensajeDeError(e, 'No se pudieron cargar las solicitudes de Oncología.'), 'error');
      },
    });
  }

  cambiarFiltro(filtro: FiltroQuimio): void {
    this.filtro.set(filtro);
    this.cargarSolicitudes();
  }

  /** Llena la solicitud, el paciente y la nota con lo que capturó el médico en Intelisis. */
  abrirSolicitud(folio: string | number): void {
    this.cargandoDetalle.set(true);
    this.api.solicitudQuimio(folio).subscribe({
      next: (d) => {
        this.cargandoDetalle.set(false);
        this.limpiar();
        this.detalle.set(d);
        const s = d.solicitud;
        this.folio.set(String(s.folio));
        this.expediente.set(s.expediente ?? '');
        this.nombreSolicitud.set(s.paciente ?? '');
        this.notaMedico.set(d.notas ?? '');
        this.consultarSesiones();
        // Se recarga la página con una solicitud a medias: se regresa lo que ya se llevaba.
        // Si se abre desde la lista la misma solicitud que estaba a medias, también se retoma.
        if (!this.restaurando && this.enPausa()?.folioIntelisis === String(s.folio)) {
          this.restaurando = this.enPausa();
          this.enPausa.set(null);
        }
        const pendiente = this.restaurando;
        if (pendiente?.folioIntelisis === String(s.folio)) {
          if (!pendiente.paciente && s.expediente && s.paciente) this.paciente.set({ nombre: s.paciente, expediente: s.expediente, curp: null });
          return this.aplicarBorrador(pendiente);
        }
        if (s.expediente && s.paciente) {
          // El paciente viene de Intelisis: el expediente de la solicitud ya trae su nombre.
          this.paciente.set({ nombre: s.paciente, expediente: s.expediente, curp: null });
          this.noExiste.set(false);
        } else {
          this.noExiste.set(true);
          return this.mostrar(`El expediente ${s.expediente ?? '(vacío)'} de la solicitud no existe en Intelisis: busca al paciente por nombre o CURP.`, 'warn');
        }
        const n = d.medicamentos.length;
        this.mostrar(
          n
            ? `Solicitud ${s.folio} cargada de Intelisis. En la nota se encontraron ${n} medicamento(s): confirma la clave de cada uno.`
            : `Solicitud ${s.folio} cargada de Intelisis. No se reconocieron medicamentos en la nota: búscalos por nombre.`,
          n ? 'info' : 'warn',
        );
      },
      error: (e) => {
        this.cargandoDetalle.set(false);
        this.restaurando = null;
        if (e instanceof HttpErrorResponse && e.status === 503) {
          this.capturaManual.set(true);
          return this.mostrar('Intelisis no responde: captura la solicitud a mano con los datos de la hoja.', 'warn');
        }
        this.mostrar(mensajeDeError(e, `No se pudo leer la solicitud ${folio}.`), 'error');
      },
    });
  }

  /** Lo que trae cada frasco expresado en la unidad de la dosis (1 g → 1000 mg); null si no se puede. */
  contenidoEnUnidadDeDosis(m: MedicamentoEnNota, c: ClaveSugerida): number | null {
    if (!c.contenidoPorPieza || c.contenidoPorPieza <= 0 || !c.unidadContenido || !m.dosisUnidad) return null;
    if (c.unidadContenido === m.dosisUnidad) return c.contenidoPorPieza;
    const de = EN_MG[c.unidadContenido];
    const a = EN_MG[m.dosisUnidad];
    return de && a ? (c.contenidoPorPieza * de) / a : null;
  }

  /** Frascos para la dosis de la nota: dosis ÷ lo que trae cada frasco, redondeado hacia arriba. */
  piezasCalculadas(m: MedicamentoEnNota, c: ClaveSugerida): number | null {
    // Soluciones: una bolsa por cada vez que aparece en la nota (diluyentes y lavados).
    if (m.solucion) return m.veces || 1;
    const porPieza = this.contenidoEnUnidadDeDosis(m, c);
    if (!m.dosisCantidad || m.dosisCantidad <= 0 || !porPieza) return null;
    // toFixed evita que 1200 / 1000.0000001 se vuelva 2 por un error de decimales.
    return Math.ceil(+(m.dosisCantidad / porPieza).toFixed(6));
  }

  /**
   * Lo que pasó con este medicamento de la nota en las sesiones anteriores de la misma solicitud:
   * si ya se surtió (en qué sesión, qué clave y cuántas piezas) o si quedó sin surtir y por qué.
   */
  historialDe(m: MedicamentoEnNota): {
    surtido: { sesion: number; fecha: string; clave: string; piezas: number; devuelto: boolean; folioEntrega: string }[];
    noSurtido: { sesion: number; motivo: string }[];
  } {
    const claves = new Set(m.claves.map((c) => c.clave));
    const surtido: { sesion: number; fecha: string; clave: string; piezas: number; devuelto: boolean; folioEntrega: string }[] = [];
    const noSurtido: { sesion: number; motivo: string }[] = [];
    const sinDevolver = this.entregadoSinDevolver();
    for (const s of this.sesiones()?.sesiones ?? []) {
      for (const r of s.renglones) {
        if (!claves.has(r.claveIndicada) && !claves.has(r.claveSurtida)) continue;
        if (r.noSurtido) noSurtido.push({ sesion: s.sesion, motivo: r.motivoNoSurtido ?? '' });
        else surtido.push({ sesion: s.sesion, fecha: s.fecha, clave: r.claveSurtida, piezas: r.piezas, devuelto: !sinDevolver.has(r.claveSurtida), folioEntrega: s.folioEntrega });
      }
    }
    return { surtido, noSurtido };
  }

  /**
   * Claves entregadas en sesiones anteriores de esta solicitud que el paciente no ha devuelto: no se
   * vuelven a surtir. Si se devolvieron (Ventanilla › Devoluciones), se liberan.
   */
  readonly entregadoSinDevolver = computed(() => {
    const mapa = new Map<string, { sesion: number; folioEntrega: string; piezas: number }>();
    for (const s of this.sesiones()?.sesiones ?? []) {
      for (const p of s.partidas ?? []) {
        const pendiente = p.entregada - (p.devuelta ?? 0);
        if (pendiente <= 0) continue;
        const previo = mapa.get(p.clave);
        mapa.set(p.clave, { sesion: s.sesion, folioEntrega: s.folioEntrega, piezas: (previo?.piezas ?? 0) + pendiente });
      }
    }
    return mapa;
  });

  /** Si este medicamento de la nota ya se entregó y no se ha devuelto, no se puede elegir otra vez. */
  bloqueoDe(m: MedicamentoEnNota): { clave: string; sesion: number; folioEntrega: string; piezas: number } | null {
    for (const h of this.historialDe(m).surtido) {
      const info = this.entregadoSinDevolver().get(h.clave);
      if (info) return { clave: h.clave, ...info };
    }
    return null;
  }

  /** Medicamentos de la nota: primero los indicados y al final las alternativas por desabasto. */
  readonly medicamentosNota = computed(() => {
    const todos = this.detalle()?.medicamentos ?? [];
    return [
      ...todos.filter((m) => !m.soloSiNoHay && !m.solucion),
      ...todos.filter((m) => m.solucion && !m.soloSiNoHay),
      ...todos.filter((m) => m.soloSiNoHay),
    ];
  });

  /** Resumen para una sesión que no es la primera: qué ya se surtió y qué falta. */
  readonly resumenSesiones = computed(() => {
    // (en resumenSesiones) Las alternativas por desabasto no cuentan como "falta".
    const medicamentos = (this.detalle()?.medicamentos ?? []).filter((m) => !m.soloSiNoHay && !m.solucion);
    if (!this.sesiones()?.sesiones?.length || !medicamentos.length) return null;
    const surtidos: string[] = [];
    const faltan: string[] = [];
    for (const m of medicamentos) (this.bloqueoDe(m) ? surtidos : faltan).push(m.farmaco);
    return { surtidos, faltan };
  });

  /** La clave de este medicamento que espera sus piezas (para mostrar su nombre completo). */
  claveEsperando(m: MedicamentoEnNota): ClaveSugerida | undefined {
    const pendiente = this.pendientePiezas();
    return pendiente?.farmaco === this.claveNota(m) ? m.claves.find((c) => c.clave === pendiente.clave) : undefined;
  }

  /** Corrige a mano las piezas de un renglón; si no es un entero mayor a 0 se regresa a lo que tenía. */
  corregirPiezas(id: number, input: HTMLInputElement): void {
    const piezas = Number(input.value);
    const actual = this.renglones().find((r) => r.id === id);
    if (!actual) return;
    if (!Number.isInteger(piezas) || piezas <= 0) {
      input.value = String(actual.piezas);
      return this.mostrar('Las piezas deben ser un número entero mayor a 0.', 'error');
    }
    if (piezas !== actual.piezas) this.cambiarRenglon(id, { piezas });
  }


  sumaPiezas(renglones: RenglonQuimio[]): number {
    return renglones.reduce((total, r) => total + r.piezas, 0);
  }

  /** Renglones de la indicación que salieron de este medicamento de la nota. */
  renglonesDe(m: MedicamentoEnNota): RenglonQuimio[] {
    const llave = this.claveNota(m);
    const claves = new Set(m.claves.map((c) => c.clave));
    return this.renglones().filter((r) => (r.deNota ? r.deNota === llave : !m.solucion && claves.has(r.principal.clave)));
  }

  /** Identifica cada renglón de la nota (el mismo fármaco puede salir como solución de otro volumen o como alternativa). */
  claveNota(m: MedicamentoEnNota): string {
    return `${m.farmaco}|${m.dosisCantidad ?? ''}|${m.solucion ? 'S' : ''}|${m.soloSiNoHay ? 'A' : ''}`;
  }

  /** Esta clave ya está agregada desde este renglón de la nota. */
  claveAgregadaEn(agregados: RenglonQuimio[], clave: string): boolean {
    return agregados.some((r) => r.principal.clave === clave);
  }

  /**
   * Tabla de 1 clic: la clave elegida pasa directo a la indicación con la dosis de la nota y las
   * piezas calculadas. Si no se pueden calcular, se piden en el mismo renglón (Enter agrega).
   */
  agregarSugerencia(m: MedicamentoEnNota, c: ClaveSugerida): void {
    const bloqueo = this.bloqueoDe(m);
    if (bloqueo) return this.mostrar(`${m.farmaco} ya se entregó en la sesión ${bloqueo.sesion} (${bloqueo.clave}). Si hay que cambiarlo, registra primero la devolución.`, 'error');
    const piezas = this.piezasCalculadas(m, c);
    if (!piezas) {
      this.pendientePiezas.set({ farmaco: this.claveNota(m), clave: c.clave });
      this.piezasFila.set(null);
      // La cajita de piezas aparece en el renglón; se le pone el cursor para escribir y dar Enter.
      setTimeout(() => document.getElementById('pz-fila')?.focus());
      return;
    }
    this.agregarDeNota(m, c, piezas);
  }

  /** Piezas escritas en el renglón del medicamento (cuando SICOMED no las pudo calcular). */
  confirmarPiezas(m: MedicamentoEnNota): void {
    const pendiente = this.pendientePiezas();
    const clave = pendiente && m.claves.find((c) => c.clave === pendiente.clave);
    if (!clave) return;
    const piezas = this.piezasFila();
    if (!piezas || piezas <= 0 || !Number.isInteger(piezas)) return this.mostrar('Escribe las piezas (frascos o ampolletas) a surtir.', 'error');
    this.agregarDeNota(m, clave, piezas);
    // Si la clave salió de escanear una caja, esa caja ya cuenta como la primera pieza.
    const espera = this.escaneoEnEspera();
    if (espera && espera.clave === clave.clave) {
      this.escaneoEnEspera.set(null);
      this.asignarEscaneo(espera.codigo, espera.gs1, espera.clave);
    }
  }

  /** La caja escaneada es una de las claves posibles de un medicamento de la nota que aún no tiene clave. */
  private notaParaClave(clave: string): { m: MedicamentoEnNota; c: ClaveSugerida } | null {
    for (const m of this.medicamentosNota()) {
      const c = m.claves.find((x) => x.clave === clave);
      if (c && !this.bloqueoDe(m) && !this.renglonesDe(m).length) return { m, c };
    }
    return null;
  }

  /** Escanear la caja elige la clave: se agrega a su medicamento de la nota y la caja cuenta como pieza. */
  private agregarPorEscaneo(m: MedicamentoEnNota, c: ClaveSugerida, codigo: string, gs1: DatosGs1 | null): void {
    const piezas = this.piezasCalculadas(m, c);
    if (piezas) {
      this.agregarDeNota(m, c, piezas);
      return this.asignarEscaneo(codigo, gs1, c.clave);
    }
    this.escaneoEnEspera.set({ codigo, gs1, clave: c.clave });
    this.agregarSugerencia(m, c);
    this.avisoEscaneo('aviso', `Esta caja es ${c.clave} ${c.nombre}: va en ${m.farmaco}. Escribe cuántas piezas se surten y da Enter.`);
  }

  private agregarDeNota(m: MedicamentoEnNota, c: ClaveSugerida, piezas: number): void {
    const dosisNota = m.dosisCantidad ? `${m.dosisCantidad} ${m.dosisUnidad ?? ''}`.trim() : '';
    const unidad = m.dosisUnidad && this.unidades.includes(m.dosisUnidad) ? m.dosisUnidad : 'mg';
    // Una clave por medicamento de la nota: elegir otra la cambia (no se suma). Para combinar
    // presentaciones (ej. 300 mg + 150 mg) se usa "+ Agregar otro medicamento".
    const anteriores = this.renglonesDe(m);
    const quitar = new Set(anteriores.map((r) => r.id));
    this.renglones.update((lista) => [
      ...lista.filter((r) => !quitar.has(r.id)),
      {
        id: this.siguienteId++,
        textoMedico: m.texto.length > 300 ? m.texto.slice(0, 300) : m.texto,
        deNota: this.claveNota(m),
        principal: { clave: c.clave, nombre: c.nombre },
        alternativa: null,
        // Sin dosis en números, queda el renglón de la nota (Farmacia lo revisa antes de registrar).
        dosis: dosisNota || (m.texto.length > 100 ? `${m.texto.slice(0, 100)}…` : m.texto),
        piezas,
        usar: 'PRINCIPAL',
        lote: '',
        motivoAlternativa: '',
        dosisCantidad: m.dosisCantidad,
        dosisUnidad: unidad,
        contenidoPorPieza: this.contenidoEnUnidadDeDosis(m, c),
        destinoSobrante: null,
        escaneos: [],
        sinEscaneo: false,
        noSurtido: false,
        motivoNoSurtido: '',
      },
    ]);
    this.pendientePiezas.set(null);
    this.piezasFila.set(null);
    if (anteriores.length) {
      const antes = anteriores.map((r) => r.principal.clave).join(', ');
      this.mostrar(`${m.farmaco}: se cambió ${antes} por ${c.clave} (${piezas} pz).`, 'info');
    } else {
      this.mensaje.set('');
    }
    const nuevo = this.codigoNuevoEnEspera();
    if (nuevo) {
      this.codigoNuevoEnEspera.set(null);
      this.codigosConocidos.set(nuevo.codigo, c.clave);
      this.asignarEscaneo(nuevo.codigo, nuevo.gs1, c.clave);
    }
  }

  /** Copia lo que se le surtió al paciente la vez anterior (Farmacia lo compara con la nota de hoy). */
  repetirCicloAnterior(): void {
    const anterior = this.detalle()?.cicloAnterior;
    if (!anterior) return;
    const yaEstan = new Set(this.renglones().map((r) => r.principal.clave));
    const nuevos: RenglonQuimio[] = anterior.renglones
      .filter((r) => !yaEstan.has(r.claveIndicada))
      .map((r) => ({
        id: this.siguienteId++,
        textoMedico: r.textoMedico ?? '',
        principal: { clave: r.claveIndicada, nombre: r.nombreIndicada },
        alternativa: r.claveAlternativa ? { clave: r.claveAlternativa, nombre: r.nombreAlternativa ?? '' } : null,
        dosis: r.dosis,
        piezas: r.piezas,
        usar: 'PRINCIPAL',
        lote: '',
        motivoAlternativa: '',
        dosisCantidad: r.dosisCantidad,
        dosisUnidad: r.dosisUnidad ?? 'mg',
        contenidoPorPieza: r.contenidoPorPieza,
        destinoSobrante: null,
        escaneos: [],
        sinEscaneo: false,
        noSurtido: false,
        motivoNoSurtido: '',
      }));
    this.renglones.update((lista) => [...lista, ...nuevos]);
    this.mostrar(
      nuevos.length
        ? `Se copiaron ${nuevos.length} medicamento(s) del ciclo anterior. Compáralos con la nota de hoy: la dosis puede haber cambiado.`
        : 'Lo del ciclo anterior ya está en la indicación.',
      nuevos.length ? 'warn' : 'info',
    );
  }

  /** "hace 3 min" para la última lectura de Intelisis. */
  hace(fecha: string | null): string {
    if (!fecha) return 'sin lectura';
    const minutos = Math.max(0, Math.round((Date.now() - new Date(fecha).getTime()) / 60000));
    return minutos < 1 ? 'leído hace un momento' : minutos < 60 ? `leído hace ${minutos} min` : `leído a las ${new Date(fecha).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })}`;
  }

  private cargarExistencias(): void {
    this.api.existencias().subscribe({ next: (lista) => this.existencias.set(lista), error: () => this.existencias.set([]) });
  }

  // Filtros de "Quimioterapias surtidas": por día (AAAA-MM-DD) y por folio, expediente o nombre.
  readonly diaRecientes = signal('');
  readonly buscarRecientes = signal('');
  readonly cargandoRecientes = signal(false);

  cargarRecientes(): void {
    this.cargandoRecientes.set(true);
    this.api.quimiosRecientes(this.diaRecientes(), this.buscarRecientes()).subscribe({
      next: (lista) => {
        this.cargandoRecientes.set(false);
        this.recientes.set(lista);
      },
      error: () => {
        this.cargandoRecientes.set(false);
        this.recientes.set([]);
      },
    });
  }

  filtrarRecientesPorDia(dia: string): void {
    this.diaRecientes.set(dia);
    this.cargarRecientes();
  }

  limpiarFiltroRecientes(): void {
    this.diaRecientes.set('');
    this.buscarRecientes.set('');
    this.cargarRecientes();
  }

  readonly esNoSurtido = (r: { noSurtido: boolean }) => r.noSurtido;

  hoyIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  /** Desde "Quimioterapias surtidas": abre esa solicitud (para la siguiente sesión) y sube a verla. */
  abrirDesdeRecientes(q: QuimioRegistrada): void {
    if (this.renglones().length) this.volverALista();
    this.abrirSolicitud(q.folio);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ---------- 1. Solicitud y paciente ----------
  verificarPaciente(): void {
    const expediente = this.expediente().trim();
    if (!this.folio().trim() || !expediente) return this.mostrar('Escribe el folio y el expediente de la solicitud.', 'error');
    this.intelisisCaido.set(false);
    this.sinIntelisis.set(false);
    this.consultarSesiones();
    this.buscar('EXPEDIENTE', expediente, true);
  }

  /** Si la solicitud ya se surtió antes (otra sesión o ciclo), se avisa y debe ser el mismo paciente. */
  private consultarSesiones(): void {
    this.sesiones.set(null);
    this.api.sesionesQuimio(this.folio().trim()).subscribe({ next: (s) => this.sesiones.set(s), error: () => this.sesiones.set(null) });
  }

  /** Intelisis no responde: se usa el paciente del expediente físico (queda justificado). */
  usarSinIntelisis(): void {
    const nombre = this.nombreManual().trim();
    if (nombre.length < 5) return this.mostrar('Escribe el nombre completo como viene en el expediente físico.', 'error');
    this.paciente.set({ nombre, expediente: this.expediente().trim(), curp: null });
    this.sinIntelisis.set(true);
    this.expedienteCorregido.set(false);
    this.mostrar('Paciente tomado del expediente físico. Escribe cómo lo confirmaste.', 'warn');
  }

  buscarOtro(): void {
    const texto = this.textoBusqueda().trim();
    if (texto.length < 3) return this.mostrar('Escribe al menos 3 letras para buscar.', 'error');
    this.buscar(this.modoBusqueda(), texto, false);
  }

  private buscar(modo: 'EXPEDIENTE' | ModoBusqueda, valor: string, delaSolicitud: boolean): void {
    this.buscando.set(true);
    this.coincidencias.set([]);
    this.api.buscarPacientes(modo, valor).subscribe({
      next: (lista) => {
        this.buscando.set(false);
        if (!lista.length) {
          if (delaSolicitud) {
            this.paciente.set(null);
            this.noExiste.set(true);
          }
          return this.mostrar(
            delaSolicitud ? `El expediente ${valor} no existe en Intelisis. Busca al paciente por nombre o CURP.` : 'No se encontró ningún paciente.',
            'warn',
          );
        }
        if (delaSolicitud) this.noExiste.set(false);
        if (lista.length === 1) return this.elegirPaciente(lista[0], !delaSolicitud);
        this.coincidencias.set(lista);
        this.mostrar('Hay varios pacientes: elige el correcto.', 'info');
      },
      error: (e) => {
        this.buscando.set(false);
        if (delaSolicitud && e instanceof HttpErrorResponse && e.status === 503) {
          this.intelisisCaido.set(true);
          return this.mostrar('Intelisis no responde. Puedes confirmar al paciente con el expediente físico y justificarlo.', 'warn');
        }
        this.mostrar(mensajeDeError(e, 'No se pudo consultar Intelisis. Revisa la conexión e intenta de nuevo.'), 'error');
      },
    });
  }

  elegirPaciente(paciente: Paciente, corregido: boolean): void {
    // Si se eligió el mismo expediente de la solicitud no es una corrección.
    const esCorreccion = corregido && paciente.expediente?.toUpperCase() !== this.expediente().trim().toUpperCase();
    this.paciente.set(paciente);
    this.coincidencias.set([]);
    this.corrigiendo.set(false);
    this.expedienteCorregido.set(esCorreccion);
    this.justificacionPaciente.set('');
    if (esCorreccion) {
      this.mostrar(`Se usará el expediente ${paciente.expediente} de Intelisis en lugar del ${this.expediente()} de la solicitud.`, 'warn');
    } else if (this.nombreCoincide() === false) {
      this.mostrar('El nombre de la solicitud no coincide con el del expediente en Intelisis. Busca al paciente correcto o justifica.', 'warn');
    } else {
      this.mensaje.set('');
    }
  }

  cambiarPaciente(): void {
    this.paciente.set(null);
    this.expedienteCorregido.set(false);
    this.justificacionPaciente.set('');
    this.sinIntelisis.set(false);
  }

  // ---------- 2. Indicación ----------
  elegirPrincipal(medicamento: Medicamento): void {
    this.principal.set(medicamento);
  }

  elegirAlternativa(medicamento: Medicamento): void {
    this.alternativa.set(medicamento);
    this.buscandoAlternativa.set(false);
  }

  agregarRenglon(): void {
    const principal = this.principal();
    const piezas = this.piezas() ?? this.piezasSugeridas();
    if (!principal) return this.mostrar('Elige la clave del medicamento que indicó el médico.', 'error');
    if (!this.dosis().trim()) return this.mostrar('Escribe la dosis tal como la indicó el médico.', 'error');
    if (!piezas || piezas <= 0 || !Number.isInteger(piezas)) return this.mostrar('Escribe las piezas (frascos o ampolletas) a surtir.', 'error');
    const alternativa = this.alternativa();
    this.renglones.update((lista) => [
      ...lista,
      {
        id: this.siguienteId++,
        textoMedico: this.textoRenglon().trim(),
        principal: { clave: principal.clave, nombre: principal.nombreGenerico },
        alternativa: alternativa ? { clave: alternativa.clave, nombre: alternativa.nombreGenerico } : null,
        dosis: this.dosis().trim(),
        piezas,
        usar: 'PRINCIPAL',
        lote: '',
        motivoAlternativa: '',
        dosisCantidad: this.dosisCantidad(),
        dosisUnidad: this.dosisUnidad(),
        contenidoPorPieza: this.contenidoPorPieza(),
        destinoSobrante: null,
        escaneos: [],
        sinEscaneo: false,
        noSurtido: false,
        motivoNoSurtido: '',
      },
    ]);
    this.textoRenglon.set('');
    this.principal.set(null);
    this.alternativa.set(null);
    this.buscandoAlternativa.set(false);
    this.dosis.set('');
    this.piezas.set(null);
    this.dosisCantidad.set(null);
    this.contenidoPorPieza.set(null);
    this.mensaje.set('');
  }

  quitarRenglon(id: number): void {
    this.renglones.update((lista) => lista.filter((r) => r.id !== id));
  }

  // ---------- 3. Surtido ----------
  cambiarRenglon(id: number, cambios: Partial<RenglonQuimio>): void {
    this.renglones.update((lista) =>
      // Cambiar entre principal y alternativa borra el lote y lo escaneado (es otra clave).
      lista.map((r) => (r.id === id ? { ...r, ...cambios, ...(cambios.usar ? { lote: '', escaneos: [] } : {}) } : r)),
    );
  }

  claveSurtida(r: RenglonQuimio): string {
    return r.usar === 'ALTERNATIVA' && r.alternativa ? r.alternativa.clave : r.principal.clave;
  }

  lotesDe(r: RenglonQuimio): LoteFarmacia[] {
    const clave = this.claveSurtida(r);
    return this.existencias().filter((l) => l.clave === clave && l.estado !== 'VENCIDO');
  }

  existenciaDe(r: RenglonQuimio): number {
    return this.lotesDe(r).reduce((t, l) => t + l.piezas, 0);
  }

  /** Piezas vigentes de la clave principal (para saber si la alternativa necesita motivo). */
  private existenciaPrincipal(r: RenglonQuimio): number {
    return this.existencias().filter((l) => l.clave === r.principal.clave && l.estado !== 'VENCIDO').reduce((t, l) => t + l.piezas, 0);
  }

  /** Se surte la alternativa aunque sí hay de la principal: hay que decir por qué. */
  pideMotivoAlternativa(r: RenglonQuimio): boolean {
    return r.usar === 'ALTERNATIVA' && this.existenciaPrincipal(r) >= r.piezas;
  }

  /** Lo que sobra del último frasco (en la unidad de la dosis); null si no se capturó la dosis. */
  sobrante(r: RenglonQuimio): number | null {
    if (!r.dosisCantidad || !r.contenidoPorPieza) return null;
    return Math.round((r.contenidoPorPieza * r.piezas - r.dosisCantidad) * 1000) / 1000;
  }

  // ---------- Escaneo continuo ----------
  // Se escanean las cajas de todos los medicamentos en cualquier orden (lector o cámara): SICOMED
  // reconoce de qué clave es cada caja por su código, la suma a su renglón (3 de 5) y toma el lote
  // del código GS1; si la caja no trae lote, el que caduca primero.

  /** Cuadritos de la barra de avance (uno por pieza; con muchas piezas se topa para que quepa). */
  cuadros(piezas: number): number[] {
    return Array.from({ length: Math.min(piezas, 30) }, (_, i) => i);
  }

  escaneadas(r: RenglonQuimio): number {
    return r.escaneos.length;
  }

  /** Lo escaneado de un renglón agrupado por lote (para mostrarlo y para registrarlo). */
  lotesEscaneados(r: RenglonQuimio): { lote: string; caducidad: string; piezas: number; codigo: string; fefo: boolean }[] {
    const grupos = new Map<string, { lote: string; caducidad: string; piezas: number; codigo: string; fefo: boolean }>();
    for (const e of r.escaneos) {
      const g = grupos.get(e.lote);
      if (g) {
        g.piezas++;
        g.codigo ||= e.codigo;
        g.fefo &&= e.fefo;
      } else grupos.set(e.lote, { lote: e.lote, caducidad: e.caducidad, piezas: 1, codigo: e.codigo, fefo: e.fefo });
    }
    return [...grupos.values()];
  }

  readonly escaneoCompleto = computed(() => {
    const porEscanear = this.renglones().filter((r) => !r.sinEscaneo && !r.noSurtido);
    return porEscanear.every((r) => this.escaneadas(r) === r.piezas);
  });

  escanearFrasco(): void {
    const crudo = this.codigoFrasco.trim();
    this.codigoFrasco = '';
    if (!crudo) return;
    this.codigoSinClave.set(null);
    const gs1 = leerGs1(crudo);
    const codigo = gs1?.gtin ?? crudo;
    if (gs1?.caducidad && gs1.caducidad < diaLocal()) {
      return this.avisoEscaneo('error', `El lote ${gs1.lote ?? ''} de esta caja está vencido: no se surte. Apártalo para darlo de baja.`);
    }
    const conocida = this.codigosConocidos.get(codigo);
    if (conocida !== undefined) return this.asignarEscaneo(codigo, gs1, conocida);
    this.api.codigoMedicamento(codigo).subscribe({
      next: (c) => {
        this.codigosConocidos.set(codigo, c.clave);
        this.asignarEscaneo(codigo, gs1, c.clave);
      },
      error: (e: HttpErrorResponse) => {
        if (e.status !== 404) return this.avisoEscaneo('error', 'No se pudo consultar el código. Vuelve a escanear la caja.');
        this.codigosConocidos.set(codigo, null);
        this.asignarEscaneo(codigo, gs1, null);
      },
    });
  }

  frascoDesdeCamara(codigo: string): void {
    // La cámara sigue abierta (lectura continua); cada código se procesa igual que con el lector.
    this.codigoFrasco = codigo;
    this.escanearFrasco();
  }

  /** Código que SICOMED no conocía: Farmacia dice de qué medicamento es y queda aprendido al registrar. */
  elegirRenglonParaCodigo(r: RenglonQuimio): void {
    const pendiente = this.codigoSinClave();
    if (!pendiente) return;
    this.codigoSinClave.set(null);
    this.codigosConocidos.set(pendiente.codigo, this.claveSurtida(r));
    this.sumarEscaneo(r, pendiente.codigo, pendiente.gs1);
  }

  /** Quita una pieza escaneada de ese lote (si se escaneó de más o la caja equivocada). */
  quitarEscaneo(r: RenglonQuimio, lote: string): void {
    const i = r.escaneos.map((e) => e.lote).lastIndexOf(lote);
    if (i < 0) return;
    this.cambiarRenglon(r.id, { escaneos: r.escaneos.filter((_, j) => j !== i) });
    this.enfocarEscaner();
  }

  /** "No se puede escanear" (sin código o lector descompuesto): se elige el lote a mano o el que caduca primero. */
  cambiarSinEscaneo(r: RenglonQuimio, sinEscaneo: boolean): void {
    this.cambiarRenglon(r.id, { sinEscaneo, escaneos: [], lote: '' });
  }

  // Motivos rápidos para un medicamento que no se surte; "Otro" deja escribirlo.
  readonly motivosNoSurtido = ['Sin existencia en Farmacia', 'No se maneja en el hospital (el paciente lo compra)'];

  /** Marca (o quita la marca) de "no se surte": se borra lo escaneado y se entrega lo demás. */
  marcarNoSurtido(r: RenglonQuimio, noSurtido: boolean): void {
    const motivo = noSurtido && this.existenciaDe(r) < r.piezas ? this.motivosNoSurtido[0] : '';
    this.cambiarRenglon(r.id, { noSurtido, motivoNoSurtido: motivo, escaneos: [], lote: '', sinEscaneo: false, destinoSobrante: null });
  }

  enfocarEscaner(): void {
    setTimeout(() => document.getElementById('escaner-surtido')?.focus());
  }

  private asignarEscaneo(codigo: string, gs1: DatosGs1 | null, clave: string | null): void {
    const pendientes = this.renglones().filter((r) => !r.sinEscaneo && !r.noSurtido && this.escaneadas(r) < r.piezas);
    if (!clave) {
      // Código nuevo: si su lote solo existe en uno de los medicamentos pendientes, es de ese.
      const porLote = gs1?.lote ? pendientes.filter((r) => this.lotesDe(r).some((l) => mismoLote(l.lote, gs1.lote!))) : [];
      if (porLote.length === 1) return this.sumarEscaneo(porLote[0], codigo, gs1);
      if (!this.renglones().length && this.medicamentosNota().length) {
        this.codigoNuevoEnEspera.set({ codigo, gs1 });
        return this.avisoEscaneo('aviso', 'Este código todavía no está en SICOMED. Elige arriba la clave de este medicamento (revisa su ficha): la caja se cuenta y el código queda guardado.');
      }
      if (!pendientes.length) return this.avisoEscaneo('error', 'Ya está escaneado todo lo de la indicación: esta caja sobra.');
      this.codigoSinClave.set({ codigo, gs1 });
      return this.avisoEscaneo('aviso', 'Este código todavía no está en SICOMED. Elige abajo de qué medicamento es y queda guardado.');
    }
    const deClave = this.renglones().filter((r) => this.claveSurtida(r) === clave);
    if (!deClave.length) {
      const deNota = this.notaParaClave(clave);
      if (deNota) return this.agregarPorEscaneo(deNota.m, deNota.c, codigo, gs1);
      const deAlternativa = this.renglones().find((r) => r.usar === 'PRINCIPAL' && r.alternativa?.clave === clave);
      if (deAlternativa) {
        return this.avisoEscaneo('error', `Esta caja es ${clave}, la alternativa de ${deAlternativa.principal.clave}. Si vas a surtir la alternativa, elígela en su tarjeta.`);
      }
      const dePrincipal = this.renglones().find((r) => r.usar === 'ALTERNATIVA' && r.principal.clave === clave);
      if (dePrincipal) {
        return this.avisoEscaneo('error', `Esta caja es ${clave}, pero en su tarjeta se eligió surtir la alternativa ${dePrincipal.alternativa?.clave}.`);
      }
      return this.avisoEscaneo('error', `Esta caja es ${clave}: no está en la indicación. Revisa que sea el medicamento correcto.`);
    }
    const destino = deClave.find((r) => pendientes.includes(r));
    if (!destino) {
      if (deClave.every((r) => r.noSurtido)) return this.avisoEscaneo('error', `${clave} está marcado como "no se surte": si sí lo vas a dar, quítale esa marca.`);
      if (deClave.every((r) => r.sinEscaneo || r.noSurtido)) return this.avisoEscaneo('error', `${clave} está marcado como "sin escaneo": quítale esa marca para escanearlo.`);
      return this.avisoEscaneo('error', `Ya están todas las piezas de ${clave}: esta caja sobra.`);
    }
    this.sumarEscaneo(destino, codigo, gs1);
  }

  private sumarEscaneo(r: RenglonQuimio, codigo: string, gs1: DatosGs1 | null): void {
    const clave = this.claveSurtida(r);
    const lotes = [...this.lotesDe(r)].sort((a, b) => a.caducidad.localeCompare(b.caducidad));
    const usadas = (lote: string) => r.escaneos.filter((e) => mismoLote(e.lote, lote)).length;
    let lote: LoteFarmacia | undefined;
    if (gs1?.lote) {
      lote = lotes.find((l) => mismoLote(l.lote, gs1.lote!));
      if (!lote) return this.avisoEscaneo('error', `El lote ${gs1.lote} de ${clave} no está en el inventario de Farmacia (o está vencido).`);
      if (usadas(lote.lote) >= lote.piezas) return this.avisoEscaneo('error', `Del lote ${lote.lote} solo hay ${lote.piezas} pieza(s) en Farmacia.`);
    } else {
      // La caja no trae lote (código de producto normal): el que caduca primero con piezas disponibles.
      lote = lotes.find((l) => usadas(l.lote) < l.piezas);
      if (!lote) return this.avisoEscaneo('error', `No hay existencia de ${clave} en Farmacia.`);
    }
    // Una caja cuenta lo que falta, hasta una caja completa (sin pasar de lo que hay en ese lote).
    const cuantas = Math.max(1, Math.min(this.catalogo.piezasPorCaja(clave), r.piezas - r.escaneos.length, lote.piezas - usadas(lote.lote)));
    const pieza = { lote: lote.lote, caducidad: lote.caducidad, codigo, fefo: !gs1?.lote };
    const escaneos = [...r.escaneos, ...Array.from({ length: cuantas }, () => ({ ...pieza }))];
    this.cambiarRenglon(r.id, { escaneos });
    const nombre = `${clave} ${r.usar === 'ALTERNATIVA' && r.alternativa ? r.alternativa.nombre : r.principal.nombre}`;
    const sinLote = gs1?.lote ? '' : ' (la caja no trae lote: se tomó el que caduca primero)';
    if (escaneos.length < r.piezas) {
      return this.avisoEscaneo('ok', `${nombre}: +${cuantas} (${escaneos.length} de ${r.piezas}) · lote ${lote.lote}${sinLote}`);
    }
    const restantes = this.renglones().filter((x) => !x.sinEscaneo && this.escaneadas(x) < x.piezas).length;
    this.avisoEscaneo('completo', restantes
      ? `✓ ${nombre}: completo (${r.piezas} de ${r.piezas}). Faltan ${restantes} medicamento(s).`
      : '✓ Todo escaneado. Ya puedes registrar la entrega.');
  }

  private avisoEscaneo(tipo: 'ok' | 'completo' | 'error' | 'aviso', texto: string): void {
    this.ultimoEscaneo.set({ tipo, texto });
    pitido(tipo === 'aviso' ? 'error' : tipo);
    this.enfocarEscaner();
  }

  // ---------- 4. Registro ----------

  // ---------- Pasos de arriba y confirmación antes de registrar ----------
  readonly pasos = computed(() => {
    const paciente = this.pacienteListo();
    const renglones = this.renglones();
    const porEscanear = renglones.filter((r) => !r.sinEscaneo && !r.noSurtido);
    const total = porEscanear.reduce((t, r) => t + r.piezas, 0);
    const hechas = porEscanear.reduce((t, r) => t + Math.min(r.escaneos.length, r.piezas), 0);
    const surtido = renglones.length > 0 && this.escaneoCompleto();
    const faltan = this.faltanParaEntregar().length;
    const lista = [
      { n: 1, titulo: 'Paciente', detalle: paciente ? 'Confirmado' : 'Por confirmar', hecho: paciente },
      { n: 2, titulo: 'Indicación', detalle: renglones.length ? `${renglones.length} medicamento(s) en clave` : 'Pasar a clave', hecho: renglones.length > 0 },
      { n: 3, titulo: 'Surtir', detalle: total ? `${hechas} de ${total} piezas` : 'Escanear cajas', hecho: surtido },
      { n: 4, titulo: 'Registrar', detalle: faltan ? `Falta ${faltan} cosa(s)` : 'Listo para registrar', hecho: false },
    ];
    const actual = lista.find((p) => !p.hecho)?.n ?? 4;
    return lista.map((p) => ({ ...p, actual: p.n === actual }));
  });

  readonly confirmando = signal(false);
  // El paso 1 se esconde cuando el paciente ya está confirmado; se puede volver a abrir desde la columna.
  readonly paso1Abierto = signal(false);

  /** Antes de registrar se muestra el resumen de lo que se entrega. */
  pedirConfirmacion(): void {
    if (this.faltanParaEntregar().length || this.guardando()) return;
    this.confirmando.set(true);
  }

  registrar(): void {
    this.confirmando.set(false);
    const paciente = this.paciente();
    if (!paciente || this.faltanParaEntregar().length || this.guardando()) return;
    this.guardando.set(true);
    this.api
      .registrarQuimio({
        folio: this.folio().trim(),
        expedienteSolicitud: this.expediente().trim(),
        nombreSolicitud: this.nombreSolicitud().trim(),
        paciente,
        expedienteCorregido: this.expedienteCorregido(),
        justificacionPaciente: this.justificacionPaciente().trim(),
        notaMedico: this.notaMedico().trim(),
        renglones: this.renglones().map((r) => ({
          textoMedico: r.textoMedico,
          clave: r.principal.clave,
          alternativa: r.alternativa?.clave ?? null,
          usarAlternativa: r.usar === 'ALTERNATIVA',
          motivoAlternativa: r.usar === 'ALTERNATIVA' ? r.motivoAlternativa.trim() || null : null,
          dosis: r.dosis,
          piezas: r.piezas,
          lote: r.sinEscaneo && !r.noSurtido ? r.lote || null : null,
          noSurtido: r.noSurtido,
          motivoNoSurtido: r.noSurtido ? r.motivoNoSurtido.trim() : null,
          lotes: r.sinEscaneo || r.noSurtido ? null : this.lotesEscaneados(r).map((l) => ({ lote: l.lote, piezas: l.piezas, codigo: l.codigo || null })),
          dosisCantidad: r.dosisCantidad,
          dosisUnidad: r.dosisCantidad ? r.dosisUnidad : null,
          contenidoPorPieza: r.contenidoPorPieza,
          destinoSobrante: (this.sobrante(r) ?? 0) > 0 ? r.destinoSobrante : null,
        })),
        justificacionSinIntelisis: this.sinIntelisis() ? this.justificacionSinIntelisis().trim() : null,
      })
      .subscribe({
        next: (registrada) => {
          this.guardando.set(false);
          this.registrada.set(registrada);
          this.limpiar();
          this.cargarExistencias();
          this.cargarRecientes();
          this.cargarBajaDia();
          this.cargarSolicitudes();
          this.mostrar(`Quimioterapia registrada: solicitud ${registrada.folio}, sesión ${registrada.sesion}, entrega ${registrada.folioEntrega}.`, 'ok');
        },
        error: (e) => {
          this.guardando.set(false);
          this.mostrar(mensajeDeError(e, 'No se pudo registrar la quimioterapia.'), 'error');
        },
      });
  }

  private limpiar(): void {
    this.detalle.set(null);
    this.capturaManual.set(false);
    this.corrigiendo.set(false);
    this.folio.set('');
    this.expediente.set('');
    this.nombreSolicitud.set('');
    this.noExiste.set(false);
    this.cambiarPaciente();
    this.sesiones.set(null);
    this.intelisisCaido.set(false);
    this.nombreManual.set('');
    this.justificacionSinIntelisis.set('');
    this.notaMedico.set('');
    this.renglones.set([]);
    this.pendientePiezas.set(null);
    this.formularioAbierto.set(false);
    this.paso1Abierto.set(false);
    this.confirmando.set(false);
    this.ultimoEscaneo.set(null);
    this.codigoSinClave.set(null);
    this.camara.set(false);
  }

  /** Aviso flotante. Si el error habla de una devolución, trae el botón para ir a Devoluciones. */
  private mostrar(texto: string, tipo: TipoAviso, accion?: { texto: string; ruta: string }): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
    const conAccion = accion ?? (tipo === 'error' && /devoluci/i.test(texto) ? { texto: 'Ir a Devoluciones', ruta: '/farmacia/dispensacion/devoluciones' } : undefined);
    const titulos: Record<TipoAviso, string> = { ok: 'Listo', info: 'Aviso', warn: 'Revisa antes de seguir', error: 'No se pudo' };
    // El mismo texto seguido no se repite: se reemplaza.
    const sinRepetir = this.avisos().filter((a) => a.texto !== texto);
    const aviso: Aviso = { id: this.siguienteAviso++, tipo, titulo: titulos[tipo], texto, accion: conAccion };
    this.avisos.set([...sinRepetir, aviso].slice(-4));
    if (tipo === 'ok' || tipo === 'info') setTimeout(() => this.cerrarAviso(aviso.id), 6000);
  }
}
