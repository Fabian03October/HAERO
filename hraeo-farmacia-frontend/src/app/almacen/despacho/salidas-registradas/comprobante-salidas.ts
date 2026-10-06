import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { MovimientoSalida, TIPO_SALIDA_TEXTO, formatoCaducidad } from '../../solicitudes-almacen';

const GUINDA: [number, number, number] = [97, 18, 50];
const DORADO: [number, number, number] = [165, 127, 44];
const GRIS: [number, number, number] = [111, 114, 113];
const MARGEN = 16;

type Documento = jsPDF & { lastAutoTable: { finalY: number } };

/**
 * Comprobante en PDF de las salidas elegidas. Cada salida a Farmacia se agrupa con
 * todas las entregas de su solicitud (también las de otros días), para que quede en
 * una sola hoja quién la pidió, quién la despachó y cuándo. Préstamos, transferencias,
 * canjes y caducados salen cada uno en su hoja.
 *
 * @param elegidas salidas marcadas en la tabla
 * @param historial todas las salidas conocidas, para completar cada solicitud
 * @param generadoPor nombre de quien descarga el comprobante
 * @param logo logo del hospital como data URL (ver cargarLogo); sin él solo va el texto
 */
export function descargarComprobante(elegidas: MovimientoSalida[], historial: MovimientoSalida[], generadoPor: string, logo?: string): void {
  const doc = new jsPDF({ unit: 'mm', format: 'letter' }) as Documento;
  const generado = new Date();

  const grupos: MovimientoSalida[][] = [];
  const solicitudesVistas = new Set<number>();
  for (const salida of ordenarPorFecha(elegidas)) {
    if (salida.solicitudId == null) {
      grupos.push([salida]);
    } else if (!solicitudesVistas.has(salida.solicitudId)) {
      solicitudesVistas.add(salida.solicitudId);
      grupos.push(ordenarPorFecha(historial.filter((otra) => otra.solicitudId === salida.solicitudId)));
    }
  }

  grupos.forEach((grupo, indice) => {
    if (indice > 0) doc.addPage();
    let y = encabezado(doc, grupo[0], logo);
    y = grupo[0].solicitudId != null ? datosSolicitud(doc, grupo, y) : datosMovimiento(doc, grupo[0], y);
    y = tablaEntregas(doc, grupo, y);
    y = leyenda(doc, grupo, y, logo);
    firmas(doc, grupo, y, logo);
  });

  pies(doc, generado, generadoPor);
  doc.save(nombreArchivo(grupos, generado));
}

/** Lee el logo del hospital (public/logo-hraeo-gris.png) como data URL; undefined si no se pudo. */
export async function cargarLogo(): Promise<string | undefined> {
  try {
    const respuesta = await fetch('logo-hraeo-gris.png');
    if (!respuesta.ok) return undefined;
    const imagen = await respuesta.blob();
    return await new Promise((resolver) => {
      const lector = new FileReader();
      lector.onload = () => resolver(lector.result as string);
      lector.onerror = () => resolver(undefined);
      lector.readAsDataURL(imagen);
    });
  } catch {
    return undefined;
  }
}

// Proporción del logo (512 x 222 px).
const LOGO_ALTO = 15;
const LOGO_ANCHO = (LOGO_ALTO * 256) / 111;

function encabezado(doc: Documento, salida: MovimientoSalida, logo?: string): number {
  const ancho = doc.internal.pageSize.getWidth();
  let x = MARGEN;
  if (logo) {
    doc.addImage(logo, 'PNG', MARGEN, 9, LOGO_ANCHO, LOGO_ALTO);
    doc.setDrawColor(228, 221, 211);
    doc.setLineWidth(0.3);
    x = MARGEN + LOGO_ANCHO + 6;
    doc.line(x - 3, 10, x - 3, 23);
  }

  doc.setTextColor(...GUINDA);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(14);
  doc.text('Comprobante de salida de Almacén', x, 15);
  doc.setTextColor(...GRIS);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8.5);
  doc.text('Hospital Regional de Alta Especialidad de Oaxaca', x, 20.5);
  doc.text('Farmacia hospitalaria · Almacén de medicamentos', x, 24.5);

  doc.setTextColor(...GUINDA);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(13);
  doc.text(referencia(salida), ancho - MARGEN, 15, { align: 'right' });
  doc.setTextColor(...GRIS);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8.5);
  doc.text(TIPO_SALIDA_TEXTO[salida.tipo], ancho - MARGEN, 20.5, { align: 'right' });

  doc.setFillColor(...GUINDA);
  doc.rect(MARGEN, 29, ancho - MARGEN * 2, 1.1, 'F');
  doc.setFillColor(...DORADO);
  doc.rect(MARGEN, 30.1, ancho - MARGEN * 2, 0.5, 'F');
  return 41;
}

// Si lo que sigue no cabe, continúa en otra hoja con el mismo encabezado.
function asegurarEspacio(doc: Documento, y: number, alto: number, salida: MovimientoSalida, logo?: string): number {
  if (y + alto <= doc.internal.pageSize.getHeight() - 20) return y;
  doc.addPage();
  return encabezado(doc, salida, logo);
}

