// Excel con el mismo contenido y colores que el PDF. Mientras se arma un reporte, sus piezas (encabezado, datos,
// subtítulos, tablas, firmas, pie) se anotan en el documento; con esas notas se arma el Excel. Así cualquier reporte
// hecho con esas piezas sale también en Excel sin escribirlo dos veces.

type Rgb = [number, number, number];
type Celda = string | number | { content?: unknown } | null | undefined;

export type BloqueExcel =
  | { tipo: 'titulo'; titulo: string; folio: string; subtitulo: string; logo?: string }
  | { tipo: 'subtitulo'; texto: string }
  | { tipo: 'datos'; filas: [string, string][] }
  | { tipo: 'texto'; texto: string }
  | { tipo: 'tabla'; head: Celda[]; body: Celda[][]; foot?: Celda[]; color?: Rgb }
  | { tipo: 'firmas'; firmantes: [string, string][] }
  | { tipo: 'pie'; texto: string };

const GUINDA: Rgb = [97, 18, 50];
const FONDO_ALTERNO = 'FFFAF8F5';
const FONDO_TOTAL = 'FFF6E9ED';
const BORDE = 'FFE4DDD3';

type ConNotas = { __excel?: BloqueExcel[] };

/** Anota una pieza del reporte (la llaman las mismas funciones que la dibujan en el PDF). */
export function anotar(doc: object, bloque: BloqueExcel): void {
  const notas = ((doc as ConNotas).__excel ??= []);
  // El encabezado se repite en cada hoja del PDF que continúa; en el Excel basta una vez por folio.
  if (bloque.tipo === 'titulo' && notas.some((n) => n.tipo === 'titulo' && n.folio === bloque.folio && n.titulo === bloque.titulo)) return;
  // El pie del PDF se pone en cada página; en el Excel, una sola vez.
  if (bloque.tipo === 'pie' && notas.some((n) => n.tipo === 'pie')) return;
  notas.push(bloque);
}

function texto(celda: Celda): string {
  if (celda === null || celda === undefined) return '';
  if (typeof celda === 'object') return String((celda as { content?: unknown }).content ?? '');
  return String(celda);
}

function argb([r, g, b]: Rgb): string {
  return 'FF' + [r, g, b].map((n) => n.toString(16).padStart(2, '0')).join('').toUpperCase();
}

