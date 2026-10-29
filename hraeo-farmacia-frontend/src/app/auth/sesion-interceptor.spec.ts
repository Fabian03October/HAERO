import { HttpClient, HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { rolTieneAcceso, sesionInterceptor } from './sesion-interceptor';
import { Session } from './session';

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

describe('sesionInterceptor: pendientes que exige el servidor (HU08 CA4)', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: Session;
  const navegar = vi.fn();

  beforeEach(() => {
    localStorage.clear();
    navegar.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sesionInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl: navegar } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(Session);
    session.usuarioActual.set({
      nombreCompleto: 'Ana Torres',
      nombreUsuario: 'atorres',
      rol: 'ALMACEN',
      debeCambiarContrasena: false,
      versionPoliticasAceptada: '1.0',
      fechaAceptacionPoliticas: '2026-10-01T09:00:00',
    });
  });

  const responder403 = (pendiente: string) => {
    http.get('/api/almacen/existencias', { headers: { Authorization: 'Bearer t' } }).subscribe({ error: () => {} });
    backend.expectOne('/api/almacen/existencias').flush(
      { mensaje: 'pendiente' },
      { status: 403, statusText: 'Forbidden', headers: { 'X-Auth-Pendiente': pendiente } },
    );
  };

  it('contraseña temporal: manda a cambiarla sin cerrar la sesión', () => {
    responder403('cambiar-contrasena');
    expect(navegar).toHaveBeenCalledWith('/cambiar-contrasena');
    expect(session.usuarioActual()?.debeCambiarContrasena).toBe(true);
    expect(session.motivoSalida()).toBe('');
  });

  it('políticas sin aceptar: manda a aceptarlas', () => {
    responder403('aceptar-politicas');
    expect(navegar).toHaveBeenCalledWith('/politicas');
    expect(session.usuarioActual()?.versionPoliticasAceptada).toBeNull();
  });
});
