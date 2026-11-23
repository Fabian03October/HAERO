import { queBuscar } from './trazabilidad';

describe('queBuscar (buscador único de trazabilidad)', () => {
  it('una caja escaneada se busca por su lote', () => {
    expect(queBuscar('(01)07501234567894(17)280731(10)LAC-2028-B')).toEqual({ tipo: 'LOTE', lote: 'LAC-2028-B' });
  });

  it('reconoce la CURP', () => {
    expect(queBuscar(' gaxl800101hdfrrn09 ')).toEqual({ tipo: 'CURP', valor: 'GAXL800101HDFRRN09' });
  });

  it('el texto libre marca si parece expediente', () => {
    expect(queBuscar('123456')).toEqual({ tipo: 'TEXTO', valor: '123456', pareceExpediente: true });
    expect(queBuscar('HRA-004512')).toEqual({ tipo: 'TEXTO', valor: 'HRA-004512', pareceExpediente: true });
    expect(queBuscar('paracetamol')).toEqual({ tipo: 'TEXTO', valor: 'paracetamol', pareceExpediente: false });
    expect(queBuscar('010.000.0104.00')).toEqual({ tipo: 'TEXTO', valor: '010.000.0104.00', pareceExpediente: false });
  });

  it('pide más letras si es muy corto', () => {
    expect(queBuscar('ab')).toEqual({ tipo: 'CORTO' });
  });
});