/** Arma y descarga el Excel de un reporte ya armado en PDF (mismos datos y colores). */
export async function descargarExcel(doc: object, archivo: string): Promise<void> {
  const buffer = await armarExcel(doc);
  const url = URL.createObjectURL(new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = archivo.endsWith('.xlsx') ? archivo : `${archivo}.xlsx`;
  enlace.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** El Excel en memoria (también lo usa la prueba). */
export async function armarExcel(doc: object): Promise<ArrayBuffer> {
  const ExcelJS = (await import('exceljs')).default;
  const notas = (doc as ConNotas).__excel ?? [];
  const libro = new ExcelJS.Workbook();
  const hoja = libro.addWorksheet('Reporte', { views: [{ showGridLines: false }], pageSetup: { paperSize: 1 as never, fitToPage: true, fitToWidth: 1, fitToHeight: 0 } });
  const columnas = Math.max(4, ...notas.map((n) => (n.tipo === 'tabla' ? n.head.length : 0)));
  hoja.columns = Array.from({ length: columnas }, () => ({ width: 18 }));
  const guinda = argb(GUINDA);
  let fila = 1;
  const borde = { style: 'thin' as const, color: { argb: BORDE } };

  for (const n of notas) {
    switch (n.tipo) {
      case 'titulo': {
        if (fila > 1) fila += 1;
        let col = 1;
        if (n.logo?.startsWith('data:image/')) {
          const imagen = libro.addImage({ base64: n.logo, extension: n.logo.includes('image/png') ? 'png' : 'jpeg' });
          hoja.addImage(imagen, { tl: { col: 0, row: fila - 1 }, ext: { width: 120, height: 52 } });
          col = 2;
        }
        const t = hoja.getCell(fila, col);
        t.value = n.titulo;
        t.font = { bold: true, size: 16, color: { argb: guinda } };
        const folio = hoja.getCell(fila, columnas);
        folio.value = n.folio;
        folio.font = { bold: true, size: 13, color: { argb: guinda } };
        folio.alignment = { horizontal: 'right' };
        hoja.getCell(fila + 1, col).value = 'Hospital Regional de Alta Especialidad de Oaxaca';
        hoja.getCell(fila + 2, col).value = 'Farmacia hospitalaria · Almacén de medicamentos';
        hoja.getCell(fila + 1, columnas).value = n.subtitulo;
        hoja.getCell(fila + 1, columnas).alignment = { horizontal: 'right' };
        for (const r of [fila + 1, fila + 2]) hoja.getRow(r).font = { color: { argb: 'FF6F7271' } };
        // Título y nombre del hospital ocupan varias columnas (así no ensanchan la primera).
        if (columnas - 1 > col) for (const r of [fila, fila + 1, fila + 2]) hoja.mergeCells(r, col, r, columnas - 1);
        // Franja guinda y dorada, como en el PDF.
        for (let c = 1; c <= columnas; c++) {
          hoja.getCell(fila + 3, c).border = { bottom: { style: 'thick', color: { argb: guinda } } };
          hoja.getCell(fila + 4, c).border = { top: { style: 'thin', color: { argb: 'FFA57F2C' } } };
        }
        fila += 6;
        break;
      }
      case 'subtitulo': {
        hoja.mergeCells(fila, 1, fila, columnas);
        const c = hoja.getCell(fila, 1);
        c.value = n.texto.toUpperCase();
        c.font = { bold: true, size: 12, color: { argb: guinda } };
        fila += 1;
        break;
      }
      case 'datos':
        for (const [etiqueta, valor] of n.filas) {
          hoja.getCell(fila, 1).value = etiqueta;
          hoja.getCell(fila, 1).font = { color: { argb: 'FF6F7271' } };
          hoja.mergeCells(fila, 2, fila, columnas);
          hoja.getCell(fila, 2).value = valor;
          hoja.getCell(fila, 2).font = { bold: true };
          fila += 1;
        }
        fila += 1;
        break;
      case 'texto':
        hoja.mergeCells(fila, 1, fila + 2, columnas);
        hoja.getCell(fila, 1).value = n.texto;
        hoja.getCell(fila, 1).alignment = { wrapText: true, vertical: 'top' };
        fila += 4;
        break;
      case 'tabla': {
        const color = argb(n.color ?? GUINDA);
        n.head.forEach((h, i) => {
          const c = hoja.getCell(fila, i + 1);
          c.value = texto(h);
          c.font = { bold: true, color: { argb: 'FFFFFFFF' } };
          c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: color } };
          c.alignment = { vertical: 'middle', wrapText: true };
          c.border = { top: borde, bottom: borde, left: borde, right: borde };
        });
        fila += 1;
        n.body.forEach((renglon, r) => {
          renglon.forEach((v, i) => {
            const c = hoja.getCell(fila, i + 1);
            const valor = texto(v);
            // Los números (cajas, piezas) quedan como número para poder sumar en Excel.
            c.value = /^-?\d+(\.\d+)?$/.test(valor) ? Number(valor) : valor;
            c.alignment = { vertical: 'top', wrapText: true };
            c.border = { top: borde, bottom: borde, left: borde, right: borde };
            if (r % 2 === 1) c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: FONDO_ALTERNO } };
          });
          fila += 1;
        });
        if (n.foot?.some((v) => texto(v))) {
          n.foot.forEach((v, i) => {
            const c = hoja.getCell(fila, i + 1);
            const valor = texto(v);
            c.value = /^-?\d+(\.\d+)?$/.test(valor) ? Number(valor) : valor;
            c.font = { bold: true, color: { argb: guinda } };
            c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: FONDO_TOTAL } };
            c.border = { top: borde, bottom: borde, left: borde, right: borde };
          });
          fila += 1;
        }
        fila += 1;
        break;
      }
      case 'firmas': {
        fila += 3;
        const ancho = Math.max(1, Math.floor(columnas / n.firmantes.length));
        n.firmantes.forEach(([cargo, nombre], i) => {
          const desde = i * ancho + 1;
          const hasta = Math.min(columnas, desde + ancho - 1);
          for (let c = desde; c <= hasta; c++) hoja.getCell(fila, c).border = { top: { style: 'thin', color: { argb: 'FF6F7271' } } };
          hoja.mergeCells(fila, desde, fila, hasta);
          hoja.getCell(fila, desde).value = nombre || 'Nombre';
          hoja.getCell(fila, desde).font = { bold: true };
          hoja.getCell(fila, desde).alignment = { horizontal: 'center' };
          hoja.mergeCells(fila + 1, desde, fila + 1, hasta);
          hoja.getCell(fila + 1, desde).value = `${cargo} · firma`;
          hoja.getCell(fila + 1, desde).alignment = { horizontal: 'center' };
          hoja.getCell(fila + 1, desde).font = { color: { argb: 'FF6F7271' } };
        });
        fila += 3;
        break;
      }
      case 'pie':
        hoja.mergeCells(fila, 1, fila, columnas);
        hoja.getCell(fila, 1).value = n.texto;
        hoja.getCell(fila, 1).font = { size: 9, color: { argb: 'FF6F7271' } };
        fila += 1;
        break;
    }
  }
  // Ancho de cada columna según su contenido (ni muy angosta ni más de 48).
  hoja.columns.forEach((columna) => {
    let max = 10;
    columna.eachCell?.({ includeEmpty: false }, (c) => {
      if (c.isMerged) return;
      max = Math.max(max, Math.min(48, String(c.value ?? '').length + 2));
    });
    columna.width = max;
  });

  return (await libro.xlsx.writeBuffer()) as ArrayBuffer;
}
