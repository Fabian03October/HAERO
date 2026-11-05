import { jsPDF } from 'jspdf';
import autoTable, { RowInput } from 'jspdf-autotable';
import * as XLSX from 'xlsx';
import { CajaQuirofano, TURNOS, ValeCaja } from './cajas-quirofano';

// "Vale de medicamentos para el uso en el quirófano" con el acomodo del formato de Farmacia (los 3
// servicios usan el mismo: salas de quirófano, imagen y endoscopias, hemodinamia). Se genera en PDF
// o en Excel: en blanco (al dar de alta la caja, con las cantidades de su listado) o el de una salida.

export const TITULO_HOSPITAL = 'FARMACIA DEL HOSPITAL REGIONAL DE ALTA ESPECIALIDAD DE OAXACA';
export const TITULO_VALE = 'VALE DE MEDICAMENTOS PARA EL USO EN EL QUIROFANO';
export const NOTA_VALE = 'NOTA: El presente documento tiene validez durante la jornada laboral de los médicos asignados en el servicio de quirófano.';
export const FIRMAS_VALE = ['MÉDICO ANESTESIÓLOGO', 'PERSONAL QUE ENTREGA EN FARMACIA', 'PERSONAL QUE RECIBE LA DEVOLUCIÓN'];

export interface RenglonVale {
  descripcion: string;
  surtido: number | null;
  devolucion: number | null;
  // Una fila por paciente que lo utilizó (al menos una, en blanco).
  usos: { expediente: string; nombre: string; piezas: number | null }[];
}

export interface DatosVale {
  caja: string;
  nombreCaja: string;
  servicio: string;
  fecha: string;
  turno: string | null;
  renglones: RenglonVale[];
  // Nombres para las firmas (vacíos en el vale en blanco).
  firmas: [string, string, string];
  folio: string;
}

const fechaCorta = (valor: string | null | undefined) => {
  if (!valor) return '';
  const [anio, mes, dia] = valor.slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
};

/** Los datos del vale: el de una salida o, sin vale, en blanco con las cantidades del listado de la caja. */
export function datosVale(caja: CajaQuirofano, vale: ValeCaja | null): DatosVale {
  if (!vale) {
    return {
      caja: caja.codigo,
      nombreCaja: caja.nombre,
      servicio: caja.servicio,
      fecha: '',
      turno: null,
      renglones: caja.renglones.map((r) => ({ descripcion: r.medicamento, surtido: r.cantidad, devolucion: null, usos: [{ expediente: '', nombre: '', piezas: null }] })),
      firmas: ['', '', ''],
      folio: 'Vale en blanco',
    };
  }
  const abierto = !vale.recibidoEn;
  return {
    caja: caja.codigo,
    nombreCaja: caja.nombre,
    servicio: vale.sala || caja.servicio,
    fecha: fechaCorta(vale.entregadoEn),
    turno: vale.turno,
    renglones: vale.partidas.map((p) => {
      const usos = vale.usos.filter((u) => u.clave === p.clave);
      return {
        descripcion: p.medicamento,
        surtido: p.surtido,
        devolucion: abierto ? null : p.surtido - p.utilizado,
        usos: usos.length
          ? usos.map((u) => ({ expediente: u.expediente, nombre: u.nombre, piezas: u.piezas }))
          : [{ expediente: '', nombre: '', piezas: abierto ? null : 0 }],
      };
    }),
    firmas: [vale.medico ?? '', vale.entregadoPor ?? '', vale.recibidoPor ?? ''],
    folio: `Vale HAERO V-${vale.id}`,
  };
}

function textoTurno(turno: string | null): string {
  return 'Turno: ' + TURNOS.map((t) => `${t.etiqueta} ( ${turno === t.valor ? 'X' : '  '} )`).join('   ');
}

function nombreArchivo(datos: DatosVale, extension: string): string {
  const servicio = datos.nombreCaja.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^A-Za-z0-9]+/g, '-').replace(/^-|-$/g, '');
  return `vale-quirofano-${datos.caja}-${servicio || 'caja'}.${extension}`;
}

// ---------- PDF ----------

const GUINDA: [number, number, number] = [97, 18, 50];

