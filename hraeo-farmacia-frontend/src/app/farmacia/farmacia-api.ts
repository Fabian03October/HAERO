import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import { Session } from '../auth/session';
import { Medicamento } from '../almacen/almacen-api';

// Tipos copiados de los DTOs del backend (com.example.backend.farmacia.dto).
// Los nombres de campo deben coincidir letra por letra con los del backend.
// Farmacia cuenta en piezas; las cajas son de referencia.

export type EstadoLoteFarmacia = 'FEFO' | 'ESPERA' | 'VENCIDO';

// HU27: lote del inventario de Farmacia.
export interface LoteFarmacia {
  id: number;
  clave: string;
  medicamento: string;
  piezasPorCaja: number | null;
  lote: string;
  caducidad: string;
  piezas: number;
  cajas: number | null;
  estado: EstadoLoteFarmacia;
  porCaducar: boolean;
  loteAlmacenId: number | null;
}

// HU25
export interface PartidaAltaInicial {
  clave: string;
  lote: string;
  caducidad: string;
  piezas: number;
  // HU42: código de barras de la caja; si es nuevo, queda ligado a la clave.
  codigo?: string;
}

// HU26
export interface PartidaPorRecibir {
  movimientoId: number;
  lote: string;
  caducidad: string;
  cajas: number;
  fechaDespacho: string;
  despachadoPor: string;
}

export interface DespachoPorRecibir {
  solicitudId: number;
  fechaSolicitud: string;
  clave: string;
  medicamento: string;
  piezasPorCaja: number | null;
  cajasPedidas: number;
  cajasDespachadas: number;
  partidas: PartidaPorRecibir[];
}

export interface Recepcion {
  id: number;
  solicitudId: number;
  fecha: string;
  usuario: string;
  clave: string;
  medicamento: string;
  cajasDespachadas: number;
  cajasRecibidas: number;
  piezasRecibidas: number;
  diferencia: number;
  motivo: string | null;
}

// HU28
export interface Paciente {
  nombre: string;
  expediente: string | null;
  curp: string | null;
}

// Lo que un paciente ya recibió (EntregasRecientesController.EntregaReciente).
export interface EntregaReciente {
  clave: string;
  medicamento: string;
  fecha: string;
  piezas: number;
  folioEntrega: string;
  folioReceta: string;
  despachador: string;
}

// Receta firmada en Intelisis (solo folio, fecha, origen, clave y cantidad).
export interface PartidaRecetaIntelisis {
  clave: string | null;
  medicamento: string;
  piezasPorCaja: number | null;
  cantidad: number;
  enCatalogo: boolean;
}

export interface RecetaIntelisis {
  folio: string | null;
  // ID_Receta de Intelisis; la búsqueda por folio acepta este número o el folio.
  idReceta: string;
  fecha: string | null;
  origen: string | null;
  paciente: Paciente;
  // Estado de esa receta en HAERO; null si todavía no se registra.
  estadoEnHaero: EstadoReceta | null;
  partidas: PartidaRecetaIntelisis[];
  // Médico que hizo la receta y, si es otro, su médico adscrito.
  medico: string | null;
  medicoAdscrito: string | null;
}

export interface EntregaRequest {
  folioReceta: string;
  paciente?: Paciente;
  claves: { clave: string; piezasIndicadas?: number; noDispensada: boolean; motivo?: string }[];
  // Sin lote (el código no es GS1) el backend toma el lote que sale primero (HU30).
  cajas: { clave: string; lote?: string; piezas: number }[];
}

export type EstadoReceta = 'SURTIDA' | 'PARCIAL' | 'NO_DISPENSADA';

export interface ClaveReceta {
  clave: string;
  medicamento: string;
  piezasPorCaja: number | null;
  indicada: number;
  entregada: number;
  pendiente: number;
  noDispensada: boolean;
  motivo: string | null;
}

export interface Receta {
  folio: string;
  expediente: string | null;
  paciente: string;
  curp: string | null;
  fecha: string;
  despachador: string;
  estado: EstadoReceta;
  claves: ClaveReceta[];
}

export interface PartidaEntregada {
  partidaId: number;
  clave: string;
  medicamento: string;
  lote: string;
  caducidad: string;
  entregada: number;
  devuelta: number;
}

