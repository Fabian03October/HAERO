import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import { Session } from '../auth/session';

// Tipos copiados de los DTOs del backend (com.example.backend.almacen.dto).
// Los nombres de campo deben coincidir letra por letra con los del backend.
// Las fechas LocalDate llegan como 'AAAA-MM-DD' y LocalDateTime como 'AAAA-MM-DDTHH:mm:ss'.

export interface Medicamento {
  clave: string;
  nombreGenerico: string;
  presentacion: string;
  piezasPorCaja: number | null;
  descripcion: string | null;
  // Datos de Intelisis; vienen vacíos en las claves capturadas a mano en HAERO.
  formaFarmaceutica: string | null;
  concentracion: string | null;
  grupoTerapeutico: string | null;
  cuadroBasico: boolean | null;
  estatus: string | null; // ALTA o BAJA
  claveCucop: string | null;
  unidadCompra: string | null;
  unidadMinima: string | null;
  riesgo: string | null;
  principalIndicacion: string | null;
  contraindicaciones: string | null;
  descripcionCompleta: string | null;
  origen: 'HAERO' | 'INTELISIS';
  sincronizadoEn: string | null;
  // Dada de alta en HAERO = ya tiene código de barras; solo esas entran al inventario.
  dadoDeAlta: boolean;
  codigosBarras: number;
}

export interface Existencia {
  existenciaId: number;
  clave: string;
  nombreGenerico: string;
  loteId: number;
  numeroLote: string;
  caducidad: string;
  ubicacion: string;
  cajas: number;
  // true en las existencias del lote que se debe despachar primero (HU15).
  fefo: boolean;
  proveedor: string;
  // Registro de Intelisis del que salió el lote y día en que entró a HAERO.
  movimientoIntelisis: string | null;
  fechaIngreso: string | null;
}

export interface FiltroExistencias {
  clave?: string;
  lote?: string;
  ubicacion?: string;
}

export type EstatusPedido = 'ACTIVO' | 'CANCELADO';

// Estatus del pedido en Intelisis (los pedidos se leen de allá; en HAERO no se capturan).
export type EstatusPedidoIntelisis = 'PENDIENTE' | 'CONCLUIDO' | 'CANCELADO';

export interface PartidaPedido {
  clave: string;
  nombreGenerico: string;
  // Lo esperado según Intelisis (sin lo cancelado).
  cantidadEsperada: number;
  // Lo que Almacén recibió físicamente en HAERO.
  cantidadRecibida: number;
  cucop: string | null;
  // Lo que dice Intelisis: recibido allá y cancelado.
  recibidaIntelisis: number | null;
  canceladaIntelisis: number | null;
}

export interface Pedido {
  id: number;
  numeroPedido: string;
  proveedor: string;
  estatus: EstatusPedido;
  fecha: string;
  partidas: PartidaPedido[];
  motivoCancelacion: string | null;
  origen: 'HAERO' | 'INTELISIS';
  contrato: string | null;
  fechaEntrega: string | null;
  estatusIntelisis: EstatusPedidoIntelisis | null;
  fechaCancelacion: string | null;
}

export interface UbicacionCantidad {
  ubicacion: string;
  cajas: number;
}

// COMPRA (Ent. Compra), INV (donación o traspaso) o EXISTENCIA (alta inicial).
export type OrigenRegistroIntelisis = 'COMPRA' | 'INV' | 'EXISTENCIA';

// ENTRADA (HU10) o ALTA_INICIAL (HU21, lo que ya está en los anaqueles).
export type TipoCapturaIntelisis = 'ENTRADA' | 'ALTA_INICIAL';

// Registro de Intelisis que Almacén elige al recibir: lote, caducidad y cantidades son los de Intelisis.
export interface RegistroIntelisis {
  origen: OrigenRegistroIntelisis;
  idIntelisis: number | null;
  movimiento: string;
  referencia: string | null;
  fecha: string | null;
  clave: string;
  lote: string;
  caducidad: string | null;
  piezas: number | null;
  // null si no se sabe cuántas piezas trae la caja.
  cajas: number | null;
  proveedor: string | null;
  pedidoId: number | null;
  numeroPedido: string | null;
  contrato: string | null;
  // Por qué no se puede recibir; null si se puede.
  bloqueo: string | null;
  loteHaeroId: number | null;
}