export function descargarValePdf(datos: DatosVale, logo?: string): void {
  const doc = new jsPDF({ unit: 'mm', format: 'letter', orientation: 'landscape' }) as jsPDF & { lastAutoTable: { finalY: number } };
  const ancho = doc.internal.pageSize.getWidth();
  const margen = 12;
  let y = 12;
  if (logo) {
    doc.addImage(logo, 'PNG', margen, y - 2, 34, 15);
  }
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(12);
  doc.text(TITULO_HOSPITAL, ancho / 2 + 10, y + 2, { align: 'center' });
  doc.setFontSize(10);
  doc.text(TITULO_VALE, ancho / 2 + 10, y + 8, { align: 'center' });
  y += 20;
  doc.setFontSize(9);
  doc.setFont('helvetica', 'bold');
  doc.text('Servicio:', margen, y);
  doc.setFont('helvetica', 'normal');
  doc.text(datos.servicio, margen + 16, y, { maxWidth: 90 });
  doc.setFont('helvetica', 'bold');
  doc.text('Fecha:', margen + 110, y);
  doc.setFont('helvetica', 'normal');
  doc.text(datos.fecha || '____________', margen + 122, y);
  doc.text(textoTurno(datos.turno), ancho - margen, y, { align: 'right' });

  const cuerpo: RowInput[] = [];
  for (const r of datos.renglones) {
    r.usos.forEach((u, i) => {
      const fila: RowInput = [u.expediente, u.nombre];
      if (i === 0) {
        fila.push({ content: r.descripcion, rowSpan: r.usos.length }, { content: r.surtido ?? '', rowSpan: r.usos.length, styles: { halign: 'center' } });
      }
      fila.push({ content: u.piezas ?? '', styles: { halign: 'center' } });
      if (i === 0) fila.push({ content: r.devolucion ?? '', rowSpan: r.usos.length, styles: { halign: 'center' } });
      cuerpo.push(fila);
    });
  }
  autoTable(doc, {
    startY: y + 4,
    margin: { left: margen, right: margen },
    head: [['No. EXPEDIENTE', 'NOMBRE DEL PACIENTE', 'DESCRIPCIÓN DE FÁRMACOS', 'SURTIDO', 'UTILIZADO', 'DEVOLUCIÓN']],
    body: cuerpo,
    theme: 'grid',
    styles: { fontSize: 8, cellPadding: 1.4, lineColor: [0, 0, 0], lineWidth: 0.2, textColor: [0, 0, 0], valign: 'middle', minCellHeight: 6 },
    headStyles: { fillColor: [255, 255, 255], textColor: [0, 0, 0], fontStyle: 'bold', halign: 'center' },
    columnStyles: { 0: { cellWidth: 30 }, 1: { cellWidth: 52 }, 3: { cellWidth: 20 }, 4: { cellWidth: 22 }, 5: { cellWidth: 24 } },
  });

  let fin = doc.lastAutoTable.finalY + 6;
  doc.setFontSize(8.5);
  doc.text(NOTA_VALE, margen, fin);
  // Firmas al final, como en el formato.
  fin += 22;
  if (fin > doc.internal.pageSize.getHeight() - 20) {
    doc.addPage();
    fin = 40;
  }
  const columna = (ancho - margen * 2) / 3;
  FIRMAS_VALE.forEach((rol, i) => {
    const centro = margen + columna * i + columna / 2;
    doc.setFont('helvetica', 'bold');
    doc.text(rol, centro, fin - 14, { align: 'center' });
    doc.setFont('helvetica', 'normal');
    if (datos.firmas[i]) doc.text(datos.firmas[i], centro, fin - 2, { align: 'center' });
    doc.setDrawColor(0);
    doc.line(centro - columna / 2 + 8, fin, centro + columna / 2 - 8, fin);
    doc.text('NOMBRE Y FIRMA', centro, fin + 4, { align: 'center' });
  });
  doc.setFontSize(7.5);
  doc.setTextColor(...GUINDA);
  doc.text(`Caja ${datos.caja} · ${datos.nombreCaja} · ${datos.folio}`, margen, doc.internal.pageSize.getHeight() - 6);
  doc.save(nombreArchivo(datos, 'pdf'));
}

// ---------- Excel ----------

export function descargarValeExcel(datos: DatosVale): void {
  const filas: (string | number | null)[][] = [
    [TITULO_HOSPITAL],
    [TITULO_VALE],
    [`Servicio: ${datos.servicio}`, null, `Fecha: ${datos.fecha || '____________'}`, textoTurno(datos.turno)],
    [],
    ['No. EXPEDIENTE', 'NOMBRE DEL PACIENTE', 'DESCRIPCIÓN DE FÁRMACOS', 'SURTIDO', 'UTILIZADO', 'DEVOLUCIÓN'],
  ];
  const merges: XLSX.Range[] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: 5 } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: 5 } },
    { s: { r: 2, c: 0 }, e: { r: 2, c: 1 } },
    { s: { r: 2, c: 3 }, e: { r: 2, c: 5 } },
  ];
  for (const r of datos.renglones) {
    const inicio = filas.length;
    r.usos.forEach((u, i) => {
      filas.push([u.expediente, u.nombre, i === 0 ? r.descripcion : null, i === 0 ? r.surtido : null, u.piezas, i === 0 ? r.devolucion : null]);
    });
    if (r.usos.length > 1) {
      for (const c of [2, 3, 5]) merges.push({ s: { r: inicio, c }, e: { r: inicio + r.usos.length - 1, c } });
    }
  }
  filas.push([], [NOTA_VALE], [], [], []);
  merges.push({ s: { r: filas.length - 4, c: 0 }, e: { r: filas.length - 4, c: 5 } });
  // Firmas al final: rol, espacio para firmar, nombre y la leyenda.
  const filaRol = filas.length;
  filas.push([FIRMAS_VALE[0], null, FIRMAS_VALE[1], null, FIRMAS_VALE[2], null]);
  filas.push([], []);
  filas.push([datos.firmas[0], null, datos.firmas[1], null, datos.firmas[2], null]);
  filas.push(['______________________________', null, '______________________________', null, '______________________________', null]);
  filas.push(['NOMBRE Y FIRMA', null, 'NOMBRE Y FIRMA', null, 'NOMBRE Y FIRMA', null]);
  for (const r of [filaRol, filaRol + 3, filaRol + 4, filaRol + 5]) {
    for (const c of [0, 2, 4]) merges.push({ s: { r, c }, e: { r, c: c + 1 } });
  }
  filas.push([], [`Caja ${datos.caja} · ${datos.nombreCaja} · ${datos.folio}`]);

  const hoja = XLSX.utils.aoa_to_sheet(filas);
  hoja['!merges'] = merges;
  hoja['!cols'] = [{ wch: 16 }, { wch: 30 }, { wch: 48 }, { wch: 11 }, { wch: 12 }, { wch: 13 }];
  hoja['!pageSetup'] = { orientation: 'landscape' };
  const libro = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(libro, hoja, datos.nombreCaja.slice(0, 31).replace(/[\\/?*[\]:]/g, '') || 'Vale');
  XLSX.writeFile(libro, nombreArchivo(datos, 'xlsx'));
}
