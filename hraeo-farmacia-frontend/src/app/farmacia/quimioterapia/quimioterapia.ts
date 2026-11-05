import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { EscanerCamara } from '../../shared/escaner-camara/escaner-camara';
import { MarcaSeguridad } from '../../shared/marca-seguridad/marca-seguridad';
import { leerGs1 } from '../../shared/gs1';
import { Medicamento, mensajeDeError } from '../../almacen/almacen-api';
import { BuscadorMedicamento } from '../buscador-medicamento/buscador-medicamento';
import {
  ClaveSugerida,
  EstadoQuimio,
  FarmaciaApi,
  FiltroQuimio,
  LoteFarmacia,
  MedicamentoEnNota,
  Paciente,
  QuimioRegistrada,
  SesionesSolicitud,
  SolicitudQuimio,
  SolicitudQuimioDetalle,
} from '../farmacia-api';
import { HttpErrorResponse } from '@angular/common/http';
import { FARMACIA_MENU } from '../farmacia-nav';
import { CajasQuirofano, EstadoBajaArea } from '../cajas-quirofano/cajas-quirofano';

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
}

export const UNIDADES_DOSIS = ['mg', 'mcg', 'g', 'UI', 'ml'];

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
// diagnóstico, el médico y la nota tal como la escribió. La nota no trae claves: HAERO sugiere las
// del catálogo y Farmacia las confirma (o repite las del ciclo anterior del paciente). Se surte cada
// frasco (escaneado o eligiendo el lote) y otra persona verifica con su usuario antes de registrar.
// Se descuenta como una entrega normal de Farmacia. Intelisis solo se consulta.
@Component({
  selector: 'app-quimioterapia',
  imports: [FormsModule, DatePipe, Topbar, Sidebar, EscanerCamara, MarcaSeguridad, BuscadorMedicamento],
  templateUrl: './quimioterapia.html',
  styleUrls: ['../panel-farmacia/panel-farmacia.css', '../farmacia-comun.css', './quimioterapia.css'],
})
export class Quimioterapia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;
  private readonly api = inject(FarmaciaApi);

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'info' | 'error' | 'warn'>('info');

  // ---------- 0. Solicitudes de Oncología en Intelisis ----------
  readonly estado = signal<EstadoQuimio | null>(null);
  readonly filtro = signal<FiltroQuimio>('POR_SURTIR');
  readonly buscarSolicitud = signal('');
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
    { valor: 'SURTIDAS', etiqueta: 'Ya surtidas' },
    { valor: 'TODAS', etiqueta: 'Todas' },
  ];

  // ---------- 1. Solicitud y paciente ----------
  readonly folio = signal('');
  readonly expediente = signal('');
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
  private siguienteId = 1;
  readonly clavesEnLista = computed(() => this.renglones().flatMap((r) => [r.principal.clave, ...(r.alternativa ? [r.alternativa.clave] : [])]));

  // ---------- 3. Surtido ----------
  codigoFrasco = '';
  readonly renglonActivo = signal<number | null>(null);
  readonly camara = signal(false);
  readonly existencias = signal<LoteFarmacia[]>([]);

  // ---------- 4. Doble verificación ----------
  readonly verificadorUsuario = signal('');
  readonly verificadorContrasena = signal('');
  readonly checks = signal({ paciente: false, medicamentos: false, dosis: false, lotes: false });
  readonly guardando = signal(false);
  readonly registrada = signal<QuimioRegistrada | null>(null);
  readonly recientes = signal<QuimioRegistrada[]>([]);

  readonly faltanParaEntregar = computed(() => {
    const faltan: string[] = [];
    if (!this.pacienteListo()) faltan.push('verificar al paciente');
    if (!this.renglones().length) faltan.push('transcribir la indicación');
    for (const r of this.renglones()) {
      if (this.pideMotivoAlternativa(r) && !r.motivoAlternativa.trim()) faltan.push(`el motivo de surtir la alternativa de ${r.principal.clave}`);
      if ((this.sobrante(r) ?? 0) > 0 && !r.destinoSobrante) faltan.push(`qué se hace con el sobrante de ${this.claveSurtida(r)}`);
      if (this.sobrante(r) !== null && this.sobrante(r)! < 0) faltan.push(`más piezas de ${this.claveSurtida(r)} (no alcanza la dosis)`);
    }
    if (!this.verificadorUsuario().trim() || !this.verificadorContrasena()) faltan.push('el usuario y contraseña de quien verifica');
    if (!Object.values(this.checks()).every(Boolean)) faltan.push('marcar toda la lista de verificación');
    return faltan;
  });

  // ---------- Baja del día (como en Hospitalización) ----------
  private readonly bajas = inject(CajasQuirofano);
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

  imprimirHojasPacientes(): void {
    window.open('/farmacia/hospitalizacion/imprimir/bajas?central=QUIMIO', '_blank');
  }

  constructor() {
    this.cargarEstado();
    this.cargarSolicitudes();
    this.cargarExistencias();
    this.cargarRecientes();
    this.cargarBajaDia();
  }

  // ---------- 0. Solicitudes de Oncología en Intelisis ----------
  private cargarEstado(): void {
    this.api.estadoQuimio().subscribe({ next: (e) => this.estado.set(e), error: () => this.estado.set(null) });
  }

  cargarSolicitudes(): void {
    this.cargandoLista.set(true);
    this.api.solicitudesQuimio(this.filtro(), this.buscarSolicitud()).subscribe({
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
        if (e instanceof HttpErrorResponse && e.status === 503) {
          this.capturaManual.set(true);
          return this.mostrar('Intelisis no responde: captura la solicitud a mano con los datos de la hoja.', 'warn');
        }
        this.mostrar(mensajeDeError(e, `No se pudo leer la solicitud ${folio}.`), 'error');
      },
    });
  }

  cerrarSolicitud(): void {
    this.limpiar();
    this.mensaje.set('');
  }

  /** Lleva la clave sugerida al formulario, con la dosis que escribió el médico; Farmacia revisa y la agrega. */
  usarSugerencia(m: MedicamentoEnNota, c: ClaveSugerida): void {
    this.principal.set({ clave: c.clave, nombreGenerico: c.nombre });
    this.alternativa.set(null);
    this.buscandoAlternativa.set(false);
    this.textoRenglon.set(m.texto.length > 300 ? m.texto.slice(0, 300) : m.texto);
    this.dosis.set(m.dosisCantidad ? `${m.dosisCantidad} ${m.dosisUnidad ?? ''}`.trim() : '');
    this.dosisCantidad.set(m.dosisCantidad);
    if (m.dosisUnidad) this.dosisUnidad.set(m.dosisUnidad);
    // Lo que trae cada frasco, si el catálogo lo dice en la misma unidad que la dosis.
    this.contenidoPorPieza.set(c.contenidoPorPieza && c.unidadContenido === this.dosisUnidad() ? c.contenidoPorPieza : null);
    this.piezas.set(null);
    this.mostrar(`${c.clave} ${c.nombre}: revisa la dosis y las piezas y agrégalo a la indicación.`, 'info');
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

  private cargarRecientes(): void {
    this.api.quimiosRecientes().subscribe({ next: (lista) => this.recientes.set(lista), error: () => this.recientes.set([]) });
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
      lista.map((r) => (r.id === id ? { ...r, ...cambios, ...(cambios.usar ? { lote: '' } : {}) } : r)),
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

  escanearFrasco(): void {
    const id = this.renglonActivo();
    const gs1 = leerGs1(this.codigoFrasco);
    this.codigoFrasco = '';
    if (id === null) return this.mostrar('Primero elige en la tabla qué medicamento vas a escanear.', 'error');
    if (!gs1?.lote) return this.mostrar('El código no trae lote: elige el lote de la lista.', 'warn');
    if (gs1.caducidad && gs1.caducidad < new Date().toISOString().slice(0, 10)) {
      return this.mostrar(`El lote ${gs1.lote} está vencido: no se surte.`, 'error');
    }
    const renglon = this.renglones().find((r) => r.id === id);
    if (renglon && !this.lotesDe(renglon).some((l) => l.lote.toUpperCase() === gs1.lote!.toUpperCase())) {
      return this.mostrar(`El lote ${gs1.lote} no está en el inventario de Farmacia para ${this.claveSurtida(renglon)}.`, 'error');
    }
    this.cambiarRenglon(id, { lote: gs1.lote });
    this.mostrar(`Lote ${gs1.lote} registrado.`, 'info');
  }

  frascoDesdeCamara(codigo: string): void {
    this.camara.set(false);
    this.codigoFrasco = codigo;
    this.escanearFrasco();
  }

  // ---------- 4. Verificación y registro ----------
  marcar(campo: 'paciente' | 'medicamentos' | 'dosis' | 'lotes', valor: boolean): void {
    this.checks.update((actual) => ({ ...actual, [campo]: valor }));
  }

  registrar(): void {
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
          lote: r.lote || null,
          dosisCantidad: r.dosisCantidad,
          dosisUnidad: r.dosisCantidad ? r.dosisUnidad : null,
          contenidoPorPieza: r.contenidoPorPieza,
          destinoSobrante: (this.sobrante(r) ?? 0) > 0 ? r.destinoSobrante : null,
        })),
        verificadorUsuario: this.verificadorUsuario().trim(),
        verificadorContrasena: this.verificadorContrasena(),
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
          this.mostrar(`Quimioterapia registrada (${registrada.folioEntrega}). Surtió ${registrada.surtio}, verificó ${registrada.verifico}.`, 'info');
        },
        error: (e) => {
          this.guardando.set(false);
          this.verificadorContrasena.set('');
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
    this.renglonActivo.set(null);
    this.verificadorUsuario.set('');
    this.verificadorContrasena.set('');
    this.checks.set({ paciente: false, medicamentos: false, dosis: false, lotes: false });
  }

  private mostrar(texto: string, tipo: 'info' | 'error' | 'warn'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
