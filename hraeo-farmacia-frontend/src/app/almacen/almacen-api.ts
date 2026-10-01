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
}

export interface CrearMedicamentoRequest {
  clave: string;
  nombreGenerico: string;
  presentacion: string;
  piezasPorCaja?: number | null;
  descripcion?: string | null;
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
}

export interface FiltroExistencias {
  clave?: string;
  lote?: string;
  ubicacion?: string;
}

export type EstatusPedido = 'ACTIVO' | 'CANCELADO';

export interface PartidaPedido {
  clave: string;
  nombreGenerico: string;
  cantidadEsperada: number;
  cantidadRecibida: number;
}

export interface Pedido {
  id: number;
  numeroPedido: string;
  proveedor: string;
  estatus: EstatusPedido;
  fecha: string;
  partidas: PartidaPedido[];
  motivoCancelacion: string | null;
}

export interface CrearPedidoRequest {
  numeroPedido: string;
  fecha: string;
  proveedor: string;
  partidas: { clave: string; cantidadEsperada: number }[];
}

export interface UbicacionCantidad {
  ubicacion: string;
  cajas: number;
}

export interface RegistrarEntradaRequest {
  pedidoId: number;
  clave: string;
  proveedor: string;
  numeroLote: string;
  caducidad: string;
  ubicaciones: UbicacionCantidad[];
}

export interface RegistrarEntradaResponse {
  loteId: number;
  numeroLote: string;
  caducidad: string;
  existencias: UbicacionCantidad[];
}

export interface ResolverCodigoResponse {
  clave: string;
  nombre: string;
  proveedor: string;
}

export interface AsociarCodigoRequest {
  codigo: string;
  clave: string;
  proveedor: string;
}

export interface CargaInicialResponse {
  exito: boolean;
  totalRenglones: number;
  lotesCreados: number;
  errores: { fila: number; mensaje: string }[];
}

export type EstatusSolicitud = 'PENDIENTE' | 'PARCIAL' | 'ATENDIDA';

export interface Solicitud {
  id: number;
  clave: string;
  nombreGenerico: string;
  cantidadSolicitada: number;
  cantidadAtendida: number;
  estatus: EstatusSolicitud;
  fecha: string;
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
}

export interface DespacharSolicitudResponse {
  solicitudId: number;
  estatus: EstatusSolicitud;
  cantidadSolicitada: number;
  cantidadAtendida: number;
}

// ENTRADA (recepción), CARGA_INICIAL (HU21), SALIDA (despacho a Farmacia) y los
// movimientos especiales: CANJE (HU18), BAJA_CADUCIDAD (HU19), PRESTAMO y TRANSFERENCIA (HU20).
export type TipoMovimiento = 'ENTRADA' | 'CARGA_INICIAL' | 'SALIDA' | 'CANJE' | 'BAJA_CADUCIDAD' | 'PRESTAMO' | 'TRANSFERENCIA';
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

export const ESTATUS_SOLICITUD_TEXTO: Record<EstatusSolicitud, string> = {
  PENDIENTE: 'Pendiente',
  PARCIAL: 'Parcial',
  ATENDIDA: 'Atendida',
};

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

  crearMedicamento(request: CrearMedicamentoRequest): Observable<Medicamento> {
    return this.http.post<Medicamento>(`${API_BASE_URL}/almacen/medicamentos`, request, this.cabeceras());
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

  crearPedido(request: CrearPedidoRequest): Observable<Pedido> {
    return this.http.post<Pedido>(`${API_BASE_URL}/almacen/pedidos`, request, this.cabeceras());
  }

  cancelarPedido(id: number, motivo: string): Observable<Pedido> {
    return this.http.patch<Pedido>(`${API_BASE_URL}/almacen/pedidos/${id}/cancelar`, { motivo }, this.cabeceras());
  }

  // ---------- Recepción ----------
  resolverCodigo(codigo: string): Observable<ResolverCodigoResponse> {
    return this.http.get<ResolverCodigoResponse>(`${API_BASE_URL}/almacen/codigos-barras/${encodeURIComponent(codigo)}`, this.cabeceras());
  }

  asociarCodigo(request: AsociarCodigoRequest): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/almacen/codigos-barras`, request, this.cabeceras());
  }

  registrarEntrada(request: RegistrarEntradaRequest): Observable<RegistrarEntradaResponse> {
    return this.http.post<RegistrarEntradaResponse>(`${API_BASE_URL}/almacen/entradas`, request, this.cabeceras());
  }

  // ---------- Carga inicial (HU21): el backend solo lee CSV ----------
  cargarInventarioInicial(csv: string, nombreArchivo = 'inventario.csv'): Observable<CargaInicialResponse> {
    const datos = new FormData();
    datos.append('archivo', new Blob([csv], { type: 'text/csv;charset=utf-8' }), nombreArchivo);
    return this.http.post<CargaInicialResponse>(`${API_BASE_URL}/almacen/carga-inicial`, datos, this.cabeceras());
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

  registrarMovimientoExterno(request: MovimientoExternoRequest): Observable<Movimiento> {
    return this.http.post<Movimiento>(`${API_BASE_URL}/almacen/movimientos-externos`, request, this.cabeceras());
  }

  // ---------- Solicitudes: lado de Almacén (HU14, HU15) ----------
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

  // ---------- Solicitudes: lado de Farmacia (HU13) ----------
  crearSolicitud(clave: string, cantidad: number): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${API_BASE_URL}/farmacia/solicitudes`, { clave, cantidad }, this.cabeceras());
  }

  misSolicitudes(): Observable<Solicitud[]> {
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
