import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import { Session } from '../auth/session';

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
  // Códigos de barras de la caja ligados a la clave (del alta inicial); son los que se escanean al surtir.
  codigos: string[];
  // Estante donde está en Farmacia.
  ubicacion: string | null;
}

// HU25: conteo del inventario inicial (V41). Primero se registran los códigos y lotes de cada
// estante, luego se capturan las piezas y al final se guarda todo junto, una sola vez.
// Área del conteo inicial: Almacén cuenta cajas y Farmacia piezas.
export type AreaConteo = 'almacen' | 'farmacia';

export interface RenglonConteo {
  id: number;
  codigo: string;
  clave: string;
  medicamento: string;
  piezasPorCaja: number | null;
  lote: string;
  caducidad: string;
  // Estante; null si se registró sin estante.
  ubicacion: string | null;
  // Piezas. null = todavía no se cuenta; 0 = no había.
  cantidad: number | null;
  registradoPor: string;
  registradoEn: string;
  contadoPor: string | null;
  contadoEn: string | null;
}

export interface EstadoConteo {
  area: string;
  // true cuando ya se guardó: ya no se puede contar ni dar de alta inicial.
  cerrado: boolean;
  cerradoEn: string | null;
  cerradoPor: string | null;
  totalRenglones: number;
  sinCantidad: number;
  renglones: RenglonConteo[];
}

// Caja escaneada. La clave solo se manda cuando el backend dijo que el código es nuevo (404).
// Lote que Intelisis tiene en existencia de una clave (Almacén y Farmacia juntos), para elegirlo.
export interface LoteIntelisisConteo {
  lote: string;
  // null: Intelisis no tiene su caducidad; se escribe a mano.
  caducidad: string | null;
  // null: no está en Intelisis, ya se registró a mano en el conteo.
  piezas: number | null;
}

export interface NuevoRenglonConteo {
  codigo: string;
  clave?: string;
  lote: string;
  caducidad: string;
  ubicacion?: string;
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
  // Lo que Farmacia ya tiene de esa clave y en qué estante (null: es nueva en Farmacia).
  piezasEnFarmacia: number | null;
  ubicacionSugerida: string | null;
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
  // Estado de esa receta en SICOMED; null si todavía no se registra.
  estadoEnHaero: EstadoReceta | null;
  partidas: PartidaRecetaIntelisis[];
  // Médico que hizo la receta y, si es otro, su médico adscrito.
  medico: string | null;
  medicoAdscrito: string | null;
  // Estatus en Intelisis: por folio sale aunque no esté firmada (FIRMADO, SINFIRMAR…).
  estatus: string | null;
}

export interface EntregaRequest {
  folioReceta: string;
  paciente?: Paciente;
  claves: { clave: string; piezasIndicadas?: number; noDispensada: boolean; motivo?: string }[];
  // Sin lote (el código no es GS1) el backend toma el lote que sale primero (HU30).
  cajas: { clave: string; lote?: string; piezas: number }[];
  // Si al paciente ya se le entregó alguna de estas claves en los últimos 7 días.
  justificacionRepetida?: string;
}

/** Lo que ya se le entregó al paciente de una clave en los últimos 7 días (cualquier área). */
export interface EntregaPrevia {
  fecha: string;
  folioEntrega: string;
  receta: string;
  area: string;
  clave: string;
  medicamento: string;
  piezas: number;
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
  // A medias y con más de 30 días (vigencia): ya no se surte lo que faltó.
  vencida: boolean;
  // Veces que se resurtió con el mismo folio y fecha de la última entrega.
  resurtimientos: number;
  ultimaEntrega: string | null;
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
  // De dónde salió el dato; null si no hay registro.
  fuente: 'HAERO' | 'Intelisis' | null;
}

// Lote de un medicamento con lo que saben SICOMED e Intelisis (existencias en sus unidades).
export interface LoteTrazable {
  clave: string;
  medicamento: string | null;
  lote: string;
  // Sufijos de Intelisis (origen del recurso: SCC, UNOPS, FONSABI...).
  sufijos: string | null;
  caducidad: string | null;
  piezasFarmacia: number | null;
  existenciaAlmacenIntelisis: number | null;
  existenciaFarmaciaIntelisis: number | null;
  enHaero: boolean;
  enIntelisis: boolean;
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
  // SICOMED (entrega registrada en SICOMED) o Intelisis (baja de Farmacia anterior a SICOMED).
  fuente: 'HAERO' | 'Intelisis';
  // Médico de la receta (solo SICOMED).
  medico?: string | null;
  // Fuente de financiamiento de la baja de Intelisis (PEPS): "SAEAC · Donacion FONSABI 953 · orden …".
  financiamiento?: string | null;
}

