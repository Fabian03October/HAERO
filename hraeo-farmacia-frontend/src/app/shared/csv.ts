// Reportes en CSV que se abren directo en Excel (con BOM para que respete los acentos).

export const hoyIso = () => new Date().toISOString().slice(0, 10);

/** Descarga un CSV con una línea de título, los encabezados y las filas. */
export function descargarCsv(nombre: string, titulo: string, encabezados: string[], filas: (string | number | null)[][]): void {
  const celda = (valor: string | number | null) => `"${String(valor ?? '').replace(/"/g, '""')}"`;
  const texto = '﻿' + [[titulo], encabezados, ...filas].map((fila) => fila.map(celda).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob([texto], { type: 'text/csv;charset=utf-8' }));
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.click();
  URL.revokeObjectURL(url);
}
