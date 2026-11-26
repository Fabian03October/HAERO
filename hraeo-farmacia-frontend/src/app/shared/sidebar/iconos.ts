// Íconos del menú lateral: trazos SVG de 24×24 (línea de 1.8, sin relleno), sin librerías externas.
export const ICONOS = {
  inicio: ['M3 11l9-7 9 7', 'M5 10v10h14V10', 'M10 20v-6h4v6'],
  ventanilla: ['M6 3h12v18l-3-2-3 2-3-2-3 2z', 'M9 8h6', 'M9 12h6', 'M9 16h3'],
  piso: ['M3 18V7', 'M3 14h18v4', 'M21 14v-3a3 3 0 0 0-3-3h-7v6', 'M7 11.5a1.5 1.5 0 1 0 0-.01'],
  abasto: ['M3 8l9-5 9 5-9 5z', 'M3 8v8l9 5 9-5V8', 'M12 13v8'],
  consultas: ['M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z', 'M21 21l-5-5'],
  configuracion: ['M4 6h10', 'M18 6h2', 'M16 4v4', 'M4 12h4', 'M12 12h8', 'M10 10v4', 'M4 18h12', 'M20 18h0', 'M18 16v4'],
  recepcion: ['M4 13l2-8h12l2 8', 'M4 13v6h16v-6', 'M4 13h5l1 2h4l1-2h5'],
  despacho: ['M3 6h11v10H3z', 'M14 10h4l3 3v3h-7', 'M7 19a2 2 0 1 0 0-.01', 'M17 19a2 2 0 1 0 0-.01'],
  alertas: ['M6 16V11a6 6 0 0 1 12 0v5l2 2H4z', 'M10 21h4'],
  movimientos: ['M4 8h14', 'M15 5l3 3-3 3', 'M20 16H6', 'M9 13l-3 3 3 3'],
  usuarios: ['M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z', 'M3 20a6 6 0 0 1 12 0', 'M16 4.5a3.5 3.5 0 0 1 0 6.5', 'M18 14a6 6 0 0 1 3 6'],
  bitacora: ['M6 3h9l4 4v14H6z', 'M14 3v5h5', 'M9 12h7', 'M9 16h7'],
  grafica: ['M4 20V4', 'M4 20h16', 'M8 16v-4', 'M12 16V8', 'M16 16v-6'],
  reportes: ['M6 3h9l4 4v14H6z', 'M14 3v5h5', 'M9 17v-3', 'M12 17v-5', 'M15 17v-2'],
} satisfies Record<string, string[]>;

export type Icono = keyof typeof ICONOS;
