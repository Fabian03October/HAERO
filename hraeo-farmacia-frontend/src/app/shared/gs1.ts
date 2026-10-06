// Lectura de códigos GS1 (GS1-128 y GS1 DataMatrix) de las cajas de medicamento.
// Además del producto, estos códigos traen el lote y la caducidad en
// "identificadores de aplicación" (AI):
//   (01) GTIN del producto, 14 dígitos
//   (17) caducidad AAMMDD (DD = 00 significa fin de mes)
//   (10) lote, longitud variable (hasta 20)
//   (21) número de serie, longitud variable
// Un EAN-13 normal solo trae el producto; en ese caso no se llena nada más.

export interface DatosGs1 {
  gtin?: string;
  lote?: string;
  // AAAA-MM-DD
  caducidad?: string;
  serie?: string;
}

// Separador de campos de longitud variable (FNC1 / Group Separator).
const GS = '\u001d';

// AI de longitud fija que pueden aparecer en cajas de medicamento: AI → dígitos de datos.
const FIJOS: Record<string, number> = {
  '00': 18, '01': 14, '02': 14, '11': 6, '12': 6, '13': 6, '15': 6, '16': 6, '17': 6, '20': 2,
};
// AI de longitud variable conocidos: AI → longitud máxima.
const VARIABLES: Record<string, number> = { '10': 20, '21': 20, '22': 20, '30': 8, '37': 8, '240': 30, '241': 30, '90': 30 };

/**
 * Interpreta el texto leído por la cámara o el lector. Acepta:
 * - formato legible con paréntesis: (01)07501234567894(17)280731(10)LOTE123
 * - formato crudo con separador GS y prefijo de simbología opcional (]C1, ]d2, ]Q3)
 * Devuelve null si el texto no es GS1 (por ejemplo, un EAN-13 normal).
 */
export function leerGs1(texto: string): DatosGs1 | null {
  const crudo = (texto ?? '').trim();
  if (!crudo) return null;
  const campos = crudo.startsWith('(') ? camposConParentesis(crudo) : camposCrudos(crudo);
  if (!campos || !campos.has('01')) return null;

  const datos: DatosGs1 = { gtin: campos.get('01') };
  const lote = campos.get('10');
  if (lote) datos.lote = lote;
  const caducidad = fechaGs1(campos.get('17'));
  if (caducidad) datos.caducidad = caducidad;
  const serie = campos.get('21');
  if (serie) datos.serie = serie;
  return datos;
}

function camposConParentesis(texto: string): Map<string, string> | null {
  const campos = new Map<string, string>();
  const patron = /\((\d{2,4})\)([^(]*)/g;
  let coincidencia: RegExpExecArray | null;
  while ((coincidencia = patron.exec(texto))) campos.set(coincidencia[1], coincidencia[2].trim());
  return campos.size ? campos : null;
}

function camposCrudos(texto: string): Map<string, string> | null {
  let resto = texto.replace(/^\][A-Za-z]\d/, '');
  if (resto.startsWith(GS)) resto = resto.slice(1);
  // Un GS1 crudo empieza con un AI conocido y es más largo que un EAN/UPC.
  if (!/^(00|01|02)\d/.test(resto) || /^\d{8,14}$/.test(resto)) return null;

  const campos = new Map<string, string>();
  while (resto.length) {
    if (resto.startsWith(GS)) {
      resto = resto.slice(1);
      continue;
    }
    const ai = [4, 3, 2].map((largo) => resto.slice(0, largo)).find((candidato) => candidato in FIJOS || candidato in VARIABLES);
    if (!ai) return campos.size ? campos : null;
    resto = resto.slice(ai.length);
    if (ai in FIJOS) {
      const valor = resto.slice(0, FIJOS[ai]);
      if (valor.length < FIJOS[ai] || !/^\d+$/.test(valor)) return campos.size ? campos : null;
      campos.set(ai, valor);
      resto = resto.slice(FIJOS[ai]);
    } else {
      // Longitud variable: termina en el separador GS, al llegar al máximo o al final del texto.
      const fin = resto.indexOf(GS);
      const largo = Math.min(fin < 0 ? resto.length : fin, VARIABLES[ai]);
      campos.set(ai, resto.slice(0, largo));
      resto = resto.slice(largo);
    }
  }
  return campos;
}

/** AAMMDD → AAAA-MM-DD. DD = 00 es el último día del mes (regla GS1). */
function fechaGs1(valor?: string): string | undefined {
  if (!valor || !/^\d{6}$/.test(valor)) return undefined;
  const anio = 2000 + Number(valor.slice(0, 2));
  const mes = Number(valor.slice(2, 4));
  let dia = Number(valor.slice(4, 6));
  if (mes < 1 || mes > 12) return undefined;
  const ultimo = new Date(anio, mes, 0).getDate();
  if (dia === 0) dia = ultimo;
  if (dia > ultimo) return undefined;
  return `${anio}-${String(mes).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
}
