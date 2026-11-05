import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from '../../api-config';
import { Session } from '../../auth/session';

// Cajas de anestesia de quirófano (vales de medicamentos para quirófano). Tipos copiados de
// com.example.backend.farmacia.dto.CajasQuirofanoDtos: los nombres deben coincidir con el backend.

export type EstadoCaja = 'INCOMPLETA' | 'COMPLETA' | 'ENTREGADA';
export type Turno = 'MATUTINO' | 'VESPERTINO' | 'NOCTURNO' | 'FIN_DE_SEMANA';

export const TURNOS: { valor: Turno; etiqueta: string }[] = [
  { valor: 'MATUTINO', etiqueta: 'Matutino' },
  { valor: 'VESPERTINO', etiqueta: 'Vespertino' },
  { valor: 'NOCTURNO', etiqueta: 'Nocturno' },
  { valor: 'FIN_DE_SEMANA', etiqueta: 'Fines de semana y días festivos' },
];

export interface ContenidoCaja {
  clave: string;
  // Nombre del catálogo (Intelisis).
  medicamento: string;
  cantidad: number;
  // Red fría: va en el vale pero no se guarda en la caja; sale del refrigerador al entregarla.
  aparte?: boolean;
  // Nombre como lo escribe Farmacia en la caja y el vale; vacío = el del catálogo.
  descripcion?: string | null;
}

export interface MedicoIntelisis {
  usuario: string;
  nombre: string;
  especialidad: string | null;
  cedula: string | null;
}

// Los 3 vales de medicamentos para quirófano que usa Farmacia (formato en Excel). Cada caja se da de
// alta desde su vale; las cajas del mismo vale traen los mismos medicamentos y solo cambian las
// cantidades. Claves buscadas en el catálogo por la descripción del vale (revisarlas con Farmacia).
export interface PlantillaVale {
  id: 'SALAS' | 'IMAGEN' | 'HEMODINAMIA';
  nombre: string;
  servicio: string;
  contenido: ContenidoCaja[];
}

