import { coincide } from './buscar';

describe('coincide', () => {
  it('encuentra al paciente en cualquier orden, sin acentos y con menos palabras', () => {
    const q = 'fabian jimenez castillejos';
    expect(coincide('JIMENEZ CASTILLEJOS FABIAN', q, 'JIMENEZ CASTILLEJOS FABIAN')).toBe(true);
    expect(coincide('CAMA 12 JIMÉNEZ FABIAN 1234', q, 'JIMÉNEZ FABIAN')).toBe(true);
    expect(coincide('CAMA 12 FABIAN 1234', q, 'FABIAN')).toBe(false);
    expect(coincide('CAMA 12 FABIAN LOPEZ', q, 'FABIAN LOPEZ')).toBe(false);
    expect(coincide('CAMA 12 FABIAN JIMENEZ CASTILLEJOS', 'cama 12')).toBe(true);
  });
});
