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
}

// HU25
export interface PartidaAltaInicial {
  clave: string;
  lote: string;
  caducidad: string;
  piezas: number;
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

  // ---------- Trazabilidad (HU33) ----------
  trazabilidadPorLote(lote: string): Observable<Trazabilidad> {
    return this.http.get<Trazabilidad>(`${this.base}/trazabilidad`, { ...this.cabeceras(), params: { lote } });
  }

  trazabilidadPorExpediente(expediente: string): Observable<Trazabilidad> {
    return this.http.get<Trazabilidad>(`${this.base}/trazabilidad`, { ...this.cabeceras(), params: { expediente } });
  }
}