function datosSolicitud(doc: Documento, grupo: MovimientoSalida[], y: number): number {
  const primera = grupo[0];
  const entregadas = grupo.reduce((total, salida) => total + salida.cajas, 0);
  const pedidas = primera.cantidadSolicitada;
  return bloqueDatos(doc, 'Solicitud de Farmacia', y, [
    ['Medicamento', `${primera.clave} · ${primera.nombreGenerico}`],
    ['Solicitó (Farmacia)', primera.solicitadoPor ?? 'Sin dato'],
    ['Fecha y hora de la solicitud', primera.fechaSolicitud ? fechaHora(primera.fechaSolicitud) : 'Sin dato'],
    ['Cajas solicitadas', pedidas != null ? String(pedidas) : 'Sin dato'],
    ['Cajas entregadas', pedidas != null ? `${entregadas} de ${pedidas}${entregadas < pedidas ? ` (faltan ${pedidas - entregadas})` : ' (completa)'}` : String(entregadas)],
  ]);
}

function datosMovimiento(doc: Documento, salida: MovimientoSalida, y: number): number {
  const filas: [string, string][] = [
    ['Tipo de salida', TIPO_SALIDA_TEXTO[salida.tipo]],
    ['Medicamento', `${salida.clave} · ${salida.nombreGenerico}`],
  ];
  if (salida.institucion) filas.push(['Institución', salida.institucion]);
  filas.push(['Registró (Almacén)', salida.usuario || 'Sin dato'], ['Fecha y hora', fechaHora(salida.fecha)]);
  return bloqueDatos(doc, 'Datos del movimiento', y, filas);
}

function bloqueDatos(doc: Documento, titulo: string, y: number, filas: [string, string][]): number {
  subtitulo(doc, titulo, y);
  autoTable(doc, {
    startY: y + 3,
    margin: { left: MARGEN, right: MARGEN },
    body: filas,
    theme: 'plain',
    styles: { fontSize: 10, cellPadding: { top: 1.8, bottom: 1.8, left: 2, right: 2 }, textColor: [43, 43, 43] },
    columnStyles: { 0: { cellWidth: 58, textColor: GRIS }, 1: { fontStyle: 'bold' } },
  });
  return doc.lastAutoTable.finalY + 8;
}

/** Texto de recibido: deja por escrito de quién se recibió la solicitud y cuándo. */
function leyenda(doc: Documento, grupo: MovimientoSalida[], y: number, logo?: string): number {
  const primera = grupo[0];
  const ultima = grupo[grupo.length - 1];
  const cajas = grupo.reduce((total, salida) => total + salida.cajas, 0);
  let texto: string;
  if (primera.solicitudId != null) {
    const quien = primera.solicitadoPor ? `de ${primera.solicitadoPor}, del área de Farmacia,` : 'del área de Farmacia';
    const cuando = primera.fechaSolicitud ? ` el día ${fechaLarga(primera.fechaSolicitud)}` : '';
    texto =
      `Recibí la solicitud ${primera.folio} ${quien}${cuando}, ` +
      `por ${primera.cantidadSolicitada ?? cajas} caja(s) de ${primera.clave} · ${primera.nombreGenerico}, ` +
      `y la atendí entregando ${cajas} caja(s) de los lotes y ubicaciones que se detallan en este comprobante.`;
  } else {
    const destino = primera.institucion ? ` a ${primera.institucion}` : '';
    texto =
      `Se registró la salida de ${cajas} caja(s) de ${primera.clave} · ${primera.nombreGenerico}${destino} ` +
      `el día ${fechaLarga(ultima.fecha)}, del lote y ubicación que se detallan en este comprobante.`;
  }
  const ancho = doc.internal.pageSize.getWidth() - MARGEN * 2;
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10);
  const lineas = doc.splitTextToSize(texto, ancho - 10) as string[];
  const alto = lineas.length * 5.2 + 8;
  y = asegurarEspacio(doc, y, alto, primera, logo);

  doc.setFillColor(248, 245, 240);
  doc.rect(MARGEN, y, ancho, alto, 'F');
  doc.setFillColor(...DORADO);
  doc.rect(MARGEN, y, 1.2, alto, 'F');
  doc.setTextColor(43, 43, 43);
  doc.text(lineas, MARGEN + 5, y + 6.5, { lineHeightFactor: 1.5 });
  return y + alto + 8;
}

