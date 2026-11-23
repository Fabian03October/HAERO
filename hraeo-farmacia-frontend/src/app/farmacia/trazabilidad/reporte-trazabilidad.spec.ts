import { Trazabilidad } from '../farmacia-api';
import { armarReporteTrazabilidad, armarReporteVarios, conDatosHaero } from './reporte-trazabilidad';

const loteSoloIntelisis: Trazabilidad = {
  modo: 'LOTE',
  valor: '2L23198',
  aviso: null,
  recorrido: [
    { etapa: 'Pedido y contrato', fecha: '2025-06-18T00:00:00', detalle: 'Compra 3444 · contrato 6773', fuente: 'Intelisis' },
    { etapa: 'Despacho a Farmacia', fecha: null, detalle: 'Sin despachos de Almacén', fuente: null },
  ],
  compras: [
    { fecha: '2025-06-18', compra: '3444', origen: 'Pedido Proveedor 7434', contrato: 6773, proveedor: 'Proveedor', clave: '010.000.1768.02', cantidad: 36, caducidad: '2025-11-30', almacen: '25301' },
  ],
  existencias: [{ almacen: '25301', tipo: 'Farmacia', clave: '010.000.1768.02', cantidad: 4 }],
  entregas: [
    { fecha: '2025-11-11T00:00:00', folio: 'Consumo Farmacia 422905', paciente: 'PACIENTE DE PRUEBA', expediente: '85680 / 86747', clave: '010.000.1768.02', lote: '2L23198', piezas: 2, despachador: 'BSILVA', fuente: 'Intelisis' },
  ],
};

describe('armarReporteTrazabilidad', () => {
  it('arma el PDF de un lote que solo está en Intelisis', () => {
    const { doc, nombre } = armarReporteTrazabilidad(loteSoloIntelisis, 'Lote 2L23198 · 010.000.1768.02', 'Usuario de prueba');
    expect(nombre).toMatch(/^trazabilidad-2L23198-\d{4}-\d{2}-\d{2}\.pdf$/);
    expect(doc.getNumberOfPages()).toBe(1);
  });

  it('arma el PDF aunque no haya entregas, compras ni recorrido', () => {
    const { doc, nombre } = armarReporteTrazabilidad(
      { ...loteSoloIntelisis, modo: 'EXPEDIENTE', valor: '74525', recorrido: [], compras: [], existencias: [], entregas: [] }, 'Expediente 74525', '');
    expect(nombre).toContain('trazabilidad-74525');
    expect(doc.getNumberOfPages()).toBe(1);
  });

  it('con datos de HAERO lleva la línea de tiempo; solo con Intelisis, no', () => {
    const conHaero: Trazabilidad = {
      ...loteSoloIntelisis,
      recorrido: [...loteSoloIntelisis.recorrido, { etapa: 'Recepción en Farmacia', fecha: '2026-11-20T10:30:00', detalle: '3 cajas recibidas', fuente: 'HAERO' }],
    };
    expect(conDatosHaero(conHaero)).toBe(true);
    expect(conDatosHaero(loteSoloIntelisis)).toBe(false);
    expect(conDatosHaero({ ...conHaero, modo: 'EXPEDIENTE' })).toBe(false);
    expect(armarReporteTrazabilidad(conHaero, 'Lote 2L23198', '').doc.getNumberOfPages()).toBe(1);
  });

  it('varios lotes: hoja de resumen y una hoja por lote', () => {
    const otro: Trazabilidad = { ...loteSoloIntelisis, valor: 'F226C012' };
    const { doc, nombre } = armarReporteVarios(
      [{ resultado: loteSoloIntelisis, titulo: 'Lote 2L23198 · 010.000.1768.02' }, { resultado: otro, titulo: 'Lote F226C012 · 010.000.6325.00' }], 'Usuario');
    expect(nombre).toMatch(/^trazabilidad-2-consultas-/);
    expect(doc.getNumberOfPages()).toBe(3);
  });

  it('cientos de entregas continúan en varias hojas', () => {
    const muchas = Array.from({ length: 300 }, () => loteSoloIntelisis.entregas[0]);
    const { doc } = armarReporteTrazabilidad({ ...loteSoloIntelisis, entregas: muchas }, 'Lote 2L23198', '');
    expect(doc.getNumberOfPages()).toBeGreaterThan(3);
  });
});