export interface EntregaRegistrada {
  id: number | null;
  folio: string | null;
  fecha: string;
  partidas: PartidaEntregada[];
  receta: Receta;
}

export interface ClavePendiente {
  clave: string;
  medicamento: string;
  piezas: number;
  recetas: number;
}

// HU34
export interface EntregaEncontrada {
  id: number;
  folio: string;
  receta: string;
  expediente: string | null;
  paciente: string;
  fecha: string;
  despachador: string;
  partidas: PartidaEntregada[];
}

export interface Devolucion {
  id: number;
  folioEntrega: string;
  clave: string;
  lote: string;
  piezas: number;
  danado: boolean;
  fecha: string;
  enResumen: 'RESTA' | 'DEVOLUCION';
  diaResumen: string;
  // Paciente de la entrega que se devolvió.
  paciente: string;
  expediente: string | null;
}

// HU35
export type MotivoBaja = 'CADUCIDAD' | 'DANO' | 'EXTRAVIO';

export interface Baja {
  id: number;
  clave: string;
  medicamento: string;
  lote: string;
  caducidad: string;
  piezas: number;
  motivo: MotivoBaja;
  detalle: string | null;
  fecha: string;
  usuario: string;
  deDevolucion: boolean;
}

// HU31 y HU32
export type EstadoDia = 'PENDIENTE' | 'DADO_DE_BAJA' | 'VERIFICADO';

export interface RenglonResumen {
  claveIntelisis: string;
  clave: string;
  medicamento: string;
  lote: string;
  piezas: number;
  piezasPorCaja: number | null;
  pacientes: number;
}

export interface RenglonBaja {
  claveIntelisis: string;
  lote: string;
  piezas: number;
  motivo: string;
}

export interface ResumenArea {
  area: string;
  nombre: string;
  piezas: number;
  claves: number;
  pacientes: number;
  entregasSinBaja: number;
  foliosBaja: string;
}

export interface CorteDia {
  dia: string;
  estado: EstadoDia;
  folioIntelisis: string | null;
  marcadoEn: string | null;
  marcadoPor: string | null;
  cambiosDespuesDeLaBaja: number;
  consumo: RenglonResumen[];
  devoluciones: RenglonResumen[];
  bajas: RenglonBaja[];
  totalClaves: number;
  totalPiezas: number;
  totalPacientes: number;
  diasPendientes: string[];
  // Hospitalización, Quimioterapia y Quirófano: se capturan con la baja de su área, no con esta.
  areas: ResumenArea[];
}

export type ResultadoVerificacion = 'CUADRA' | 'FALTA' | 'DE_MAS';

export interface RenglonVerificacion {
  claveIntelisis: string;
  lote: string;
  haero: number;
  intelisis: number;
  diferencia: number;
  resultado: ResultadoVerificacion;
}

export interface Verificacion {
  dia: string;
  estado: EstadoDia;
  sincronizadoEn: string | null;
  renglones: RenglonVerificacion[];
}

// HU33
export interface PasoRecorrido {
  etapa: string;
  fecha: string | null;
  detalle: string;
}

export interface EntregaPaciente {
  fecha: string;
  folio: string;
  paciente: string;
  expediente: string | null;
  clave: string;
  lote: string;
  piezas: number;
  despachador: string;
}

export interface Trazabilidad {
  modo: 'LOTE' | 'EXPEDIENTE';
  valor: string;
  recorrido: PasoRecorrido[];
  entregas: EntregaPaciente[];
}

// Quimioterapia (com.example.backend.farmacia.dto.QuimioterapiaDtos). Sin lote, sale el FEFO.
export interface RenglonQuimioRequest {
  textoMedico: string;
  clave: string;
  alternativa: string | null;
  usarAlternativa: boolean;
  // Obligatorio si se surte la alternativa y sí hay existencia de la principal.
  motivoAlternativa: string | null;
  dosis: string;
  piezas: number;
  lote: string | null;
  // Opcionales: dosis (ej. 280 mg) contra lo que trae cada frasco (ej. 300 mg) para el sobrante.
  dosisCantidad: number | null;
  dosisUnidad: string | null;
  contenidoPorPieza: number | null;
  destinoSobrante: 'MERMA' | 'RESGUARDO' | null;
}

