import { jsPDF } from 'jspdf';
import { anotar } from '../../shared/reporte-excel';
import {
  DORADO,
  Documento,
  GRIS,
  GUINDA,
  ESTILO_TABLA,
  MARGEN,
  bloqueDatos,
  encabezadoBase,
  pies,
  tabla as tablaPdf,
  subtitulo,
} from '../../almacen/despacho/salidas-registradas/comprobante-salidas';
import { Trazabilidad } from '../farmacia-api';

// Dibuja el encabezado del hospital en la hoja actual y regresa la altura donde empieza el contenido.
type Cabecera = () => number;
// Lo que ocupa el encabezado (encabezadoBase regresa 41).
const ALTO_ENCABEZADO = 41;

const MODO_TEXTO: Record<Trazabilidad['modo'], string> = {
  LOTE: 'Por lote',
  EXPEDIENTE: 'Por expediente',
  CURP: 'Por CURP',
  NOMBRE: 'Por nombre del paciente',
  RECETA: 'Por folio de receta',
};

/**
 * Reporte en PDF de una consulta de trazabilidad, con el mismo formato que el comprobante de
 * salidas de Almacén (encabezado, tablas guinda y pie con quién lo generó). Lleva lo que se ve en
 * pantalla: el recorrido, las compras y la existencia de Intelisis (por lote) y todas las entregas.
 *
 * @param titulo lo que se buscó, como se muestra en pantalla ("Lote 2L23198 · 010.000.1768.02")
 */
export function descargarReporteTrazabilidad(resultado: Trazabilidad, titulo: string, generadoPor: string, logo?: string): void {
  const { doc, nombre } = armarReporteTrazabilidad(resultado, titulo, generadoPor, logo);
  doc.save(nombre);
}

/** Arma el PDF sin descargarlo (lo usa la prueba). */
export function armarReporteTrazabilidad(resultado: Trazabilidad, titulo: string, generadoPor: string, logo?: string): { doc: Documento; nombre: string } {
  const doc = new jsPDF({ unit: 'mm', format: 'letter' }) as Documento;
  const generado = new Date();
  const cab: Cabecera = () => encabezadoBase(doc, 'Reporte de trazabilidad', titulo, MODO_TEXTO[resultado.modo], logo);
  let y = cab();
  y = bloqueDatos(doc, 'Consulta', y, [
    ['Búsqueda', `${MODO_TEXTO[resultado.modo]}: ${resultado.valor}`],
    ['Origen de los datos', origen(resultado)],
    ['Entregas a pacientes', resumenEntregas(resultado)],
  ]);
  seccion(doc, cab, y, resultado);
  pies(doc, generado, generadoPor);
  return { doc, nombre: `trazabilidad-${resultado.valor.replace(/[^\w-]+/g, '-')}-${generado.toLocaleDateString('en-CA')}.pdf` };
}

/** Una consulta elegida para el reporte de varias (lote, expediente, paciente o receta) con su título. */
export interface ConsultaReporte {
  resultado: Trazabilidad;
  titulo: string;
}

export function descargarReporteVarios(lotes: ConsultaReporte[], generadoPor: string, logo?: string): void {
  const { doc, nombre } = armarReporteVarios(lotes, generadoPor, logo);
  doc.save(nombre);
}

/**
 * Un solo PDF con varias consultas (lotes de distintos medicamentos, expedientes, pacientes o
 * recetas): primero un resumen con todas y después cada una en su hoja, con lo mismo que su reporte.
 */
export function armarReporteVarios(lotes: ConsultaReporte[], generadoPor: string, logo?: string): { doc: Documento; nombre: string } {
  const doc = new jsPDF({ unit: 'mm', format: 'letter' }) as Documento;
  const generado = new Date();
  const folio = `${lotes.length} ${lotes.length === 1 ? 'consulta' : 'consultas'}`;
  const resumen: Cabecera = () => encabezadoBase(doc, 'Reporte de trazabilidad', folio, 'Varias consultas', logo);
  let y = resumen();
  tabla(doc, resumen, y, 'Consultas del reporte', ['Consulta', 'Origen de los datos', 'Entregas', 'Pacientes', 'Piezas entregadas'],
    lotes.map(({ resultado, titulo }) => {
      const pacientes = new Set(resultado.entregas.map((e) => e.expediente ?? e.paciente)).size;
      return [titulo, origen(resultado), resultado.entregas.length, pacientes, resultado.entregas.reduce((s, e) => s + e.piezas, 0)];
    }),
    { 2: { halign: 'right' }, 3: { halign: 'right' }, 4: { halign: 'right' } });
  lotes.forEach(({ resultado, titulo }) => {
    doc.addPage();
    const cab: Cabecera = () => encabezadoBase(doc, 'Reporte de trazabilidad', titulo, MODO_TEXTO[resultado.modo], logo);
    seccion(doc, cab, cab(), resultado);
  });
  pies(doc, generado, generadoPor);
  return { doc, nombre: `trazabilidad-${lotes.length}-consultas-${generado.toLocaleDateString('en-CA')}.pdf` };
}

