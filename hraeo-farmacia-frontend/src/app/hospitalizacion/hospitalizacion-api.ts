import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import { Session } from '../auth/session';

// Tipos copiados de los DTOs del backend (com.example.backend.hospitalizacion.dto).
// Los datos salen de la lectura de Intelisis que HAERO hace cada 5 minutos.

export interface AreaHospitalizacion {
  central: string;
  nombre: string;
  camas: number;
  ocupadas: number;
  libres: number;
  pacientes: number;
  // Camas con novedades que este usuario no ha visto.
  conNovedades: number;
}

export interface PacienteInternado {
  idIngreso: number;
  nombre: string | null;
  expediente: string | null;
  servicio: string | null;
  fechaIngreso: string | null;
  diasInternado: number | null;
  medicamentosActivos: number;
}

export type EstadoCama = 'LIBRE' | 'OCUPADA';

export interface CamaHospitalizacion {
  espacio: string;
  nombre: string | null;
  tipo: string | null;
  central: string;
  estado: EstadoCama;
  // Normalmente uno; puede haber más (sillones de urgencias o registros sin cerrar en Intelisis).
  pacientes: PacienteInternado[];
  novedades: number;
}

export interface MedicamentoIndicado {
  id: number;
  clave: string | null;
  medicamento: string | null;
  descripcion: string;
  dosis: number | null;
  unidad: string | null;
  via: string | null;
  cada: number | null;
  cadaUnidad: string | null;
  indicadoEn: string | null;
  activo: boolean;
  suspendidoEn: string | null;
}

export type TipoEvento = 'INGRESO' | 'EGRESO' | 'CAMBIO_CAMA' | 'MEDICAMENTO_NUEVO' | 'MEDICAMENTO_SUSPENDIDO' | 'MEDICAMENTO_MODIFICADO';

export interface EventoHospitalizacion {
  id: number;
  fecha: string;
  tipo: TipoEvento;
  espacio: string | null;
  central: string | null;
  paciente: string | null;
  detalle: string;
  visto: boolean;
  // Médico que indicó, cambió o suspendió.
  medico: string | null;
}

export interface DetalleCama {
  cama: CamaHospitalizacion;
  pacientes: { paciente: PacienteInternado; medicamentos: MedicamentoIndicado[] }[];
  actualizaciones: EventoHospitalizacion[];
  sincronizadoEn: string | null;
}

export interface EstadoHospitalizacion {
  intelisisDisponible: boolean;
  enCurso: boolean;
  ultimaLectura: string | null;
  estatus: string | null;
  error: string | null;
  camas: number;
  internados: number;
}

// Carrito del día (lo surte Farmacia en /api/farmacia/carrito).
export type EstadoLinea = 'PENDIENTE' | 'SURTIDO' | 'SUSPENDIDO';

export interface LineaCarrito {
  clave: string | null;
  medicamento: string | null;
  // Con clave del catálogo; sin ella solo se muestra.
  enCatalogo: boolean;
  // Referencia para el despachador (la cantidad la captura él): "1 GRAMO INTRAVENOSA c/8 horas".
  posologia: string | null;
  dosis24h: number | null;
  entregadas: number;
  estado: EstadoLinea;
  indicadoEn: string | null;
  // Lotes entregados hoy: "A123 (3), B456 (1)".
  lotes: string | null;
  // Se indicó hoy después de la hora de corte (1:00 p.m.): se surte si urge; si no, mañana.
  despuesDelCierre: boolean;
  // Se indicó ayer y no se surtió.
  pendienteDeAyer: boolean;
  // Hoy le cambiaron la dosis, la vía o la frecuencia (solo aviso).
  modificado: boolean;
  // Cuándo y qué médico lo canceló (SUSPENDIDO = se canceló después de meterlo al carrito).
  canceladoEn: string | null;
  canceladoPor: string | null;
}

export interface CamaCarrito {
  espacio: string;
  idIngreso: number;
  paciente: string | null;
  expediente: string | null;
  lineas: LineaCarrito[];
}

export interface Carrito {
  central: string;
  dia: string;
  // Lo indicado de 00:00 a esta hora es el carrito del día; después, "después del carrito".
  horaCorte: string;
  lineas: number;
  surtidas: number;
  // Cuándo y quién cerró el carrito de hoy; null si sigue abierto.
  cerradoEn: string | null;
  cerradoPor: string | null;
  camas: CamaCarrito[];
}

export interface CandidatoCarrito {
  idIngreso: number;
  espacio: string;
  paciente: string | null;
  sugeridas: number;
  entregadas: number;
}

export interface EscaneoCarrito {
  clave: string;
  medicamento: string;
  lote: string | null;
  candidatos: CandidatoCarrito[];
}

export interface SurtidoCarrito {
  folioEntrega: string | null;
  partidas: { lote: string; entregada: number }[];
  linea: LineaCarrito;
}

// Hoja "Indicaciones médicas activas" (igual a la de Intelisis).
export interface MedicamentoHoja {
  clave: string | null;
  descripcion: string | null;
  indicacion: string;
  nota: string | null;
  externo: boolean;
  folio: number | null;
}

export interface MedicoHoja {
  nombre: string | null;
  cedula: string | null;
  especialidad: string | null;
}