export const VALES_QUIROFANO: PlantillaVale[] = [
  {
    id: 'SALAS',
    nombre: 'Salas de quirófano',
    servicio: 'Anestesiología / Dentro de quirófano',
    contenido: [
      { clave: '040.000.0242.00', medicamento: 'Fentanilo sol. inyectable 0.5 mg/10 ml', cantidad: 4 },
      { clave: '040.000.2103.00', medicamento: 'Morfina sol. inyectable 10 mg/10 ml', cantidad: 1 },
      { clave: '040.000.4026.00', medicamento: 'Buprenorfina sol. inyectable 0.3 mg/1 ml', cantidad: 1 },
      { clave: '040.000.0302.00', medicamento: 'Naloxona sol. inyectable 0.4 mg/1 ml', cantidad: 1 },
      { clave: '010.000.0612.00', medicamento: 'Norepinefrina sol. inyectable 4 mg', cantidad: 2 },
      { clave: '040.000.2107.00', medicamento: 'Efedrina solución inyectable 50 mg/2 ml', cantidad: 1 },
      { clave: '040.000.2108.00', medicamento: 'Midazolam sol. inyectable 5 mg', cantidad: 1 },
      { clave: '010.000.0271.00', medicamento: 'Bupivacaína sol. inyectable 5 mg/ml', cantidad: 1 },
      { clave: '010.000.0254.00', medicamento: 'Vecuronio frasco ámpula 4 mg', cantidad: 2 },
      { clave: '010.000.0291.00', medicamento: 'Neostigmina solución inyectable 0.5 mg/ml', cantidad: 2 },
      { clave: '010.000.4241.00', medicamento: 'Dexametasona solución inyectable 8 mg/2 ml', cantidad: 2 },
      { clave: '040.000.2106.00', medicamento: 'Tramadol sol. 100 mg/2 ml', cantidad: 2 },
      { clave: '010.000.5721.00', medicamento: 'Paracetamol frasco ámpula 1 g/100 ml', cantidad: 2 },
      { clave: '010.000.4028.00', medicamento: 'Clonixinato de lisina sol. inyectable 100 mg/2 ml', cantidad: 2 },
      { clave: '010.000.0476.00', medicamento: 'Metilprednisolona frasco ámpula 500 mg', cantidad: 2 },
      { clave: '010.000.0247.00', medicamento: 'Dexmedetomidina frasco ámpula 200 mcg/2 ml', cantidad: 1 },
      { clave: '010.000.7033.00', medicamento: 'Ácido tranexámico frasco ámpula 500 mg', cantidad: 2 },
      { clave: '010.000.2308.00', medicamento: 'Furosemida solución inyectable 20 mg/2 ml', cantidad: 2 },
      { clave: '010.000.3620.00', medicamento: 'Gluconato de calcio sol. iny. 1 g', cantidad: 1 },
      { clave: '010.000.0524.00', medicamento: 'Cloruro de potasio 20 mEq', cantidad: 1 },
      { clave: '010.000.3629.00', medicamento: 'Sulfato de magnesio 1 g/10 ml', cantidad: 1 },
    ],
  },
  {
    id: 'IMAGEN',
    nombre: 'Imagen y endoscopias',
    servicio: 'Anestesiología / Endoscopias e imagen',
    contenido: [
      { clave: '040.000.0242.00', medicamento: 'Fentanilo solución inyectable 0.5 mg/10 ml', cantidad: 2 },
      { clave: '040.000.2108.00', medicamento: 'Midazolam solución inyectable 5 mg/5 ml', cantidad: 1 },
      // En el vale la succinilcolina viene sombreada (red fría: se entrega aparte); el catálogo tiene
      // suxametonio 40 mg/2 ml y el vale dice 100 mg/2 ml.
      { clave: '010.000.0252.00', medicamento: 'Succinilcolina solución inyectable', cantidad: 2, aparte: true },
      { clave: '010.000.4241.00', medicamento: 'Dexametasona solución inyectable 8 mg/2 ml', cantidad: 2 },
      { clave: '010.000.1207.00', medicamento: 'Butilhioscina solución inyectable 20 mg/1 ml', cantidad: 3 },
      { clave: '010.000.5721.00', medicamento: 'Paracetamol solución inyectable 1 g', cantidad: 1 },
      { clave: '010.000.0247.00', medicamento: 'Dexmedetomidina sol. iny. 200 mcg/2 ml', cantidad: 1 },
    ],
  },
  {
    id: 'HEMODINAMIA',
    nombre: 'Hemodinamia',
    servicio: 'Anestesiología / Hemodinamia',
    contenido: [
      { clave: '040.000.0242.00', medicamento: 'Fentanilo sol. inyectable 0.5 mg/10 ml', cantidad: 2 },
      { clave: '040.000.2108.00', medicamento: 'Midazolam sol. inyectable 5 mg/5 ml', cantidad: 1 },
      { clave: '010.000.2308.00', medicamento: 'Furosemida solución inyectable 20 mg/2 ml', cantidad: 1 },
    ],
  },
];

export interface LoteEnCaja {
  lote: string;
  caducidad: string;
  piezas: number;
}

export interface RenglonCaja {
  clave: string;
  // El nombre que se muestra: el que escribió Farmacia o, si no, el del catálogo.
  medicamento: string;
  cantidad: number;
  tiene: number;
  falta: number;
  lotes: LoteEnCaja[];
  aparte: boolean;
  descripcion: string | null;
  nombreCatalogo: string;
}

export interface UsoCaja {
  expediente: string;
  nombre: string;
  clave: string;
  piezas: number;
  folioEntrega?: string | null;
}

// Renglón del vale: Surtido al entregar, Utilizado con pacientes; Devolución = surtido - utilizado.
export interface PartidaVale {
  clave: string;
  medicamento: string;
  surtido: number;
  utilizado: number;
}

export interface ValeCaja {
  id: number;
  turno: Turno;
  sala: string;
  medico: string;
  entregadoEn: string;
  entregadoPor: string;
  recibidoEn: string | null;
  recibidoPor: string | null;
  justificacion: string | null;
  // Expedientes con su indicación médica en PDF (una por paciente).
  indicaciones: string[];
  usos: UsoCaja[];
  partidas: PartidaVale[];
}

// Las cajas pasan tiempo armadas: un lote que caduca en 3 meses o menos conviene cambiarlo.
export const MESES_CAMBIAR_LOTE_CAJA = 3;

/** 'VENCIDO', 'PRONTO' (caduca en 3 meses o menos) o null. */
export function alertaLote(caducidad: string): 'VENCIDO' | 'PRONTO' | null {
  const hoy = new Date().toISOString().slice(0, 10);
  if (caducidad < hoy) return 'VENCIDO';
  const limite = new Date();
  limite.setMonth(limite.getMonth() + MESES_CAMBIAR_LOTE_CAJA);
  return caducidad <= limite.toISOString().slice(0, 10) ? 'PRONTO' : null;
}

