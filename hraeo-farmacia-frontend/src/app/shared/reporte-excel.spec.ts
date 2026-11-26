import ExcelJS from 'exceljs';
import { armarValePedido } from '../almacen/despacho/salidas-registradas/comprobante-salidas';
import { anotar, armarExcel } from './reporte-excel';

describe('reporte en Excel', () => {
  it('el vale en Excel trae lo mismo que el PDF, con sus colores', async () => {
    const doc = armarValePedido('PED-0027', {
      pedidoId: 27, solicitadoPor: 'Farmacia', fechaPedido: '2026-11-26T10:00:00',
      entregado: [{ clave: '010.000.0622.00', medicamento: 'HEPARINA SODICA', lote: 'A0980107 SCC', caducidad: '2027-06-30', ubicacion: 'A-2', cajas: 6, despacho: 'Almacén', fecha: '2026-11-26T10:05:00' }],
      noSurtido: [{ clave: '010.000.5541.00', medicamento: 'LETROZOL', pedidas: 5, surtidas: 0, faltan: 5, motivo: 'Sin existencia en Almacén', estatus: 'PENDIENTE' }],
    }, 'Almacén');
    const libro = new ExcelJS.Workbook();
    await libro.xlsx.load(await armarExcel(doc));
    const hoja = libro.getWorksheet('Reporte')!;
    const valores: string[] = [];
    let encabezadoGuinda = false, encabezadoDorado = false;
    hoja.eachRow((fila) => fila.eachCell((c) => {
      valores.push(String(c.value));
      const color = (c.fill as ExcelJS.FillPattern | undefined)?.fgColor?.argb;
      if (c.value === 'Lote' && color === 'FF611232') encabezadoGuinda = true;
      if (c.value === 'Motivo' && color === 'FFA57F2C') encabezadoDorado = true;
    }));
    expect(valores).toContain('A0980107 SCC');
    expect(valores).toContain('Sin existencia en Almacén');
    expect(valores).toContain('6');
    expect(encabezadoGuinda).toBe(true);
    expect(encabezadoDorado).toBe(true);
  });

  it('el encabezado que se repite en cada hoja del PDF va una sola vez', () => {
    const doc = {};
    anotar(doc, { tipo: 'titulo', titulo: 'Vale', folio: 'PED-1', subtitulo: '' });
    anotar(doc, { tipo: 'subtitulo', texto: 'Entregas' });
    anotar(doc, { tipo: 'titulo', titulo: 'Vale', folio: 'PED-1', subtitulo: '' });
    anotar(doc, { tipo: 'titulo', titulo: 'Vale', folio: 'PED-2', subtitulo: '' });
    expect((doc as { __excel: unknown[] }).__excel.length).toBe(3);
  });
});
