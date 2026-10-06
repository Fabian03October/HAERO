import { interpretarFilas, partirDescripcion, revalidar } from './carga-catalogo';

describe('carga del catálogo desde Excel', () => {
  it('toma nombre y presentación de la descripción del Drive', () => {
    expect(partirDescripcion('Lactulosa. Jarabe. Cada 100 ml contienen: Lactulosa')).toEqual({ nombre: 'Lactulosa', presentacion: 'Jarabe' });
    expect(partirDescripcion('TOFACITINIB TABLETA CADA TABLETA CONTIENE: TOFACITINIB')).toEqual({ nombre: 'TOFACITINIB TABLETA', presentacion: '' });
  });

  it('lee la plantilla, omite las claves existentes y marca repetidas y faltantes', () => {
    const resultado = interpretarFilas(
      [
        ['CLAVE', 'NOMBRE GENERICO', 'PRESENTACIÓN', 'PIEZAS POR CAJA'],
        ['010.000.0104.00-1', 'Paracetamol', 'Tableta 500 mg', 10],
        ['010.000.6059.01-1', 'Lactulosa', 'Jarabe', ''],
        ['010.000.0104.00-1', 'Paracetamol', 'Tableta 500 mg', 10],
        ['010.000.1042.00-1', 'Metformina', '', 30],
      ],
      ['010.000.6059.01-1'],
    );
    if ('error' in resultado) throw new Error(resultado.error);
    const [nuevo, existente, repetido, incompleto] = resultado.renglones;
    expect(nuevo.error).toBeUndefined();
    expect(nuevo.existente).toBeFalsy();
    expect(existente.existente).toBe(true);
    expect(repetido.error).toBe('Clave repetida en el archivo');
    expect(incompleto.error).toBe('Falta presentación');
  });

  it('acepta la hoja del Drive (solo clave y descripción)', () => {
    const resultado = interpretarFilas([['CLAVE', 'DESCRIPCION', 'LOTE'], ['010.000.6059.01-1', 'Lactulosa. Jarabe. Cada 100 ml', 'L1']], []);
    if ('error' in resultado) throw new Error(resultado.error);
    expect(resultado.renglones[0]).toMatchObject({ clave: '010.000.6059.01-1', nombreGenerico: 'Lactulosa', presentacion: 'Jarabe' });
  });

  it('sin columna de clave avisa el error', () => {
    expect(interpretarFilas([['NOMBRE'], ['x']], [])).toEqual({ error: 'Al archivo le falta la columna CLAVE.' });
  });

  it('al corregir la presentación el renglón queda correcto', () => {
    const resultado = interpretarFilas([['CLAVE', 'NOMBRE GENERICO', 'PRESENTACION'], ['A1', 'Metformina', '']], []);
    if ('error' in resultado) throw new Error(resultado.error);
    const corregido = revalidar([{ ...resultado.renglones[0], presentacion: 'Tableta 850 mg' }], []);
    expect(corregido[0].error).toBeUndefined();
  });
});
