import { TestBed } from '@angular/core/testing';
import { CantidadPiezas } from './cantidad-piezas';
import { SeguridadMedicamentos } from '../seguridad-medicamentos';

describe('CantidadPiezas', () => {
  const crear = () => {
    TestBed.configureTestingModule({
      providers: [{ provide: SeguridadMedicamentos, useValue: { cargar: () => undefined, piezasPorCaja: () => 20 } }],
    });
    return TestBed.runInInjectionContext(() => new CantidadPiezas());
  };

  it('en cajas guarda piezas: 2 cajas de 20 = 40', () => {
    const c = crear();
    c.cambiarUnidad('CAJA');
    c.cambiar(2);
    expect(c.piezas()).toBe(40);
    expect(c.mostrado()).toBe(2);
  });

  it('cambiar a cajas respeta el número escrito y volver a piezas también', () => {
    const c = crear();
    c.cambiar(3);
    expect(c.piezas()).toBe(3);
    c.cambiarUnidad('CAJA');
    expect(c.piezas()).toBe(60);
    c.cambiarUnidad('PIEZA');
    expect(c.piezas()).toBe(3);
  });
});