function tablaEntregas(doc: Documento, grupo: MovimientoSalida[], y: number): number {
  const esSolicitud = grupo[0].solicitudId != null;
  subtitulo(doc, esSolicitud ? 'Entregas de Almacén' : 'Detalle de la salida', y);
  const total = grupo.reduce((suma, salida) => suma + salida.cajas, 0);
  autoTable(doc, {
    startY: y + 3,
    margin: { left: MARGEN, right: MARGEN },
    head: [['Fecha y hora', 'Lote', 'Caducidad', 'Ubicación', 'Cajas', esSolicitud ? 'Despachó (Almacén)' : 'Registró']],
    body: grupo.map((salida) => [fechaHora(salida.fecha), salida.lote, formatoCaducidad(salida.caducidad), salida.ubicacion, String(salida.cajas), salida.usuario || 'Sin dato']),
    foot: grupo.length > 1 ? [['', '', '', 'Total', { content: String(total), styles: { halign: 'right' } }, '']] : undefined,
    theme: 'grid',
    styles: { fontSize: 9, cellPadding: 2.2, lineColor: [228, 221, 211], lineWidth: 0.2, textColor: [43, 43, 43] },
    headStyles: { fillColor: GUINDA, textColor: 255, fontStyle: 'bold' },
    footStyles: { fillColor: [246, 233, 237], textColor: GUINDA, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: [250, 248, 245] },
    columnStyles: { 4: { halign: 'right' } },
  });
  return doc.lastAutoTable.finalY + 10;
}

function firmas(doc: Documento, grupo: MovimientoSalida[], y: number, logo?: string): void {
  const salida = grupo[0];
  // Por Almacén firma quien hizo la última entrega.
  const almacen = grupo[grupo.length - 1].usuario;
  y = asegurarEspacio(doc, y, 34, salida, logo);
  const alto = doc.internal.pageSize.getHeight();
  const ancho = doc.internal.pageSize.getWidth();
  // Las firmas van hacia el pie de la hoja si hay lugar; si no, debajo de lo anterior.
  const lineaY = Math.max(y + 22, Math.min(alto - 44, y + 60));
  const columna = (ancho - MARGEN * 2 - 20) / 2;
  const firmantes: [string, string][] =
    salida.solicitudId != null
      ? [
          ['Entregó · Almacén', almacen],
          ['Recibió · Farmacia', salida.solicitadoPor ?? ''],
        ]
      : [
          ['Entregó · Almacén', almacen],
          [salida.institucion ? `Recibió · ${salida.institucion}` : 'Autorizó', ''],
        ];
  doc.setDrawColor(...GRIS);
  doc.setLineWidth(0.3);
  firmantes.forEach(([cargo, nombre], i) => {
    const centro = MARGEN + i * (columna + 20) + columna / 2;
    doc.line(centro - columna / 2, lineaY, centro + columna / 2, lineaY);
    doc.setTextColor(43, 43, 43);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(9.5);
    doc.text(nombre || 'Nombre', centro, lineaY + 5, { align: 'center', maxWidth: columna });
    doc.setTextColor(...GRIS);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8.5);
    doc.text(`${cargo} · firma`, centro, lineaY + 9.5, { align: 'center', maxWidth: columna });
  });
}

function pies(doc: Documento, generado: Date, generadoPor: string): void {
  const paginas = doc.getNumberOfPages();
  const ancho = doc.internal.pageSize.getWidth();
  const alto = doc.internal.pageSize.getHeight();
  for (let pagina = 1; pagina <= paginas; pagina++) {
    doc.setPage(pagina);
    doc.setDrawColor(228, 221, 211);
    doc.line(MARGEN, alto - 14, ancho - MARGEN, alto - 14);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8);
    doc.setTextColor(...GRIS);
    doc.text(`Generado el ${fechaHora(generado)} por ${generadoPor || 'usuario del sistema'}.`, MARGEN, alto - 9);
    doc.text(`Página ${pagina} de ${paginas}`, ancho - MARGEN, alto - 9, { align: 'right' });
  }
}

function subtitulo(doc: Documento, texto: string, y: number): void {
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.setTextColor(...GUINDA);
  doc.text(texto.toUpperCase(), MARGEN, y);
}

// Folio de la solicitud; los movimientos sin solicitud se identifican por su número.
function referencia(salida: MovimientoSalida): string {
  return salida.solicitudId != null ? salida.folio : `MOV-${String(salida.id).padStart(5, '0')}`;
}

function ordenarPorFecha(salidas: MovimientoSalida[]): MovimientoSalida[] {
  return [...salidas].sort((a, b) => a.fecha.localeCompare(b.fecha));
}

// "6 de octubre de 2026 a las 10:43 h"
function fechaLarga(fecha: string): string {
  const valor = new Date(fecha);
  const dia = valor.toLocaleDateString('es-MX', { day: 'numeric', month: 'long', year: 'numeric' });
  const hora = valor.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', hour12: false });
  return `${dia} a las ${hora} h`;
}

function fechaHora(fecha: string | Date): string {
  const valor = typeof fecha === 'string' ? new Date(fecha) : fecha;
  return valor.toLocaleString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false });
}

function nombreArchivo(grupos: MovimientoSalida[][], generado: Date): string {
  const dia = generado.toLocaleDateString('en-CA');
  if (grupos.length === 1) return `salida-${referencia(grupos[0][0]).replace(/[^\w-]+/g, '-')}-${dia}.pdf`;
  return `salidas-${grupos.length}-${dia}.pdf`;
}