export interface HojaIndicaciones {
  idIngreso: number;
  paciente: string | null;
  sexo: string | null;
  edad: string | null;
  fechaNacimiento: string | null;
  expediente: string | null;
  cama: string;
  area: string;
  fechaImpresion: string;
  indicacionesGenerales: string[];
  medicamentosPorHorario: MedicamentoHoja[];
  medicamentosPorInfusion: MedicamentoHoja[];
  desayuno: string | null;
  comida: string | null;
  cena: string | null;
  medicoTratante: MedicoHoja | null;
  medicoAdscrito: MedicoHoja | null;
  // false: Intelisis no respondió y la hoja sale sin indicaciones generales, dieta ni médicos.
  intelisisDisponible: boolean;
}

// Hoja de baja propia de HAERO (mismo acomodo que "Consumo Farmacia").
export interface PartidaBaja {
  articulo: string;
  descripcion: string;
  cantidad: number;
  lote: string;
  caducidad: string;
  precio: number | null;
  total: number | null;
  unidad: string | null;
}

export interface HojaBaja {
  folio: number;
  dia: string;
  fecha: string;
  paciente: string | null;
  expediente: string | null;
  cama: string | null;
  area: string;
  observaciones: string;
  partidas: PartidaBaja[];
  totalPiezas: number;
  total: number | null;
  surtio: string;
  preciosDeIntelisis: boolean;
}

// Llamadas a /api/hospitalizacion (Farmacia y Supervisión, con las políticas aceptadas).
@Injectable({ providedIn: 'root' })
export class HospitalizacionApi {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);
  private readonly base = `${API_BASE_URL}/hospitalizacion`;

  private cabeceras() {
    return { headers: { Authorization: `Bearer ${this.session.obtenerToken()}` } };
  }

  areas(): Observable<AreaHospitalizacion[]> {
    return this.http.get<AreaHospitalizacion[]>(`${this.base}/areas`, this.cabeceras());
  }

  camas(central: string): Observable<CamaHospitalizacion[]> {
    return this.http.get<CamaHospitalizacion[]>(`${this.base}/areas/${encodeURIComponent(central)}/camas`, this.cabeceras());
  }

  /** Abrir el detalle queda registrado en la bitácora de consultas. */
  detalle(espacio: string): Observable<DetalleCama> {
    return this.http.get<DetalleCama>(`${this.base}/camas/${encodeURIComponent(espacio)}`, this.cabeceras());
  }

  marcarVisto(espacio: string): Observable<void> {
    return this.http.post<void>(`${this.base}/camas/${encodeURIComponent(espacio)}/visto`, {}, this.cabeceras());
  }

  novedades(central?: string): Observable<EventoHospitalizacion[]> {
    const params: Record<string, string> = central ? { central } : {};
    return this.http.get<EventoHospitalizacion[]>(`${this.base}/novedades`, { ...this.cabeceras(), params });
  }

  carrito(central: string): Observable<Carrito> {
    return this.http.get<Carrito>(`${API_BASE_URL}/farmacia/carrito`, { ...this.cabeceras(), params: { central } });
  }

  /** Saca del carrito lo que se metió (por ejemplo, porque se canceló): regresa al inventario. */
  quitar(idIngreso: number, clave: string, motivo?: string): Observable<Carrito> {
    return this.http.post<Carrito>(`${API_BASE_URL}/farmacia/carrito/quitar`, { idIngreso, clave, motivo }, this.cabeceras());
  }

  /** Termina el carrito: asigna el folio de HAERO a la hoja de baja de cada paciente. */
  terminar(central: string, confirmarCancelados = false): Observable<HojaBaja[]> {
    return this.http.post<HojaBaja[]>(`${API_BASE_URL}/farmacia/carrito/terminar`, {}, {
      ...this.cabeceras(),
      params: { central, confirmarCancelados },
    });
  }

  hojasBaja(central: string, dia?: string, ingreso?: number): Observable<HojaBaja[]> {
    const params: Record<string, string> = { central };
    if (dia) params['dia'] = dia;
    if (ingreso) params['ingreso'] = String(ingreso);
    return this.http.get<HojaBaja[]>(`${API_BASE_URL}/farmacia/carrito/hojas-baja`, { ...this.cabeceras(), params });
  }

  indicaciones(central: string, ingresos?: number[]): Observable<HojaIndicaciones[]> {
    const params: Record<string, string> = { central };
    if (ingresos?.length) params['ingresos'] = ingresos.join(',');
    return this.http.get<HojaIndicaciones[]>(`${API_BASE_URL}/farmacia/carrito/indicaciones`, { ...this.cabeceras(), params });
  }

  /** Reconoce la caja por su código de barras y dice a qué pacientes del área les toca. */
  /** Con `clave`, un código que HAERO no conocía queda ligado a ese medicamento (HU42). */
  escanear(central: string, codigo: string, lote?: string, clave?: string): Observable<EscaneoCarrito> {
    return this.http.post<EscaneoCarrito>(`${API_BASE_URL}/farmacia/carrito/escanear`, { central, codigo, lote, clave }, this.cabeceras());
  }

  /** Entrega al paciente: descuenta el inventario de Farmacia (lote GS1 o FEFO). */
  surtir(idIngreso: number, clave: string, piezas: number, lote?: string): Observable<SurtidoCarrito> {
    return this.http.post<SurtidoCarrito>(`${API_BASE_URL}/farmacia/carrito/surtir`, { idIngreso, clave, piezas, lote }, this.cabeceras());
  }

  estado(): Observable<EstadoHospitalizacion> {
    return this.http.get<EstadoHospitalizacion>(`${this.base}/estado`, this.cabeceras());
  }
}