export interface RegistrarEntradaRequest {
  codigo: string;
  clave: string;
  origen: OrigenRegistroIntelisis;
  idIntelisis: number | null;
  lote: string;
  ubicaciones: UbicacionCantidad[];
  observaciones?: string;
}

export interface RegistrarEntradaResponse {
  loteId: number;
  numeroLote: string;
  caducidad: string;
  existencias: UbicacionCantidad[];
  movimientoIntelisis: string | null;
  cajasIntelisis: number | null;
  cajasConfirmadas: number;
}

export interface ResolverCodigoResponse {
  clave: string;
  nombre: string;
  proveedor: string;
}


// CANCELADA (Farmacia ya no lo necesita) y RECHAZADA (Almacén no puede surtirlo): HU40.
export type EstatusSolicitud = 'PENDIENTE' | 'PARCIAL' | 'ATENDIDA' | 'CANCELADA' | 'RECHAZADA';

/** Una solicitud que todavía espera despacho. */
export function solicitudAbierta(estatus: EstatusSolicitud): boolean {
  return estatus === 'PENDIENTE' || estatus === 'PARCIAL';
}

export interface Solicitud {
  id: number;
  clave: string;
  nombreGenerico: string;
  cantidadSolicitada: number;
  cantidadAtendida: number;
  estatus: EstatusSolicitud;
  fecha: string;
  // Quién de Farmacia la hizo: nombre para mostrar y usuario (para marcar las propias).
  solicitadoPor: string | null;
  solicitadoPorUsuario: string | null;
  // HU40: solo si se canceló o rechazó.
  motivoCierre: string | null;
  cerradaPor: string | null;
  fechaCierre: string | null;
  // HU39: pedido al que pertenece; null en las de una sola clave anteriores.
  pedidoId: number | null;
}

export interface DespachoPedido {
  pedidoId: number;
  despachadas: DespacharSolicitudResponse[];
  // Claves sin existencia que quedaron pendientes (solo en entrega parcial).
  sinExistencia: string[];
}

export interface SugerenciaPartida {
  existenciaId: number;
  loteId: number;
  numeroLote: string;
  caducidad: string;
  ubicacion: string;
  cajasDisponibles: number;
  cajasSugeridas: number;
}

export interface SugerenciaDespacho {
  solicitudId: number;
  cantidadPendiente: number;
  partidas: SugerenciaPartida[];
}

export interface DespacharSolicitudRequest {
  partidas: { existenciaId: number; cajas: number }[];
  // true para confirmar una entrega menor a lo pendiente (HU14).
  parcial: boolean;
  // Ajuste HU15: true cuando el despachador confirmó explícitamente que
  // quiere tomar un lote fuera del orden FEFO. El bloqueo de lote vencido
  // nunca se salta con esto.
  confirmarFueraDeFefo?: boolean;
  // HU15 CA2: por qué se despacha fuera de FEFO; queda en el movimiento.
  motivoFueraDeFefo?: string;
}

export interface DespacharSolicitudResponse {
  solicitudId: number;
  estatus: EstatusSolicitud;
  cantidadSolicitada: number;
  cantidadAtendida: number;
}

// ENTRADA (recepción), CARGA_INICIAL (HU21), SALIDA (despacho a Farmacia) y los
// movimientos especiales: CANJE (HU18), BAJA_CADUCIDAD (HU19), PRESTAMO y TRANSFERENCIA (HU20).
export type TipoMovimiento =
  | 'ENTRADA' | 'CARGA_INICIAL' | 'SALIDA' | 'CANJE' | 'BAJA_CADUCIDAD' | 'PRESTAMO' | 'TRANSFERENCIA' | 'AJUSTE' | 'REUBICACION';

// HU36: por qué no coincide el conteo físico.
export type MotivoAjuste = 'ERROR_CONTEO' | 'DANADA' | 'FALTANTE' | 'SOBRANTE';
export type SentidoMovimiento = 'ENTRADA' | 'SALIDA';

