import { TAMANO_MAXIMO_PDF, validarPdf } from './movimientos-especiales-almacen';

const archivo = (nombre: string, tipo: string, tamano = 1000) => {
  const pdf = new File(['%PDF-1.4'], nombre, { type: tipo });
  Object.defineProperty(pdf, 'size', { value: tamano });
  return pdf;
};

describe('validarPdf (documentos de préstamos y transferencias)', () => {
  it('pide el documento cuando no se adjuntó', () => {
    expect(validarPdf(null, 'el oficio de solicitud')).toBe('Adjunta el oficio de solicitud en PDF.');
  });

  it('acepta un PDF por su tipo o por su extensión', () => {
    expect(validarPdf(archivo('oficio.pdf', 'application/pdf'), 'x')).toBeNull();
    expect(validarPdf(archivo('ESCANEO.PDF', ''), 'x')).toBeNull();
  });

  it('rechaza otros archivos', () => {
    expect(validarPdf(archivo('foto.jpg', 'image/jpeg'), 'x')).toBe('El archivo debe ser un PDF.');
  });

  it('rechaza un PDF de más de 10 MB', () => {
    expect(validarPdf(archivo('grande.pdf', 'application/pdf', TAMANO_MAXIMO_PDF + 1), 'x')).toBe('El PDF no puede pesar más de 10 MB.');
  });
});
