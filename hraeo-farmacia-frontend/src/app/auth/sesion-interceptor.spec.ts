import { HttpRequest } from '@angular/common/http';
import { rolTieneAcceso } from './sesion-interceptor';

const peticion = (metodo: 'GET' | 'POST', url: string) => new HttpRequest(metodo, url, null);

describe('rolTieneAcceso (mismas reglas que SecurityConfig)', () => {
  it('Almacén puede todo /api/almacen', () => {
    expect(rolTieneAcceso('ALMACEN', peticion('POST', 'http://localhost:8080/api/almacen/entradas'))).toBe(true);
    expect(rolTieneAcceso('ALMACEN', peticion('GET', '/api/almacen/existencias'))).toBe(true);
  });

  it('Supervisión solo consulta', () => {
    expect(rolTieneAcceso('SUPERVISION', peticion('GET', '/api/almacen/alertas'))).toBe(true);
    expect(rolTieneAcceso('SUPERVISION', peticion('POST', '/api/almacen/canjes'))).toBe(false);
  });

  it('Farmacia consulta el catálogo pero no el resto de Almacén', () => {
    expect(rolTieneAcceso('FARMACIA', peticion('GET', '/api/almacen/medicamentos'))).toBe(true);
    expect(rolTieneAcceso('FARMACIA', peticion('GET', '/api/almacen/existencias'))).toBe(false);
    expect(rolTieneAcceso('FARMACIA', peticion('POST', '/api/farmacia/solicitudes'))).toBe(true);
  });

  it('usuarios solo Admin, salvo /me', () => {
    expect(rolTieneAcceso('ALMACEN', peticion('GET', '/api/usuarios'))).toBe(false);
    expect(rolTieneAcceso('ADMIN', peticion('GET', '/api/usuarios'))).toBe(true);
    expect(rolTieneAcceso('FARMACIA', peticion('POST', '/api/usuarios/me/contrasena'))).toBe(true);
  });
});
