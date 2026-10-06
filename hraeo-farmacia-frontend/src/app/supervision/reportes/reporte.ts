import * as XLSX from 'xlsx';

// Utilidades compartidas por los reportes de Supervisión.

/** Descarga un reporte como Excel: una hoja con encabezados y renglones. */
export function descargarExcel(nombreArchivo: string, hoja: string, encabezados: string[], filas: (string | number)[][]): void {
  const datos = XLSX.utils.aoa_to_sheet([encabezados, ...filas]);
  datos['!cols'] = encabezados.map((encabezado, indice) => ({
    wch: Math.min(45, Math.max(encabezado.length, ...filas.map((fila) => String(fila[indice] ?? '').length)) + 2),
  }));
  const libro = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(libro, datos, hoja);
  XLSX.writeFile(libro, `${nombreArchivo}-${new Date().toLocaleDateString('en-CA')}.xlsx`);
}

/** ¿La fecha (AAAA-MM-DD…) cae en el rango? Los extremos vacíos no limitan. */
export function enRango(fecha: string | null | undefined, desde: string, hasta: string): boolean {
  if (!desde && !hasta) return true;
  if (!fecha) return false;
  const dia = fecha.slice(0, 10);
  return (!desde || dia >= desde) && (!hasta || dia <= hasta);
}

/** AAAA-MM-DD… → DD/MM/AAAA. */
export function fechaCorta(fecha: string | null | undefined): string {
  if (!fecha) return '';
  const [anio, mes, dia] = fecha.slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
}