/** Periodo de las entregas a pacientes (aaaa-mm-dd, ambos días incluidos). */
export interface PeriodoTrazabilidad {
  desde?: string;
  hasta?: string;
}

export interface Trazabilidad {
  modo: 'LOTE' | 'EXPEDIENTE' | 'CURP' | 'NOMBRE' | 'RECETA';
  valor: string;
  recorrido: PasoRecorrido[];
  entregas: EntregaPaciente[];
  // Aviso de la consulta (p. ej. Intelisis no respondió); null si todo bien.
  aviso: string | null;
  // Solo por lote: compras de Intelisis con las que llegó y existencia de hoy en Intelisis.
  compras: CompraTrazabilidad[];
  existencias: ExistenciaTrazabilidad[];
  // Solo por lote (script de almacén): donaciones FONSABI/UNOPS con fuente, orden, remisión y rango de piezas PEPS.
  financiamiento?: EntradaFinanciamiento[];
}

export interface EntradaFinanciamiento {
  articulo: string;
  lote: string;
  entrada: string;
  // null = directa; "Conversion N" o "Cambio de lote X".
  via: string | null;
  fechaLlegada: string | null;
  cantidad: number;
  piezaDesde: number;
  piezaHasta: number;
  caducidad: string | null;
  fuente: string;
  ordenSuministro: string | null;
  remision: string | null;
  origenRemision: string | null;
  contrato: string | null;
  proveedor: string | null;
  concepto: string | null;
  costo: number | null;
  documentoPdf: string | null;
  entregaMismoDia: boolean;
  cartaCanje: string | null;
}

export interface CompraTrazabilidad {
  fecha: string | null;
  compra: string | null;
  origen: string | null;
  contrato: number | null;
  proveedor: string | null;
  clave: string;
  cantidad: number;
  caducidad: string | null;
  almacen: string | null;
}

export interface ExistenciaTrazabilidad {
  almacen: string;
  tipo: 'Almacén' | 'Farmacia';
  clave: string;
  cantidad: number;
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
  // Escaneo continuo: piezas por lote (deben sumar "piezas"); si viene, manda sobre "lote".
  lotes: { lote: string | null; piezas: number; codigo: string | null }[] | null;
  // No se entrega (sin existencia, o no se maneja y el paciente lo compra); lo demás sí.
  noSurtido: boolean;
  motivoNoSurtido: string | null;
  motivoParcial?: string | null;
  // Medicamento de la nota del que sale el renglón (para validar que sus claves sean del mismo laboratorio).
  grupo?: string | null;
}

// A qué clave pertenece el código de barras de una caja.
export interface CodigoMedicamento {
  codigo: string;
  clave: string;
  nombre: string;
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
  // Medicamentos de la nota: ENTREGADO, PENDIENTE (no se surtió) o POR_SURTIR.
  medicamentos: { nombre: string; estado: 'ENTREGADO' | 'PENDIENTE' | 'POR_SURTIR' }[];
  pendientes: number;
  // Si Oncología dio otra fecha: la de Intelisis (fechaRequerida ya es la nueva) y el motivo.
  fechaOriginal: string | null;
  motivoReprogramacion: string | null;
}

// Números del día para los recuadros y las pastillas de filtro.
export interface ResumenQuimio {
  porSurtir: number;
  paraHoy: number;
  conPendientes: number;
  surtidasHoy: number;
}

// Clave del catálogo que puede ser la que escribió el médico (SICOMED solo sugiere; Farmacia confirma).
export interface ClaveSugerida {
  clave: string;
  nombre: string;
  forma: string | null;
  concentracion: string | null;
  contenidoPorPieza: number | null;
  unidadContenido: string | null;
  existencia: number;
  usadaAntes: boolean;
  // Laboratorios (proveedores) de la clave según sus códigos de barras; varias claves de un medicamento solo si comparten uno.
  proveedores?: string[];
}