// Solicitud de Oncología Médica leída de Intelisis. folio = el "Folio" de la hoja impresa.
export interface SolicitudQuimio {
  folio: number;
  fecha: string | null;
  fechaRequerida: string | null;
  estatus: string | null;
  expediente: string | null;
  paciente: string | null;
  servicioSolicita: string | null;
  medico: string | null;
  sesionesSurtidas: number;
  ultimaSesion: string | null;
}

// Clave del catálogo que puede ser la que escribió el médico (HAERO solo sugiere; Farmacia confirma).
export interface ClaveSugerida {
  clave: string;
  nombre: string;
  forma: string | null;
  concentracion: string | null;
  contenidoPorPieza: number | null;
  unidadContenido: string | null;
  existencia: number;
  usadaAntes: boolean;
}

export interface MedicamentoEnNota {
  texto: string;
  farmaco: string;
  dosisCantidad: number | null;
  dosisUnidad: string | null;
  claves: ClaveSugerida[];
}

export interface SolicitudQuimioDetalle {
  solicitud: SolicitudQuimio;
  diagnostico: string | null;
  notas: string | null;
  medicamentos: MedicamentoEnNota[];
  // Lo último que se le surtió de quimioterapia al paciente (para repetir el ciclo).
  cicloAnterior: QuimioRegistrada | null;
}

export interface EstadoQuimio {
  intelisisDisponible: boolean;
  leyendo: boolean;
  ultimaLectura: string | null;
  estatus: string | null;
  error: string | null;
  solicitudes: number;
}

export type FiltroQuimio = 'POR_SURTIR' | 'HOY' | 'SURTIDAS' | 'TODAS';

// Sesiones ya surtidas de una solicitud (una solicitud puede cubrir varios ciclos).
export interface SesionesSolicitud {
  folio: string;
  pacienteExpediente: string | null;
  pacienteNombre: string | null;
  sesiones: QuimioRegistrada[];
}

export interface RegistrarQuimio {
  folio: string;
  expedienteSolicitud: string;
  nombreSolicitud: string;
  paciente: Paciente;
  expedienteCorregido: boolean;
  justificacionPaciente: string;
  notaMedico: string;
  renglones: RenglonQuimioRequest[];
  verificadorUsuario: string;
  verificadorContrasena: string;
  // Si Intelisis no responde: cómo se confirmó al paciente (expediente físico).
  justificacionSinIntelisis: string | null;
}

export interface QuimioRegistrada {
  id: number;
  folio: string;
  sesion: number;
  expedienteSolicitud: string;
  pacienteNombre: string;
  pacienteExpediente: string;
  expedienteCorregido: boolean;
  justificacionPaciente: string | null;
  sinIntelisis: boolean;
  notaMedico: string | null;
  folioEntrega: string;
  fecha: string;
  surtio: string;
  verifico: string;
  renglones: {
    textoMedico: string | null;
    claveIndicada: string;
    claveAlternativa: string | null;
    claveSurtida: string;
    medicamento: string;
    dosis: string;
    piezas: number;
    motivoAlternativa: string | null;
    dosisCantidad: number | null;
    dosisUnidad: string | null;
    contenidoPorPieza: number | null;
    sobrante: number | null;
    destinoSobrante: string | null;
    nombreIndicada: string;
    nombreAlternativa: string | null;
  }[];
  partidas: { clave: string; medicamento: string; lote: string; caducidad: string; entregada: number }[] | null;
}

