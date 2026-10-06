import * as XLSX from 'xlsx';
import { CrearMedicamentoRequest } from '../../almacen-api';

// Carga masiva del catálogo de medicamentos desde Excel/CSV. Pensada para el
// catálogo completo (cientos de claves), por ejemplo exportado de Intelisis, o
// para la hoja de inventario del Drive, que solo trae CLAVE y DESCRIPCION.

export interface RenglonCatalogo extends CrearMedicamentoRequest {
  // Identificador del renglón en la vista previa (no cambia al editarlo).
  id: number;
  error?: string;
  // La clave ya está en el catálogo: no se vuelve a dar de alta.
  existente?: boolean;
  // Resultado al guardar.
  estado?: 'guardado' | 'fallo';
}

type Campo = 'clave' | 'nombreGenerico' | 'presentacion' | 'piezasPorCaja' | 'descripcion';

// Encabezados reconocidos (normalizados: minúsculas, sin acentos ni puntos).
const ENCABEZADOS: Record<Campo, string[]> = {
  clave: ['clave', 'clave material', 'material', 'codigo', 'codigo material', 'clave cuadro basico'],
  nombreGenerico: ['nombre generico', 'nombre', 'generico', 'medicamento', 'principio activo'],
  presentacion: ['presentacion', 'forma farmaceutica', 'forma'],
  piezasPorCaja: ['piezas por caja', 'piezas', 'contenido', 'unidades por caja', 'pzas por caja'],
  descripcion: ['descripcion', 'descripcion del medicamento', 'descripcion larga'],
};

export const ENCABEZADOS_PLANTILLA = ['CLAVE', 'NOMBRE GENERICO', 'PRESENTACION', 'PIEZAS POR CAJA', 'DESCRIPCION'];

/** Lee la primera hoja de un Excel (o un CSV) como filas de celdas. */
export function leerFilas(contenido: ArrayBuffer | string): unknown[][] {
  if (typeof contenido === 'string') {
    return contenido
      .split(/\r?\n/)
      .filter((linea) => linea.trim())
      .map((linea) => linea.split(',').map((valor) => valor.trim()));
  }
  const libro = XLSX.read(new Uint8Array(contenido), { type: 'array' });
  const hoja = libro.Sheets[libro.SheetNames[0]];
  return XLSX.utils.sheet_to_json<unknown[]>(hoja, { header: 1, blankrows: false, defval: '' }) as unknown[][];
}

/**
 * Convierte las filas en renglones del catálogo. Devuelve un error si faltan
 * columnas. Si la hoja no trae nombre o presentación pero sí descripción (hoja
 * del Drive), los toma de ella: "Lactulosa. Jarabe. Cada 100 ml…" → Lactulosa / Jarabe.
 */
export function interpretarFilas(filas: unknown[][], clavesCatalogo: string[]): { renglones: RenglonCatalogo[] } | { error: string } {
  const datos = filas.filter((fila) => fila.some((valor) => String(valor ?? '').trim()));
  if (datos.length < 2) return { error: 'El archivo debe contener encabezados y al menos un renglón.' };

  const encabezados = datos[0].map((valor) => normalizar(valor).replace(/[.:]/g, '').replace(/\s+/g, ' ').trim());
  const indice = (campo: Campo) => encabezados.findIndex((cabecera) => ENCABEZADOS[campo].includes(cabecera));
  const indices = {
    clave: indice('clave'),
    nombreGenerico: indice('nombreGenerico'),
    presentacion: indice('presentacion'),
    piezasPorCaja: indice('piezasPorCaja'),
    descripcion: indice('descripcion'),
  };
  if (indices.clave < 0) return { error: 'Al archivo le falta la columna CLAVE.' };
  if ((indices.nombreGenerico < 0 || indices.presentacion < 0) && indices.descripcion < 0) {
    return { error: 'El archivo necesita NOMBRE GENERICO y PRESENTACION, o al menos DESCRIPCION para obtenerlos de ella.' };
  }

  const existentes = new Set(clavesCatalogo);
  const vistas = new Set<string>();
  const renglones = datos.slice(1).map((fila, posicion) => {
    const leer = (campo: Campo) => (indices[campo] < 0 ? '' : String(fila[indices[campo]] ?? '').trim());
    const descripcion = leer('descripcion');
    const deDescripcion = partirDescripcion(descripcion);
    const piezas = leer('piezasPorCaja').replace(/[\s,]/g, '');
    const renglon: RenglonCatalogo = {
      id: posicion + 1,
      clave: leer('clave'),
      nombreGenerico: leer('nombreGenerico') || deDescripcion.nombre,
      presentacion: leer('presentacion') || deDescripcion.presentacion,
      piezasPorCaja: piezas ? Number(piezas) : null,
      descripcion: descripcion || null,
    };
    validarRenglon(renglon, existentes, vistas);
    if (renglon.clave) vistas.add(renglon.clave);
    return renglon;
  });
  return { renglones };
}