export interface Movimiento {
  id: number;
  tipo: TipoMovimiento;
  fecha: string;
  // Nombre completo de quien lo registró.
  usuario: string;
  solicitudId: number | null;
  clave: string;
  nombreGenerico: string;
  numeroLote: string;
  caducidad: string;
  ubicacion: string;
  cajas: number;
  // Solo canjes, caducados, préstamos y transferencias.
  sentido: SentidoMovimiento | null;
  institucion: string | null;
  // Solo salidas a Farmacia: quién de Farmacia hizo la solicitud, cuándo y cuánto pidió.
  solicitadoPor: string | null;
  fechaSolicitud: string | null;
  cantidadSolicitada: number | null;
  // Préstamos: true si es la devolución (va en sentido contrario al préstamo).
  devolucion: boolean;
  // Por qué se hizo, cuando la regla lo pidió (por ejemplo, despacho fuera de FEFO).
  motivo: string | null;
  // HU39: pedido de Farmacia de la salida.
  pedidoId: number | null;
}

// ---------- Movimientos especiales (HU18, HU19, HU20) ----------

export interface CanjeRequest {
  loteOrigenId: number;
  numeroLote: string;
  caducidad: string;
  ubicaciones: UbicacionCantidad[];
}

export interface Canje {
  fecha: string | null;
  usuario: string | null;
  clave: string;
  nombreGenerico: string;
  proveedor: string;
  loteOrigenId: number;
  loteOrigen: string;
  caducidadOrigen: string;
  cajasOrigen: number;
  loteNuevoId: number;
  loteNuevo: string;
  caducidadNueva: string;
  cajasNuevas: number;
  ubicacion: string;
}

export interface Caducado {
  loteId: number;
  clave: string;
  nombreGenerico: string;
  numeroLote: string;
  caducidad: string;
  proveedor: string;
  cajas: number;
  ubicaciones: UbicacionCantidad[];
  // Solo en el apartado: cuándo se movió y quién lo confirmó.
  fechaApartado: string | null;
  usuario: string | null;
}

export interface MovimientoExternoRequest {
  tipo: 'PRESTAMO' | 'TRANSFERENCIA';
  sentido: SentidoMovimiento;
  institucion: string;
  cajas: number;
  // Salida: existencia de donde sale.
  existenciaId?: number;
  // Entrada: lote que se recibe.
  clave?: string;
  numeroLote?: string;
  caducidad?: string;
  ubicacion?: string;
}

// ---------- Préstamos y transferencias con expediente (ajuste HU20) ----------

// Guardados: ACTIVO, DEVUELTO_PARCIAL, CERRADO (préstamo); REGISTRADA, CONFIRMADA (transferencia).
// Calculados al consultar: VENCIDO y POR_CERRAR.
export type EstatusExpediente = 'ACTIVO' | 'DEVUELTO_PARCIAL' | 'POR_CERRAR' | 'VENCIDO' | 'CERRADO' | 'REGISTRADA' | 'CONFIRMADA';
export type TipoDocumentoExterno = 'SOLICITUD' | 'CIERRE' | 'ENVIO' | 'RECEPCION';

export interface DocumentoExterno {
  id: number;
  expedienteId: number;
  tipo: TipoDocumentoExterno;
  nombreArchivo: string;
  tamano: number;
  fecha: string;
  usuario: string;
}

export interface ExpedienteExterno {
  id: number;
  tipo: 'PRESTAMO' | 'TRANSFERENCIA';
  sentido: SentidoMovimiento;
  institucion: string;
  clave: string;
  nombreGenerico: string;
  cajas: number;
  cajasDevueltas: number;
  fechaRegistro: string;
  fechaLimite: string | null;
  fechaCierre: string | null;
  estatus: EstatusExpediente;
  estatusVigente: EstatusExpediente;
  observaciones: string | null;
  registradoPor: string;
  documentos: DocumentoExterno[];
  movimientos: Movimiento[];
}

export interface RegistrarExpedienteRequest extends MovimientoExternoRequest {
  // Obligatoria en préstamos.
  fechaLimite?: string;
  observaciones?: string;
}

// La devolución va en sentido contrario al préstamo: si se prestó (SALIDA) entra
// con lote, caducidad y ubicación; si nos prestaron (ENTRADA) sale de una existencia.
export interface DevolucionPrestamoRequest {
  cajas: number;
  existenciaId?: number;
  numeroLote?: string;
  caducidad?: string;
  ubicacion?: string;
}

// ---------- Rotación y alertas (HU16, HU17): el backend es la unica fuente de verdad ----------

export interface ConsumoMes {
  mes: string;
  cajas: number;
}

