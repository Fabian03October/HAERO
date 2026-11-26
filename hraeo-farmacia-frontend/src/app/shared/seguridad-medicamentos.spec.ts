import { piezasDeLaCaja } from './seguridad-medicamentos';

describe('piezasDeLaCaja', () => {
  const med = (piezasPorCaja: number | null, presentacion: string, nombreGenerico = '') => ({ piezasPorCaja, presentacion, nombreGenerico });

  it('una caja de tabletas o ampolletas cuenta sus piezas', () => {
    expect(piezasDeLaCaja(med(10, 'Envase con 10 tabletas.'))).toBe(10);
    expect(piezasDeLaCaja(med(3, 'Envase con 3 ampolletas o frascos ámpula con 4 ml.'))).toBe(3);
    expect(piezasDeLaCaja(med(30, '', 'Dexametasona. Tableta. Envase con 30 Tabletas.'))).toBe(30);
  });

  it('un frasco suelto o un líquido en ml cuenta 1', () => {
    expect(piezasDeLaCaja(med(1, 'Envase con un frasco ámpula.'))).toBe(1);
    expect(piezasDeLaCaja(med(120, 'Envase con frasco de 120 ml.'))).toBe(1);
    expect(piezasDeLaCaja(med(null, ''))).toBe(1);
  });
});
