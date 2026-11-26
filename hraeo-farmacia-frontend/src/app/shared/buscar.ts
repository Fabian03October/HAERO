/** Sin acentos y en mayúsculas, separado en palabras. */
function palabras(texto: string | null | undefined): string[] {
  return (texto ?? '').normalize('NFD').replace(/\p{M}/gu, '').toUpperCase().split(/\s+/).filter(Boolean);
}

/**
 * Igual que en el backend (NombrePaciente.contiene): "jimenez fabian" encuentra "FABIAN JIMENEZ CASTILLEJOS" en cualquier
 * orden y sin acentos. También encuentra al paciente guardado con menos palabras ("FABIAN JIMENEZ" para
 * "fabian jimenez castillejos") si coinciden al menos dos. `texto` es todo lo que se puede buscar (cama, expediente…).
 */
export function coincide(texto: string, buscado: string, nombre?: string | null): boolean {
  const busca = palabras(buscado);
  if (!busca.length) return true;
  const del = palabras(texto);
  if (busca.every((p) => del.some((d) => d.includes(p)))) return true;
  const delNombre = palabras(nombre);
  return delNombre.length >= 2 && delNombre.every((p) => busca.includes(p));
}