// SISTEMA = salidas de HAERO; INTELISIS = consumo de Farmacia en Intelisis mientras HAERO no tenga 6 meses (HU38).
export type OrigenCpm = 'SISTEMA' | 'INTELISIS';
export type EstadoStock = 'DESABASTO' | 'SOBREABASTO' | 'NORMAL' | 'SIN_CONSUMO';

export interface Rotacion {
  clave: string;
  nombreGenerico: string;
  cpm: number;
  origenCpm: OrigenCpm;
  mesesHistorial: number;
  existenciaActual: number;
  stockMinimo: number;
  stockMaximo: number;
  estado: EstadoStock;
  meses: ConsumoMes[];
}

export type TipoAlerta = 'DESABASTO' | 'SOBREABASTO' | 'CADUCIDAD_PROXIMA';

export interface Alerta {
  id: number;
  clave: string;
  nombreGenerico: string;
  tipo: TipoAlerta;
  fechaInicio: string;
  fechaFin: string | null;
  existencia: number;
  limite: number;
  // Solo para CADUCIDAD_PROXIMA: el lote especifico que esta por vencer.
  numeroLote: string | null;
  caducidad: string | null;
}

// ---------- Integración Intelisis (solo lectura, com.example.backend.intelisis.dto) ----------

export interface EstadoIntelisis {
  disponible: boolean;
  motivo: string | null;
  enCurso: boolean;
  ultimoEstatus: 'OK' | 'ERROR' | null;
  ultimoOrigen: 'AUTOMATICA' | 'MANUAL' | null;
  ultimoInicio: string | null;
  ultimoFin: string | null;
  ultimaDuracionMs: number | null;
  ultimoError: string | null;
  // De cuándo son los datos copiados (última sincronización exitosa).
  datosAl: string | null;
  articulos: number | null;
  proveedores: number | null;
  lotesExistencia: number | null;
  recepciones: number | null;
  lotesRecepcion: number | null;
  medicamentos: number | null;
  pedidos: number | null;
  entradasInv: number | null;
}

export type TipoAvisoIntelisis =
  | 'CLAVE_NUEVA'
  | 'PEDIDO_NUEVO'
  | 'PEDIDO_CANCELADO'
  | 'PARTIDA_CANCELADA'
  | 'LOTE_CAMBIO'
  | 'LOTE_POR_DAR_DE_ALTA'
  | 'CARGA_INICIAL';

// Aviso de un cambio en Intelisis. INFO = para enterarse; REVISAR = afecta lo que
// Almacén ya movió en HAERO (HAERO no lo tocó).
export interface AvisoIntelisis {
  id: number;
  fecha: string;
  tipo: TipoAvisoIntelisis;
  nivel: 'INFO' | 'REVISAR';
  titulo: string;
  detalle: string | null;
  clave: string | null;
  loteId: number | null;
  pedidoId: number | null;
  revisadoEn: string | null;
  revisadoPor: string | null;
}

export type EstadoComparacion = 'EN_AMBOS' | 'SOLO_HAERO' | 'SOLO_INTELISIS';

export interface ComparacionClave {
  clave: string;
  nombre: string | null;
  estado: EstadoComparacion;
  haeroCajas: number | null;
  haeroLotes: number | null;
  haeroProximaCaducidad: string | null;
  piezasPorCaja: number | null;
  intelisisArticulo: string | null;
  // Piezas en Intelisis y su equivalente en cajas (null si no se sabe cuántas piezas trae la caja).
  intelisisPiezas: number | null;
  intelisisCajas: number | null;
  intelisisLotes: number | null;
  intelisisProximaCaducidad: string | null;
}

export const ESTATUS_SOLICITUD_TEXTO: Record<EstatusSolicitud, string> = {
  PENDIENTE: 'Pendiente',
  PARCIAL: 'Parcial',
  ATENDIDA: 'Atendida',
  CANCELADA: 'Cancelada',
  RECHAZADA: 'No se puede surtir',
};

// Folio visible de un pedido de Farmacia (HU39).
export function folioPedido(id: number): string {
  return `PED-${String(id).padStart(4, '0')}`;
}

// Folio visible de una solicitud a partir de su id del backend.
export function folioSolicitud(id: number): string {
  return `SOL-${String(id).padStart(4, '0')}`;
}

/**
 * Texto para mostrar cuando falla una llamada. El backend responde siempre
 * { mensaje } (GlobalExceptionHandler); si no hay respuesta es que el backend no está corriendo.
 */