/** Recorrido (línea de tiempo con datos de HAERO, tabla si todo es de Intelisis), compras, existencia y entregas. */
function seccion(doc: Documento, cab: Cabecera, y: number, resultado: Trazabilidad): number {
  const pasos = resultado.recorrido.filter((p) => p.fuente);
  if (conDatosHaero(resultado)) {
    y = lineaDeTiempo(doc, cab, y, resultado.recorrido);
  } else if (pasos.length) {
    y = tabla(doc, cab, y, resultado.modo === 'RECETA' ? 'Receta y surtido' : 'Recorrido del lote', ['Etapa', 'Fecha', 'Detalle', 'Origen'],
      pasos.map((p) => [p.etapa, p.fecha ? fecha(p.fecha) : '—', p.detalle, p.fuente ?? '']), { 2: { cellWidth: 95 } });
  }
  if (resultado.compras?.length) {
    y = tabla(doc, cab, y, 'Compras en Intelisis', ['Fecha', 'Compra', 'Origen', 'Contrato', 'Proveedor', 'Cantidad', 'Caducidad'],
      resultado.compras.map((c) => [fecha(c.fecha), c.compra ?? '—', c.origen ?? '—', c.contrato ?? '—', c.proveedor ?? '—', String(c.cantidad), fecha(c.caducidad)]),
      { 5: { halign: 'right' } });
  }
  if (resultado.existencias?.length) {
    y = tabla(doc, cab, y, 'Existencia hoy en Intelisis', ['Almacén', 'Tipo', 'Clave', 'Cantidad'],
      resultado.existencias.map((e) => [e.almacen, e.tipo, e.clave, String(e.cantidad)]), { 3: { halign: 'right' } });
  }
  return tabla(doc, cab, y, 'Entregas a pacientes', ['Fecha', 'Folio', 'Paciente', 'Expediente', 'Clave', 'Lote', 'Piezas', 'Registró', 'Origen'],
    resultado.entregas.map((e) => [e.fecha ? fecha(e.fecha) : '—', e.folio, e.paciente, e.expediente ?? '—', e.clave, e.lote, String(e.piezas), e.despachador ?? '—', e.fuente]),
    { 1: { cellWidth: 30 }, 2: { cellWidth: 33 }, 6: { halign: 'right' } },
    'No hay entregas a pacientes para esta búsqueda.', 7);
}

function origen(resultado: Trazabilidad): string {
  const fuentes = new Set([
    ...resultado.entregas.map((e) => e.fuente),
    ...resultado.recorrido.map((p) => p.fuente).filter((f): f is 'HAERO' | 'Intelisis' => !!f),
  ]);
  return fuentes.size ? [...fuentes].join(' e ') : 'Sin datos';
}

function resumenEntregas(resultado: Trazabilidad): string {
  const pacientes = new Set(resultado.entregas.map((e) => e.expediente ?? e.paciente)).size;
  const piezas = resultado.entregas.reduce((suma, e) => suma + e.piezas, 0);
  return `${resultado.entregas.length} (${pacientes} ${pacientes === 1 ? 'paciente' : 'pacientes'}, ${piezas} piezas)`;
}

/** Misma regla que la pantalla: hay línea de tiempo si el lote tiene pasos o entregas registrados en HAERO. */
export function conDatosHaero(resultado: Trazabilidad): boolean {
  return resultado.modo === 'LOTE' && (resultado.recorrido.some((p) => p.fuente === 'HAERO') || resultado.entregas.some((e) => e.fuente === 'HAERO'));
}