/** Vuelve a validar todos los renglones (después de corregir uno o de quitarlo). */
export function revalidar(renglones: RenglonCatalogo[], clavesCatalogo: string[]): RenglonCatalogo[] {
  const existentes = new Set(clavesCatalogo);
  const vistas = new Set<string>();
  return renglones.map((original) => {
    const renglon = { ...original, error: undefined, existente: false };
    if (renglon.estado === 'guardado') return renglon;
    validarRenglon(renglon, existentes, vistas);
    if (renglon.clave) vistas.add(renglon.clave);
    return renglon;
  });
}

function validarRenglon(renglon: RenglonCatalogo, existentes: Set<string>, vistas: Set<string>): void {
  const faltan = [!renglon.clave && 'clave', !renglon.nombreGenerico && 'nombre genérico', !renglon.presentacion && 'presentación'].filter(Boolean);
  if (faltan.length) renglon.error = `Falta ${faltan.join(', ')}`;
  else if (renglon.clave.length > 30) renglon.error = 'La clave tiene más de 30 caracteres';
  else if (renglon.nombreGenerico.length > 150) renglon.error = 'El nombre tiene más de 150 caracteres';
  else if (renglon.presentacion.length > 80) renglon.error = 'La presentación tiene más de 80 caracteres';
  else if (renglon.piezasPorCaja !== null && renglon.piezasPorCaja !== undefined && (!Number.isInteger(renglon.piezasPorCaja) || renglon.piezasPorCaja <= 0)) {
    renglon.error = 'Piezas por caja no válidas';
  } else if (vistas.has(renglon.clave)) renglon.error = 'Clave repetida en el archivo';
  else if (existentes.has(renglon.clave)) renglon.existente = true;
  // La descripción se recorta al tamaño de la columna en el backend (200).
  if (renglon.descripcion && renglon.descripcion.length > 200) renglon.descripcion = renglon.descripcion.slice(0, 200);
}

/** "Lactulosa. Jarabe. Cada 100 ml contienen…" → { nombre: "Lactulosa", presentacion: "Jarabe" }. */
export function partirDescripcion(descripcion: string): { nombre: string; presentacion: string } {
  const partes = descripcion
    .split(/[.,]?\s+cada\s/i)[0]
    .split('.')
    .map((parte) => parte.trim())
    .filter(Boolean);
  return { nombre: (partes[0] ?? '').slice(0, 150), presentacion: (partes[1] ?? '').slice(0, 80) };
}

export function descargarPlantilla(): void {
  const hoja = XLSX.utils.aoa_to_sheet([
    ENCABEZADOS_PLANTILLA,
    ['010.000.6059.01-1', 'Lactulosa', 'Jarabe 240 ml', 20, 'Cada 100 ml contienen: Lactulosa 66.7 g'],
  ]);
  hoja['!cols'] = [{ wch: 20 }, { wch: 28 }, { wch: 28 }, { wch: 16 }, { wch: 48 }];
  const libro = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(libro, hoja, 'Catalogo');
  XLSX.writeFile(libro, 'plantilla-catalogo-medicamentos.xlsx');
}

function normalizar(valor: unknown): string {
  return String(valor ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase();
}