export function mensajeDeError(error: unknown, porDefecto = 'No se pudo completar la operación.'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'No hay conexión con el servidor. Verifica que el backend esté encendido.';
    if (error.status === 401) return 'Tu sesión expiró. Vuelve a iniciar sesión.';
    const mensaje = error.error?.mensaje;
    if (typeof mensaje === 'string' && mensaje.trim()) return mensaje;
    if (error.status === 403) return 'No tienes permisos para realizar esta acción.';
  }
  return porDefecto;
}

// Llamadas a /api/almacen y /api/farmacia. Supervisión solo puede usar las de consulta (GET).
@Injectable({ providedIn: 'root' })
export class AlmacenApi {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);

  private cabeceras() {
    return { headers: { Authorization: `Bearer ${this.session.obtenerToken()}` } };
  }

  // ---------- Catálogo de medicamentos ----------
  listarMedicamentos(): Observable<Medicamento[]> {
    return this.http.get<Medicamento[]>(`${API_BASE_URL}/almacen/medicamentos`, this.cabeceras());
  }

  obtenerMedicamento(clave: string): Observable<Medicamento> {
    return this.http.get<Medicamento>(`${API_BASE_URL}/almacen/medicamentos/${encodeURIComponent(clave)}`, this.cabeceras());
  }

  // ---------- Existencias (HU12) ----------
  listarExistencias(filtro: FiltroExistencias = {}): Observable<Existencia[]> {
    const params: Record<string, string> = {};
    for (const [campo, valor] of Object.entries(filtro)) {
      if (valor?.trim()) params[campo] = valor.trim();
    }
    return this.http.get<Existencia[]>(`${API_BASE_URL}/almacen/existencias`, { ...this.cabeceras(), params });
  }

  // ---------- Pedidos ----------
  listarPedidos(estatus?: EstatusPedido): Observable<Pedido[]> {
    const params: Record<string, string> = estatus ? { estatus } : {};
    return this.http.get<Pedido[]>(`${API_BASE_URL}/almacen/pedidos`, { ...this.cabeceras(), params });
  }

  obtenerPedido(id: number): Observable<Pedido> {
    return this.http.get<Pedido>(`${API_BASE_URL}/almacen/pedidos/${id}`, this.cabeceras());
  }

  // ---------- Recepción ----------
  resolverCodigo(codigo: string): Observable<ResolverCodigoResponse> {
    return this.http.get<ResolverCodigoResponse>(`${API_BASE_URL}/almacen/codigos-barras/${encodeURIComponent(codigo)}`, this.cabeceras());
  }


  registrarEntrada(request: RegistrarEntradaRequest): Observable<RegistrarEntradaResponse> {
    return this.http.post<RegistrarEntradaResponse>(`${API_BASE_URL}/almacen/entradas`, request, this.cabeceras());
  }

  registrosIntelisis(clave: string, tipo: TipoCapturaIntelisis): Observable<RegistroIntelisis[]> {
    return this.http.get<RegistroIntelisis[]>(`${API_BASE_URL}/almacen/registros-intelisis`, { ...this.cabeceras(), params: { clave, tipo } });
  }

  // ---------- Movimientos (HU14 CA5, HU16) ----------
  listarMovimientos(filtro: { tipo?: TipoMovimiento; desde?: string; hasta?: string } = {}): Observable<Movimiento[]> {
    const params: Record<string, string> = {};
    for (const [campo, valor] of Object.entries(filtro)) {
      if (valor) params[campo] = valor;
    }
    return this.http.get<Movimiento[]>(`${API_BASE_URL}/almacen/movimientos`, { ...this.cabeceras(), params });
  }

  // ---------- Movimientos especiales (HU18, HU19, HU20) ----------
  listarCanjes(): Observable<Canje[]> {
    return this.http.get<Canje[]>(`${API_BASE_URL}/almacen/canjes`, this.cabeceras());
  }

  /** HU36: deja en una ubicación las cajas que se contaron, con su motivo. */
  ajustar(request: { existenciaId: number; cajasContadas: number; motivo: MotivoAjuste; detalle?: string }): Observable<Movimiento> {
    return this.http.post<Movimiento>(`${API_BASE_URL}/almacen/ajustes`, request, this.cabeceras());
  }

  /** HU37: mueve cajas de un lote a otra ubicación. */
  reubicar(request: { existenciaId: number; cajas: number; ubicacionDestino: string }): Observable<Movimiento> {
    return this.http.post<Movimiento>(`${API_BASE_URL}/almacen/reubicaciones`, request, this.cabeceras());
  }

  registrarCanje(request: CanjeRequest): Observable<Canje> {
    return this.http.post<Canje>(`${API_BASE_URL}/almacen/canjes`, request, this.cabeceras());
  }

  /** pendientes=true: vencidos que siguen en el inventario; false: el apartado de caducados. */
  listarCaducados(pendientes: boolean): Observable<Caducado[]> {
    return this.http.get<Caducado[]>(`${API_BASE_URL}/almacen/caducados`, { ...this.cabeceras(), params: { pendientes } });
  }

  moverACaducados(loteId: number): Observable<Caducado> {
    return this.http.post<Caducado>(`${API_BASE_URL}/almacen/caducados`, { loteId }, this.cabeceras());
  }

  listarMovimientosExternos(): Observable<Movimiento[]> {
    return this.http.get<Movimiento[]>(`${API_BASE_URL}/almacen/movimientos-externos`, this.cabeceras());
  }

  // ---------- Préstamos y transferencias con expediente ----------
  listarExpedientesExternos(): Observable<ExpedienteExterno[]> {
    return this.http.get<ExpedienteExterno[]>(`${API_BASE_URL}/almacen/expedientes-externos`, this.cabeceras());
  }

  /** Alta con su PDF: parte "datos" (JSON) y parte "documento" (archivo). */
  registrarExpedienteExterno(datos: RegistrarExpedienteRequest, documento: File): Observable<ExpedienteExterno> {
    const cuerpo = new FormData();
    cuerpo.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
    cuerpo.append('documento', documento, documento.name);
    return this.http.post<ExpedienteExterno>(`${API_BASE_URL}/almacen/expedientes-externos`, cuerpo, this.cabeceras());
  }

  registrarDevolucionPrestamo(expedienteId: number, request: DevolucionPrestamoRequest): Observable<ExpedienteExterno> {
    return this.http.post<ExpedienteExterno>(`${API_BASE_URL}/almacen/expedientes-externos/${expedienteId}/devoluciones`, request, this.cabeceras());
  }

  /** Préstamo: lo cierra (ya devuelto todo). Transferencia: la confirma. Siempre con su PDF. */
  cerrarExpedienteExterno(expedienteId: number, documento: File): Observable<ExpedienteExterno> {
    const cuerpo = new FormData();
    cuerpo.append('documento', documento, documento.name);
    return this.http.post<ExpedienteExterno>(`${API_BASE_URL}/almacen/expedientes-externos/${expedienteId}/cierre`, cuerpo, this.cabeceras());
  }

  descargarDocumentoExterno(expedienteId: number, documentoId: number): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/almacen/expedientes-externos/${expedienteId}/documentos/${documentoId}`, {
      ...this.cabeceras(),
      responseType: 'blob',
    });
  }

  // ---------- Solicitudes: lado de Almacén (HU14, HU15) ----------
  /** HU40: Farmacia cancela una solicitud que ya no necesita. */
  cancelarSolicitud(id: number, motivo?: string): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${API_BASE_URL}/farmacia/solicitudes/${id}/cancelar`, { motivo }, this.cabeceras());
  }

  /** HU40: Almacén indica que no puede surtirla y por qué. */
  rechazarSolicitud(id: number, motivo: string): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${API_BASE_URL}/almacen/solicitudes/${id}/rechazar`, { motivo }, this.cabeceras());
  }

  bandejaSolicitudes(estatus: EstatusSolicitud[] = ['PENDIENTE', 'PARCIAL']): Observable<Solicitud[]> {
    return this.http.get<Solicitud[]>(`${API_BASE_URL}/almacen/solicitudes`, {
      ...this.cabeceras(),
      params: { estatus: estatus.join(',') },
    });
  }

  sugerenciaDespacho(solicitudId: number): Observable<SugerenciaDespacho> {
    return this.http.get<SugerenciaDespacho>(`${API_BASE_URL}/almacen/solicitudes/${solicitudId}/sugerencia`, this.cabeceras());
  }

  despachar(solicitudId: number, request: DespacharSolicitudRequest): Observable<DespacharSolicitudResponse> {
    return this.http.post<DespacharSolicitudResponse>(`${API_BASE_URL}/almacen/solicitudes/${solicitudId}/despachar`, request, this.cabeceras());
  }

  // ---------- Rotación y alertas (HU16, HU17) ----------
  listarRotacion(): Observable<Rotacion[]> {
    return this.http.get<Rotacion[]>(`${API_BASE_URL}/almacen/rotacion`, this.cabeceras());
  }

  listarAlertas(activas: boolean): Observable<Alerta[]> {
    return this.http.get<Alerta[]>(`${API_BASE_URL}/almacen/alertas`, { ...this.cabeceras(), params: { activas } });
  }

  // ---------- Integración Intelisis (lee el espejo en HAERO, nunca Intelisis directo) ----------
  estadoIntelisis(): Observable<EstadoIntelisis> {
    return this.http.get<EstadoIntelisis>(`${API_BASE_URL}/almacen/intelisis/estado`, this.cabeceras());
  }

  comparacionIntelisis(): Observable<ComparacionClave[]> {
    return this.http.get<ComparacionClave[]>(`${API_BASE_URL}/almacen/intelisis/comparacion`, this.cabeceras());
  }

  /** Avisos pendientes (sin revisar) o ya revisados. */
  avisosIntelisis(pendientes: boolean): Observable<AvisoIntelisis[]> {
    return this.http.get<AvisoIntelisis[]>(`${API_BASE_URL}/almacen/intelisis/avisos`, { ...this.cabeceras(), params: { pendientes } });
  }

  resumenAvisosIntelisis(): Observable<{ REVISAR: number; INFO: number }> {
    return this.http.get<{ REVISAR: number; INFO: number }>(`${API_BASE_URL}/almacen/intelisis/avisos/resumen`, this.cabeceras());
  }

  marcarAvisoRevisado(id: number): Observable<{ mensaje: string }> {
    return this.http.post<{ mensaje: string }>(`${API_BASE_URL}/almacen/intelisis/avisos/${id}/revisado`, {}, this.cabeceras());
  }

  marcarInformativosRevisados(): Observable<{ mensaje: string }> {
    return this.http.post<{ mensaje: string }>(`${API_BASE_URL}/almacen/intelisis/avisos/informativos/revisados`, {}, this.cabeceras());
  }

  /** Arranca la sincronización en segundo plano; el avance se ve en estadoIntelisis(). */
  sincronizarIntelisis(): Observable<{ mensaje: string }> {
    return this.http.post<{ mensaje: string }>(`${API_BASE_URL}/almacen/intelisis/sincronizar`, {}, this.cabeceras());
  }

  // ---------- Solicitudes: lado de Farmacia (HU13) ----------
  /** HU39: varias claves en un solo pedido; se guardan todas o ninguna. */
  crearPedido(partidas: { clave: string; cantidad: number }[], observaciones?: string): Observable<Solicitud[]> {
    return this.http.post<Solicitud[]>(`${API_BASE_URL}/farmacia/solicitudes/pedidos`, { partidas, observaciones }, this.cabeceras());
  }

  cancelarPedido(pedidoId: number): Observable<Solicitud[]> {
    return this.http.post<Solicitud[]>(`${API_BASE_URL}/farmacia/solicitudes/pedidos/${pedidoId}/cancelar`, {}, this.cabeceras());
  }

  /** HU39: todas las claves abiertas del pedido con su sugerencia FEFO, en una sola operación. */
  despacharPedido(pedidoId: number, parcial: boolean): Observable<DespachoPedido> {
    return this.http.post<DespachoPedido>(`${API_BASE_URL}/almacen/solicitudes/pedidos/${pedidoId}/despachar`, {}, {
      ...this.cabeceras(),
      params: { parcial: String(parcial) },
    });
  }

  /** Solicitudes de todo el personal de Farmacia (no solo las del usuario). */
  solicitudesFarmacia(): Observable<Solicitud[]> {
    return this.http.get<Solicitud[]>(`${API_BASE_URL}/farmacia/solicitudes`, this.cabeceras());
  }
}

// Las cajas traen solo mes y año (AAAA-MM); el backend guarda una fecha completa,
// así que se usa el último día de ese mes. Si ya trae día, se deja igual.
export function ultimoDiaDelMes(fecha: string): string {
  if (fecha.length > 7) return fecha;
  const [anio, mes] = fecha.split('-').map(Number);
  return `${fecha}-${String(new Date(anio, mes, 0).getDate()).padStart(2, '0')}`;
}