/** Lotes de la caja vencidos o por caducar. */
export function lotesEnRiesgo(caja: CajaQuirofano): { clave: string; lote: string; caducidad: string; alerta: 'VENCIDO' | 'PRONTO' }[] {
  return caja.renglones.flatMap((r) =>
    r.lotes.flatMap((l) => {
      const alerta = alertaLote(l.caducidad);
      return alerta ? [{ clave: r.clave, lote: l.lote, caducidad: l.caducidad, alerta }] : [];
    }),
  );
}

// Piezas dentro de las cajas por lote (siguen siendo de Farmacia).
export interface PiezasEnCaja {
  loteId: number;
  clave: string;
  medicamento: string;
  lote: string;
  caducidad: string;
  piezas: number;
  cajas: string[];
}

// Baja por volumen del área (QUIMIO o QUIROFANO), igual que la de Hospitalización.
export interface EstadoBajaArea {
  area: 'QUIMIO' | 'QUIROFANO';
  dia: string;
  pacientesPendientes: number;
  entregasPendientes: number;
  folioDelDia: number | null;
  ultimaBaja: string | null;
  ultimaBajaPor: string | null;
}

export interface EventoCaja {
  accion: string;
  detalle: string | null;
  fecha: string;
  usuario: string;
}

// Caja dada de baja: ya no se arma ni se entrega, pero su historial se conserva.
export interface CajaDadaDeBaja {
  codigo: string;
  nombre: string;
  servicio: string;
  fecha: string | null;
  usuario: string | null;
  motivo: string | null;
}

export interface CajaQuirofano {
  codigo: string;
  nombre: string;
  servicio: string;
  estado: EstadoCaja;
  piezasFaltantes: number;
  renglones: RenglonCaja[];
  valeAbierto: ValeCaja | null;
  vales: ValeCaja[];
  historial: EventoCaja[];
}

