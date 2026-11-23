import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { sesionInterceptor } from './sesion-interceptor';
import { Session } from './session';

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
        { provide: Router, useValue: { navigateByUrl: navegar, url: '/almacen/existencias' } },
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

  const responder = (metodo: 'GET' | 'POST', status: number, headers: Record<string, string> = {}) => {
    const cabeceras = { headers: { Authorization: 'Bearer t' } };
    (metodo === 'GET' ? http.get('/api/almacen/existencias', cabeceras) : http.post('/api/almacen/existencias', {}, cabeceras)).subscribe({
      error: () => {},
    });
    const peticion = backend.expectOne('/api/almacen/existencias');
    if (status === 0) peticion.error(new ProgressEvent('error'));
    else peticion.flush({ mensaje: 'x' }, { status, statusText: 'x', headers });
  };

  it('401: sesión vencida, manda al login con el motivo', () => {
    responder('GET', 401, { 'X-Auth-Error': 'inactividad' });
    expect(navegar).toHaveBeenCalledWith('/login?volver=%2Falmacen%2Fexistencias');
    expect(session.motivoSalida()).toContain('inactividad');
  });

  it('403 sin pendiente: no cierra la sesión ni cambia de pantalla', () => {
    responder('POST', 403);
    expect(navegar).not.toHaveBeenCalled();
    expect(session.usuarioActual()).not.toBeNull();
  });

  it('sin conexión al consultar: página de sin conexión con la pantalla para reintentar', () => {
    responder('GET', 0);
    expect(navegar).toHaveBeenCalledWith('/sin-conexion?volver=%2Falmacen%2Fexistencias');
  });

  it('error 500 al consultar: página de error del sistema', () => {
    responder('GET', 500);
    expect(navegar).toHaveBeenCalledWith('/error-sistema?volver=%2Falmacen%2Fexistencias');
  });

  it('error al guardar o 503: se queda en la pantalla para no perder lo capturado', () => {
    responder('POST', 500);
    responder('GET', 503);
    expect(navegar).not.toHaveBeenCalled();
  });
});