// Línea horizontal con un círculo por paso (relleno guinda si tiene datos, vacío si no) y el texto
// de cada paso en su columna, debajo del círculo. Toda la línea va junta en una hoja.
function lineaDeTiempo(doc: Documento, cab: Cabecera, y: number, pasos: Trazabilidad['recorrido']): number {
  const anchoUtil = doc.internal.pageSize.getWidth() - MARGEN * 2;
  const columna = anchoUtil / pasos.length;
  const texto = columna - 3;
  // Se mide antes de dibujar para saber si cabe; el detalle se corta a 7 renglones.
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(7);
  const bloques = pasos.map((p) => {
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(8.5);
    const etapa = doc.splitTextToSize(p.etapa, texto) as string[];
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(7);
    const detalle = (doc.splitTextToSize(p.detalle, texto) as string[]).slice(0, 7);
    return { etapa, detalle };
  });
  const alto = 12 + Math.max(...bloques.map((b) => b.etapa.length * 3.6 + 9 + b.detalle.length * 3));
  if (y + alto > doc.internal.pageSize.getHeight() - 20) {
    doc.addPage();
    y = cab();
  }
  subtitulo(doc, 'Recorrido del lote', y);
  anotar(doc, { tipo: 'tabla', head: ['Etapa', 'Fuente', 'Fecha', 'Detalle'],
    body: pasos.map((paso, i) => [bloques[i].etapa.join(' '), paso.fuente ?? '', paso.fecha ? fecha(paso.fecha) : 'Sin datos', bloques[i].detalle.join(' ')]) });
  const lineaY = y + 9;
  doc.setDrawColor(228, 221, 211);
  doc.setLineWidth(0.8);
  doc.line(MARGEN + columna / 2, lineaY, MARGEN + anchoUtil - columna / 2, lineaY);

  pasos.forEach((paso, i) => {
    const centro = MARGEN + columna * i + columna / 2;
    doc.setLineWidth(0.5);
    doc.setDrawColor(...(paso.fuente ? GUINDA : GRIS));
    doc.setFillColor(...(paso.fuente ? GUINDA : ([255, 255, 255] as [number, number, number])));
    doc.circle(centro, lineaY, 2, 'FD');

    let ty = lineaY + 7;
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(8.5);
    doc.setTextColor(...GUINDA);
    doc.text(bloques[i].etapa, centro, ty, { align: 'center' });
    ty += bloques[i].etapa.length * 3.6;
    doc.setFontSize(6.5);
    doc.setTextColor(...DORADO);
    doc.text(paso.fuente ? paso.fuente.toUpperCase() : ' ', centro, ty, { align: 'center' });
    ty += 3.4;
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(7.5);
    doc.setTextColor(43, 43, 43);
    doc.text(paso.fecha ? fecha(paso.fecha) : 'Sin datos', centro, ty, { align: 'center' });
    ty += 3.6;
    doc.setFontSize(7);
    doc.setTextColor(...GRIS);
    doc.text(bloques[i].detalle, centro, ty, { align: 'center', lineHeightFactor: 1.25 });
  });
  return y + alto + 4;
}

function tabla(doc: Documento, cab: Cabecera, y: number, titulo: string, encabezados: string[], filas: (string | number)[][],
               columnas: Record<number, object> = {}, vacia = 'Sin datos.', letra = ESTILO_TABLA.styles.fontSize): number {
  // El título no se queda solo al pie de la hoja.
  if (y > doc.internal.pageSize.getHeight() - 40) {
    doc.addPage();
    y = cab();
  }
  subtitulo(doc, titulo, y);
  tablaPdf(doc, {
    ...ESTILO_TABLA,
    startY: y + 3,
    // Si la tabla sigue en otra hoja, esa hoja también lleva el encabezado del hospital.
    margin: { left: MARGEN, right: MARGEN, top: ALTO_ENCABEZADO },
    didDrawPage: (datos) => {
      if (datos.pageNumber > 1) cab();
    },
    head: [encabezados],
    body: filas.length ? filas.map((f) => f.map(String)) : [[{ content: vacia, colSpan: encabezados.length, styles: { halign: 'center' } }]],
    styles: { ...ESTILO_TABLA.styles, fontSize: letra, cellPadding: letra < 8 ? 1.5 : ESTILO_TABLA.styles.cellPadding },
    columnStyles: columnas,
  });
  return doc.lastAutoTable.finalY + 10;
}

// Las fechas de Intelisis vienen sin hora (00:00): se muestra solo el día.
function fecha(valor: string | null): string {
  if (!valor) return '—';
  const sinHora = valor.length === 10 || /T00:00(:00)?(\.0+)?$/.test(valor);
  const d = new Date(valor.length === 10 ? valor + 'T12:00:00' : valor);
  return sinHora
    ? d.toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric' })
    : d.toLocaleString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false });
}
