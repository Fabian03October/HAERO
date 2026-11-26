import { leerCaducidad } from './almacen-api';

describe('leerCaducidad (caducidad escrita a mano)', () => {
  it('acepta la fecha con día', () => {
    expect(leerCaducidad('31/12/2027')).toBe('2027-12-31');
    expect(leerCaducidad('5/3/2028')).toBe('2028-03-05');
    expect(leerCaducidad('2027-12-31')).toBe('2027-12-31');
    expect(leerCaducidad('15.06.27')).toBe('2027-06-15');
  });

  it('sin día toma el último día del mes', () => {
    expect(leerCaducidad('12/2027')).toBe('2027-12-31');
    expect(leerCaducidad('02/2028')).toBe('2028-02-29');
    expect(leerCaducidad('2027-04')).toBe('2027-04-30');
    expect(leerCaducidad(' 6-27 ')).toBe('2027-06-30');
  });

  it('rechaza fechas que no existen o no se entienden', () => {
    expect(leerCaducidad('31/02/2027')).toBeNull();
    expect(leerCaducidad('13/2027')).toBeNull();
    expect(leerCaducidad('diciembre 2027')).toBeNull();
    expect(leerCaducidad('')).toBeNull();
  });

  it('acepta la fecha escrita solo con números (el teclado numérico del celular no trae diagonal)', () => {
    expect(leerCaducidad('31122027')).toBe('2027-12-31');
    expect(leerCaducidad('122027')).toBe('2027-12-31');
    expect(leerCaducidad('311227')).toBe('2027-12-31');
    expect(leerCaducidad('0228')).toBe('2028-02-29');
    expect(leerCaducidad('32122027')).toBeNull();
  });
});
