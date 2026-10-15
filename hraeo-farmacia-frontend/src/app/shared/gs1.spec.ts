import { leerGs1 } from './gs1';

describe('leerGs1', () => {
  it('lee el formato con paréntesis (GTIN, caducidad y lote)', () => {
    expect(leerGs1('(01)07501234567894(17)280731(10)LAC-2028-B')).toEqual({
      gtin: '07501234567894',
      caducidad: '2028-07-31',
      lote: 'LAC-2028-B',
    });
  });

  it('lee el formato crudo con separador GS y prefijo de simbología', () => {
    expect(leerGs1(']d2010750123456789417270400' + '10A1B2C3\u001d21SERIE9')).toEqual({
      gtin: '07501234567894',
      caducidad: '2027-04-30',
      lote: 'A1B2C3',
      serie: 'SERIE9',
    });
  });

  it('lee el lote al final aunque el lector no mande el separador', () => {
    expect(leerGs1('01075012345678941729123110LOTE77')).toEqual({
      gtin: '07501234567894',
      caducidad: '2029-12-31',
      lote: 'LOTE77',
    });
  });

  it('lee el DataMatrix de una caja real: GTIN de 14, lote antes de la caducidad y número de serie', () => {
    expect(leerGs1('0117506022328332' + '10IIP23072\u001d' + '17250228' + '21QS1SQYBPE5NR')).toEqual({
      gtin: '17506022328332',
      lote: 'IIP23072',
      caducidad: '2025-02-28',
      serie: 'QS1SQYBPE5NR',
    });
  });

  it('separa el lote de la caducidad cuando el lector no manda el separador', () => {
    expect(leerGs1('0117506022328332' + '10IIP23072' + '17250228' + '21QS1SQYBPE5NR')).toEqual({
      gtin: '17506022328332',
      lote: 'IIP23072',
      caducidad: '2025-02-28',
      serie: 'QS1SQYBPE5NR',
    });
  });

  it('no corta un lote que solo contiene "17" seguido de algo que no es fecha', () => {
    expect(leerGs1('0107501234567894' + '10AB179999ZZ')?.lote).toBe('AB179999ZZ');
  });

  it('día 00 en la caducidad es el último día del mes', () => {
    expect(leerGs1('(01)07501234567894(17)270200(10)X')?.caducidad).toBe('2027-02-28');
  });

  it('un EAN-13 normal no es GS1', () => {
    expect(leerGs1('7501234567894')).toBeNull();
  });

  it('un texto sin GTIN no es GS1', () => {
    expect(leerGs1('010.000.6059.01-1')).toBeNull();
    expect(leerGs1('')).toBeNull();
  });
});