// Llamadas a /api/farmacia (Iteración 3). Supervisión solo puede usar las de consulta (GET).
@Injectable({ providedIn: 'root' })
export class FarmaciaApi {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);
  private readonly base = `${API_BASE_URL}/farmacia`;

  private cabeceras() {
    return { headers: { Authorization: `Bearer ${this.session.obtenerToken()}` } };
  }

  // ---------- Inventario (HU25, HU27, HU30) ----------
  existencias(): Observable<LoteFarmacia[]> {
    return this.http.get<LoteFarmacia[]>(`${this.base}/existencias`, this.cabeceras());
  }

  /** Lote que sale primero de la clave; null si no hay existencia disponible. */
  loteFefo(clave: string): Observable<LoteFarmacia | null> {
    return this.http.get<LoteFarmacia | null>(`${this.base}/existencias/fefo`, { ...this.cabeceras(), params: { clave } });
  }

  altaInicial(partidas: PartidaAltaInicial[]): Observable<{ lotes: number; piezas: number }> {
    return this.http.post<{ lotes: number; piezas: number }>(`${this.base}/alta-inicial`, { partidas }, this.cabeceras());
  }

  // ---------- Recepción (HU26) ----------
  porRecibir(): Observable<DespachoPorRecibir[]> {
    return this.http.get<DespachoPorRecibir[]>(`${this.base}/recepciones/pendientes`, this.cabeceras());
  }

  recibir(solicitudId: number, partidas: { movimientoId: number; cajasRecibidas: number }[], motivo: string): Observable<Recepcion> {
    return this.http.post<Recepcion>(`${this.base}/recepciones`, { solicitudId, partidas, motivo }, this.cabeceras());
  }

  recepciones(conDiferencia = false): Observable<Recepcion[]> {
    return this.http.get<Recepcion[]>(`${this.base}/recepciones`, { ...this.cabeceras(), params: { conDiferencia } });
  }

  // ---------- Entregas (HU28, HU29) ----------
  buscarPacientes(modo: 'EXPEDIENTE' | 'NOMBRE' | 'CURP', valor: string): Observable<Paciente[]> {
    const params: Record<string, string> = { [modo.toLowerCase()]: valor };
    return this.http.get<Paciente[]>(`${this.base}/pacientes`, { ...this.cabeceras(), params });
  }

  /** Lo que el paciente recibió en Farmacia en los últimos días (para no entregar dos veces). */
  entregasRecientes(expediente: string, dias = 30): Observable<EntregaReciente[]> {
    return this.http.get<EntregaReciente[]>(`${this.base}/pacientes/${encodeURIComponent(expediente)}/entregas-recientes`, {
      ...this.cabeceras(),
      params: { dias },
    });
  }

  recetasDelPaciente(expediente: string): Observable<RecetaIntelisis[]> {
    return this.http.get<RecetaIntelisis[]>(`${this.base}/pacientes/${encodeURIComponent(expediente)}/recetas`, this.cabeceras());
  }

  recetaDeIntelisis(folio: string): Observable<RecetaIntelisis> {
    return this.http.get<RecetaIntelisis>(`${this.base}/recetas-intelisis/${encodeURIComponent(folio)}`, this.cabeceras());
  }

  registrarEntrega(entrega: EntregaRequest): Observable<EntregaRegistrada> {
    return this.http.post<EntregaRegistrada>(`${this.base}/entregas`, entrega, this.cabeceras());
  }

  recetas(estados = 'PARCIAL,NO_DISPENSADA'): Observable<Receta[]> {
    return this.http.get<Receta[]>(`${this.base}/recetas`, { ...this.cabeceras(), params: { estado: estados } });
  }

  receta(folio: string): Observable<Receta> {
    return this.http.get<Receta>(`${this.base}/recetas/${encodeURIComponent(folio)}`, this.cabeceras());
  }

  clavesPendientes(): Observable<ClavePendiente[]> {
    return this.http.get<ClavePendiente[]>(`${this.base}/recetas/claves-pendientes`, this.cabeceras());
  }

  // ---------- Devoluciones (HU34) ----------
  buscarEntregas(buscar: string): Observable<EntregaEncontrada[]> {
    return this.http.get<EntregaEncontrada[]>(`${this.base}/entregas`, { ...this.cabeceras(), params: { buscar } });
  }

  registrarDevolucion(entregaId: number, motivo: string, partidas: { partidaId: number; piezas: number; danado: boolean }[]): Observable<Devolucion[]> {
    return this.http.post<Devolucion[]>(`${this.base}/devoluciones`, { entregaId, motivo, partidas }, this.cabeceras());
  }

  devolucionesDelDia(dia?: string): Observable<Devolucion[]> {
    const params: Record<string, string> = dia ? { dia } : {};
    return this.http.get<Devolucion[]>(`${this.base}/devoluciones`, { ...this.cabeceras(), params });
  }

  // ---------- Bajas (HU35) ----------
  bajas(): Observable<Baja[]> {
    return this.http.get<Baja[]>(`${this.base}/bajas`, this.cabeceras());
  }

  sugeridosParaBaja(): Observable<LoteFarmacia[]> {
    return this.http.get<LoteFarmacia[]>(`${this.base}/bajas/sugeridas`, this.cabeceras());
  }

  registrarBaja(loteId: number, piezas: number, motivo: MotivoBaja, detalle: string): Observable<Baja> {
    return this.http.post<Baja>(`${this.base}/bajas`, { loteId, piezas, motivo, detalle }, this.cabeceras());
  }

  // ---------- Baja en Intelisis (HU31, HU32) ----------
  corte(dia: string): Observable<CorteDia> {
    return this.http.get<CorteDia>(`${this.base}/cortes/${dia}`, this.cabeceras());
  }

  marcarDadoDeBaja(dia: string, folioIntelisis: string): Observable<CorteDia> {
    return this.http.post<CorteDia>(`${this.base}/cortes/${dia}/baja`, { folioIntelisis }, this.cabeceras());
  }

  verificar(dia: string): Observable<Verificacion> {
    return this.http.post<Verificacion>(`${this.base}/cortes/${dia}/verificar`, {}, this.cabeceras());
  }

  csvDelCorte(dia: string): Observable<Blob> {
    return this.http.get(`${this.base}/cortes/${dia}/csv`, { ...this.cabeceras(), responseType: 'blob' });
  }

  // ---------- Seguridad del paciente: alto riesgo y red fría ----------
  /** altoRiesgo null = volver a lo que dice Intelisis. */
  // ---------- Quimioterapia ----------
  registrarQuimio(datos: RegistrarQuimio): Observable<QuimioRegistrada> {
    return this.http.post<QuimioRegistrada>(`${this.base}/quimioterapia`, datos, this.cabeceras());
  }

  sesionesQuimio(folio: string): Observable<SesionesSolicitud> {
    return this.http.get<SesionesSolicitud>(`${this.base}/quimioterapia/solicitudes/${encodeURIComponent(folio)}`, this.cabeceras());
  }

  quimiosRecientes(): Observable<QuimioRegistrada[]> {
    return this.http.get<QuimioRegistrada[]>(`${this.base}/quimioterapia`, this.cabeceras());
  }

  /** Solicitudes de Oncología Médica leídas de Intelisis. buscar: folio de la hoja, expediente o nombre. */
  solicitudesQuimio(filtro: FiltroQuimio, buscar = ''): Observable<SolicitudQuimio[]> {
    const params: Record<string, string> = { filtro, ...(buscar.trim() ? { buscar: buscar.trim() } : {}) };
    return this.http.get<SolicitudQuimio[]>(`${this.base}/quimioterapia/intelisis`, { ...this.cabeceras(), params });
  }

  solicitudQuimio(folio: string | number): Observable<SolicitudQuimioDetalle> {
    return this.http.get<SolicitudQuimioDetalle>(`${this.base}/quimioterapia/intelisis/${encodeURIComponent(String(folio))}`, this.cabeceras());
  }

  estadoQuimio(): Observable<EstadoQuimio> {
    return this.http.get<EstadoQuimio>(`${this.base}/quimioterapia/estado`, this.cabeceras());
  }

  actualizarSeguridad(clave: string, altoRiesgo: boolean | null, redFria: boolean): Observable<Medicamento> {
    return this.http.put<Medicamento>(`${this.base}/medicamentos/${encodeURIComponent(clave)}/seguridad`, { altoRiesgo, redFria }, this.cabeceras());
  }

  // ---------- Trazabilidad (HU33) ----------
  trazabilidadPorLote(lote: string): Observable<Trazabilidad> {
    return this.http.get<Trazabilidad>(`${this.base}/trazabilidad`, { ...this.cabeceras(), params: { lote } });
  }

  /** Por nombre del paciente o por su CURP. */
  trazabilidadPorPaciente(paciente: string): Observable<Trazabilidad> {
    return this.http.get<Trazabilidad>(`${this.base}/trazabilidad`, { ...this.cabeceras(), params: { paciente } });
  }

  trazabilidadPorExpediente(expediente: string): Observable<Trazabilidad> {
    return this.http.get<Trazabilidad>(`${this.base}/trazabilidad`, { ...this.cabeceras(), params: { expediente } });
  }
}