@Injectable({ providedIn: 'root' })
export class CajasQuirofano {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);
  private readonly base = `${API_BASE_URL}/farmacia/quirofano`;

  private readonly _cajas = signal<CajaQuirofano[]>([]);
  readonly cajas = this._cajas.asReadonly();
  readonly totales = computed(() => {
    const lista = this._cajas();
    const contar = (estado: EstadoCaja) => lista.filter((caja) => caja.estado === estado).length;
    return { cajas: lista.length, completas: contar('COMPLETA'), incompletas: contar('INCOMPLETA'), entregadas: contar('ENTREGADA') };
  });

  private cabeceras() {
    return { headers: { Authorization: `Bearer ${this.session.obtenerToken()}` } };
  }

  cargar(): Observable<CajaQuirofano[]> {
    return this.http.get<CajaQuirofano[]>(`${this.base}/cajas`, this.cabeceras()).pipe(tap((lista) => this._cajas.set(lista)));
  }

  detalle(codigo: string): Observable<CajaQuirofano> {
    return this.http.get<CajaQuirofano>(`${this.base}/cajas/${encodeURIComponent(codigo.trim().toUpperCase())}`, this.cabeceras())
      .pipe(tap((caja) => this.poner(caja)));
  }

  crear(codigo: string, nombre: string, servicio: string, contenido: ContenidoCaja[]): Observable<CajaQuirofano> {
    return this.http.post<CajaQuirofano>(`${this.base}/cajas`, { codigo: codigo || null, nombre, servicio, contenido }, this.cabeceras())
      .pipe(tap((caja) => this.poner(caja)));
  }

  cambiar(codigo: string, nombre: string, servicio: string, contenido: ContenidoCaja[], justificacion: string): Observable<CajaQuirofano> {
    return this.http.put<CajaQuirofano>(`${this.base}/cajas/${encodeURIComponent(codigo)}`, { nombre, servicio, contenido, justificacion }, this.cabeceras())
      .pipe(tap((caja) => this.poner(caja)));
  }

  /** No se borra: lo que trae regresa al estante y su historial se conserva. */
  darDeBaja(codigo: string, motivo: string): Observable<void> {
    return this.http.post<void>(`${this.base}/cajas/${encodeURIComponent(codigo)}/baja`, { motivo }, this.cabeceras())
      .pipe(tap(() => this._cajas.update((lista) => lista.filter((c) => c.codigo !== codigo))));
  }

  reactivar(codigo: string, motivo: string): Observable<CajaQuirofano> {
    return this.http.post<CajaQuirofano>(`${this.base}/cajas/${encodeURIComponent(codigo)}/reactivar`, { motivo }, this.cabeceras())
      .pipe(tap((caja) => this.poner(caja)));
  }

  dadasDeBaja(): Observable<CajaDadaDeBaja[]> {
    return this.http.get<CajaDadaDeBaja[]>(`${this.base}/cajas-de-baja`, this.cabeceras());
  }

  /** Sin lote, Farmacia toma el que sale primero (FEFO). codigo = código de barras escaneado. */
  armar(codigo: string, clave: string, lote: string | null, piezas: number, codigoBarras: string | null): Observable<CajaQuirofano> {
    return this.http.post<CajaQuirofano>(`${this.base}/cajas/${encodeURIComponent(codigo)}/armar`, { clave, lote, piezas, codigo: codigoBarras }, this.cabeceras())
      .pipe(tap((caja) => this.poner(caja)));
  }

  sacar(codigo: string, clave: string, lote: string, piezas: number, motivo: string): Observable<CajaQuirofano> {
    return this.http.post<CajaQuirofano>(`${this.base}/cajas/${encodeURIComponent(codigo)}/sacar`, { clave, lote, piezas, motivo }, this.cabeceras())
      .pipe(tap((caja) => this.poner(caja)));
  }

  /** medicoUsuario: el usuario de Intelisis si se eligió de la lista; si no, solo el nombre escrito. */
  entregar(codigo: string, turno: Turno, sala: string, medico: string, medicoUsuario: string | null): Observable<CajaQuirofano> {
    return this.http.post<CajaQuirofano>(`${this.base}/cajas/${encodeURIComponent(codigo)}/entregar`, { turno, sala, medico, medicoUsuario }, this.cabeceras())
      .pipe(tap((caja) => this.poner(caja)));
  }

  /** Una indicación médica (PDF) por paciente: indicaciones[expediente] = archivo. */
  recibir(codigo: string, usos: UsoCaja[], justificacion: string, indicaciones: Record<string, File>): Observable<CajaQuirofano> {
    const cuerpo = new FormData();
    const expedientes = Object.keys(indicaciones);
    cuerpo.append('datos', new Blob([JSON.stringify({ usos, justificacion, indicaciones: expedientes })], { type: 'application/json' }));
    for (const expediente of expedientes) cuerpo.append('documentos', indicaciones[expediente], indicaciones[expediente].name);
    return this.http.post<CajaQuirofano>(`${this.base}/cajas/${encodeURIComponent(codigo)}/recibir`, cuerpo, this.cabeceras())
      .pipe(tap((caja) => this.poner(caja)));
  }

  medicos(nombre: string): Observable<MedicoIntelisis[]> {
    return this.http.get<MedicoIntelisis[]>(`${this.base}/medicos`, { ...this.cabeceras(), params: { nombre } });
  }

  /** Clave de un código de barras ya registrado; error 404 si es nuevo. */
  claveDelCodigo(codigo: string): Observable<{ clave: string; medicamento: string }> {
    return this.http.get<{ clave: string; medicamento: string }>(`${this.base}/codigo`, { ...this.cabeceras(), params: { codigo } });
  }

  indicacion(valeId: number, expediente: string): Observable<Blob> {
    return this.http.get(`${this.base}/vales/${valeId}/indicacion`, { ...this.cabeceras(), params: { expediente }, responseType: 'blob' });
  }

  enCajas(): Observable<PiezasEnCaja[]> {
    return this.http.get<PiezasEnCaja[]>(`${this.base}/en-cajas`, this.cabeceras());
  }

  // ---------- Baja por volumen del día (Quirófano y Quimioterapia) ----------
  estadoBaja(area: 'QUIMIO' | 'QUIROFANO'): Observable<EstadoBajaArea> {
    return this.http.get<EstadoBajaArea>(`${API_BASE_URL}/farmacia/bajas-area/${area}`, this.cabeceras());
  }

  /** Suma lo entregado hoy a la baja del día; regresa las hojas (el folio se usa para imprimir). */
  terminarDia(area: 'QUIMIO' | 'QUIROFANO'): Observable<{ folio: number }[]> {
    return this.http.post<{ folio: number }[]>(`${API_BASE_URL}/farmacia/bajas-area/${area}/terminar`, {}, this.cabeceras());
  }

  private poner(caja: CajaQuirofano): void {
    this._cajas.update((lista) =>
      lista.some((c) => c.codigo === caja.codigo) ? lista.map((c) => (c.codigo === caja.codigo ? caja : c)) : [...lista, caja],
    );
  }
}
