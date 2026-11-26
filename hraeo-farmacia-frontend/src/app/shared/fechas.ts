/** Día local AAAA-MM-DD. Nunca toISOString(): da la fecha de Greenwich y después de las 6 pm en México ya es "mañana". */
export const diaLocal = (fecha = new Date()): string => fecha.toLocaleDateString('en-CA');