export interface MedicamentoEnNota {
  texto: string;
  farmaco: string;
  dosisCantidad: number | null;
  dosisUnidad: string | null;
  claves: ClaveSugerida[];
  // Alternativa que solo se da si falta lo indicado ("EN CASO DE DESABASTO DE siNoHayDe DAR…").
  soloSiNoHay: boolean;
  siNoHayDe: string | null;
  // Diluyente o lavado (cloruro de sodio, glucosa…) junto por volumen; veces = bolsas.
  solucion: boolean;
  veces: number;
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

export type FiltroQuimio = 'POR_SURTIR' | 'HOY' | 'CON_PENDIENTES' | 'SURTIDAS' | 'TODAS' | 'HISTORIAL';

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
  // Solo en quimios registradas cuando había doble verificación.
  verifico: string | null;
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
    noSurtido: boolean;
    motivoNoSurtido: string | null;
    motivoParcial?: string | null;
  }[];
  partidas: { clave: string; medicamento: string; lote: string; caducidad: string; entregada: number; devuelta: number }[] | null;
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

  // ---------- Inventario inicial por conteo (HU25) ----------
  // El mismo conteo sirve a Almacén (en cajas) y a Farmacia (en piezas); cada rol solo puede usar el suyo.
  conteoInicial(area: AreaConteo = 'farmacia'): Observable<EstadoConteo> {
    return this.http.get<EstadoConteo>(`${API_BASE_URL}/${area}/conteo-inicial`, this.cabeceras());
  }

  /** Pasa el renglón a otro estante ('' = sin estante); si ahí ya estaba el lote, se juntan y se suman las piezas. */
  moverEnConteo(id: number, ubicacion: string, area: AreaConteo = 'farmacia'): Observable<RenglonConteo> {
    return this.http.put<RenglonConteo>(`${API_BASE_URL}/${area}/conteo-inicial/renglones/${id}/ubicacion`, { ubicacion }, this.cabeceras());
  }

  /** Cambia el nombre de un estante ('' = sin estante); los lotes que ya estén en el nuevo se juntan. */
  renombrarEstante(actual: string, nuevo: string, area: AreaConteo = 'farmacia'): Observable<EstadoConteo> {
    return this.http.put<EstadoConteo>(`${API_BASE_URL}/${area}/conteo-inicial/estantes`, { actual, nuevo }, this.cabeceras());
  }

  /** Clave de un código: del catálogo o de lo ya escaneado en el conteo de Almacén o de Farmacia. */
  claveEnConteo(codigo: string, area: AreaConteo = 'farmacia'): Observable<{ clave: string; nombre: string }> {
    return this.http.get<{ clave: string; nombre: string }>(`${API_BASE_URL}/${area}/conteo-inicial/codigos/${encodeURIComponent(codigo)}`, this.cabeceras());
  }

  lotesParaConteo(clave: string, area: AreaConteo = 'farmacia'): Observable<LoteIntelisisConteo[]> {
    return this.http.get<LoteIntelisisConteo[]>(`${API_BASE_URL}/${area}/conteo-inicial/lotes`, { ...this.cabeceras(), params: { clave } });
  }

  /** Escanear otra vez el mismo lote en el mismo estante no lo duplica: regresa el mismo renglón. */
  registrarEnConteo(nuevo: NuevoRenglonConteo, area: AreaConteo = 'farmacia'): Observable<RenglonConteo> {
    return this.http.post<RenglonConteo>(`${API_BASE_URL}/${area}/conteo-inicial/renglones`, nuevo, this.cabeceras());
  }

  /** Verificación antes de guardar: corrige clave, lote o caducidad (en Almacén la caducidad sale de Intelisis). */
  corregirRenglonConteo(id: number, correccion: { clave: string; lote: string; caducidad: string | null }, area: AreaConteo = 'farmacia'): Observable<RenglonConteo> {
    return this.http.put<RenglonConteo>(`${API_BASE_URL}/${area}/conteo-inicial/renglones/${id}`, correccion, this.cabeceras());
  }

  contarRenglon(id: number, cantidad: number, area: AreaConteo = 'farmacia'): Observable<RenglonConteo> {
    return this.http.put<RenglonConteo>(`${API_BASE_URL}/${area}/conteo-inicial/renglones/${id}/cantidad`, { cantidad }, this.cabeceras());
  }

  quitarDelConteo(id: number, area: AreaConteo = 'farmacia'): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/${area}/conteo-inicial/renglones/${id}`, this.cabeceras());
  }

  /** Da de alta todo el conteo (todo o nada) y cierra el inventario inicial para siempre. */
  guardarInventarioInicial(area: AreaConteo = 'farmacia'): Observable<EstadoConteo> {
    return this.http.post<EstadoConteo>(`${API_BASE_URL}/${area}/conteo-inicial/cerrar`, {}, this.cabeceras());
  }

  // ---------- Recepción (HU26) ----------
  porRecibir(): Observable<DespachoPorRecibir[]> {
    return this.http.get<DespachoPorRecibir[]>(`${this.base}/recepciones/pendientes`, this.cabeceras());
  }

  recibir(solicitudId: number, partidas: { movimientoId: number; cajasRecibidas: number }[], motivo: string, ubicacion: string): Observable<Recepcion> {
    return this.http.post<Recepcion>(`${this.base}/recepciones`, { solicitudId, partidas, motivo, ubicacion }, this.cabeceras());
  }

  /** Estantes que ya existen en Farmacia (para sugerirlos al escribir). */
  estantesFarmacia(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/existencias/estantes`, this.cabeceras());
  }

  /** Pasa un lote a otro estante (queda quién, cuándo y de dónde a dónde). */
  cambiarEstante(loteId: number, ubicacion: string): Observable<LoteFarmacia> {
    return this.http.put<LoteFarmacia>(`${this.base}/existencias/${loteId}/ubicacion`, { ubicacion }, this.cabeceras());
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

  entregasPrevias(expediente: string, claves: string[], excluirReceta?: string): Observable<EntregaPrevia[]> {
    const params: Record<string, string> = { expediente, claves: claves.join(',') };
    if (excluirReceta) params['excluirReceta'] = excluirReceta;
    return this.http.get<EntregaPrevia[]>(`${this.base}/entregas/previas`, { ...this.cabeceras(), params });
  }

  registrarEntrega(entrega: EntregaRequest): Observable<EntregaRegistrada> {
    return this.http.post<EntregaRegistrada>(`${this.base}/entregas`, entrega, this.cabeceras());
  }

  recetas(estados = 'PARCIAL,NO_DISPENSADA'): Observable<Receta[]> {
    return this.http.get<Receta[]>(`${this.base}/recetas`, { ...this.cabeceras(), params: { estado: estados } });
  }

  /** Reabre una receta de Ventanilla ya surtida con las mismas cantidades (queda el motivo, quién y cuándo). */
  resurtirReceta(folio: string, motivo: string): Observable<Receta> {
    return this.http.post<Receta>(`${this.base}/recetas/${encodeURIComponent(folio)}/resurtir`, { motivo }, this.cabeceras());
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
  /** Enlaza el código de la caja a una clave que se dio de alta sin código. */
  enlazarCodigo(codigo: string, clave: string): Observable<void> {
    return this.http.post<void>(`${this.base}/codigos`, { codigo, clave }, this.cabeceras());
  }

  /** Clave a la que pertenece un código de barras de caja; error 404 si todavía no se conoce. */
  codigoMedicamento(codigo: string): Observable<CodigoMedicamento> {
    return this.http.get<CodigoMedicamento>(`${this.base}/codigos/${encodeURIComponent(codigo)}`, this.cabeceras());
  }

  registrarQuimio(datos: RegistrarQuimio): Observable<QuimioRegistrada> {
    return this.http.post<QuimioRegistrada>(`${this.base}/quimioterapia`, datos, this.cabeceras());
  }

  sesionesQuimio(folio: string): Observable<SesionesSolicitud> {
    return this.http.get<SesionesSolicitud>(`${this.base}/quimioterapia/solicitudes/${encodeURIComponent(folio)}`, this.cabeceras());
  }

  /** Surtidas: sin filtros, las últimas 30; dia = AAAA-MM-DD; buscar = folio, expediente o nombre. */
  quimiosRecientes(dia = '', buscar = ''): Observable<QuimioRegistrada[]> {
    const params: Record<string, string> = { ...(dia ? { dia } : {}), ...(buscar.trim() ? { buscar: buscar.trim() } : {}) };
    return this.http.get<QuimioRegistrada[]>(`${this.base}/quimioterapia`, { ...this.cabeceras(), params });
  }

  /** Historial de cualquier año (Intelisis en el momento): solo consulta y reprogramar. buscar: folio o expediente. */
  historialQuimio(buscar = '', desde = '', hasta = ''): Observable<SolicitudQuimio[]> {
    const params: Record<string, string> = {
      ...(buscar.trim() ? { buscar: buscar.trim() } : {}),
      ...(desde ? { desde } : {}),
      ...(hasta ? { hasta } : {}),
    };
    return this.http.get<SolicitudQuimio[]>(`${this.base}/quimioterapia/historial`, { ...this.cabeceras(), params });
  }

  /** Solicitudes de Oncología Médica leídas de Intelisis. buscar: folio de la hoja, expediente o nombre. */
  solicitudesQuimio(filtro: FiltroQuimio, buscar = '', desde = '', hasta = '', medico = ''): Observable<SolicitudQuimio[]> {
    const params: Record<string, string> = {
      filtro,
      ...(buscar.trim() ? { buscar: buscar.trim() } : {}),
      ...(desde ? { desde } : {}),
      ...(hasta ? { hasta } : {}),
      ...(medico ? { medico } : {}),
    };
    return this.http.get<SolicitudQuimio[]>(`${this.base}/quimioterapia/intelisis`, { ...this.cabeceras(), params });
  }

  /** Números del día: por surtir, para hoy, con pendientes y surtidas hoy. */
  resumenQuimio(): Observable<ResumenQuimio> {
    return this.http.get<ResumenQuimio>(`${this.base}/quimioterapia/resumen`, this.cabeceras());
  }

  /** Médicos con solicitudes de quimio en los últimos 6 meses. */
  medicosQuimio(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/quimioterapia/medicos`, this.cabeceras());
  }

  /** El paciente no puede pasar: nueva fecha (AAAA-MM-DD) que dio Oncología Médica y el motivo. */
  reprogramarQuimio(folio: number, fechaNueva: string, motivo: string): Observable<SolicitudQuimio> {
    return this.http.post<SolicitudQuimio>(`${this.base}/quimioterapia/solicitudes/${folio}/reprogramar`, { fechaNueva, motivo }, this.cabeceras());
  }

  /** Datos extra de Intelisis para la hoja "Solicitud de procedimiento" (vacío si Intelisis no responde). */
  formatoQuimio(folio: string | number): Observable<Record<string, string>> {
    return this.http.get<Record<string, string>>(`${this.base}/quimioterapia/intelisis/${encodeURIComponent(String(folio))}/formato`, this.cabeceras());
  }

  solicitudQuimio(folio: string | number): Observable<SolicitudQuimioDetalle> {
    return this.http.get<SolicitudQuimioDetalle>(`${this.base}/quimioterapia/intelisis/${encodeURIComponent(String(folio))}`, this.cabeceras());
  }

  estadoQuimio(): Observable<EstadoQuimio> {
    return this.http.get<EstadoQuimio>(`${this.base}/quimioterapia/estado`, this.cabeceras());
  }


  // ---------- Trazabilidad (HU33) ----------
  trazabilidadPorLote(lote: string, clave?: string, periodo?: PeriodoTrazabilidad): Observable<Trazabilidad> {
    const params: Record<string, string> = clave ? { lote, clave } : { lote };
    return this.http.get<Trazabilidad>(`${this.base}/trazabilidad`, { ...this.cabeceras(), params: { ...params, ...periodo } });
  }

  /** Lotes de un medicamento (clave o nombre) o de un número de lote, en SICOMED y en Intelisis. */
  trazabilidadLotes(q: string): Observable<LoteTrazable[]> {
    return this.http.get<LoteTrazable[]>(`${this.base}/trazabilidad/lotes`, { ...this.cabeceras(), params: { q } });
  }

  /** Por nombre del paciente o por su CURP. */
  trazabilidadPorPaciente(paciente: string, periodo?: PeriodoTrazabilidad): Observable<Trazabilidad> {
    return this.http.get<Trazabilidad>(`${this.base}/trazabilidad`, { ...this.cabeceras(), params: { paciente, ...periodo } });
  }

  /** Por folio de receta de Intelisis: la receta y lo que Farmacia le dio al paciente para surtirla. */
  trazabilidadPorReceta(receta: string): Observable<Trazabilidad> {
    return this.http.get<Trazabilidad>(`${this.base}/trazabilidad`, { ...this.cabeceras(), params: { receta } });
  }

  trazabilidadPorExpediente(expediente: string, periodo?: PeriodoTrazabilidad): Observable<Trazabilidad> {
    return this.http.get<Trazabilidad>(`${this.base}/trazabilidad`, { ...this.cabeceras(), params: { expediente, ...periodo } });
  }
}
