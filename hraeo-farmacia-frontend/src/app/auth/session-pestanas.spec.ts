import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { urlLogin } from './sesion-interceptor';
import { Session, TOKEN_STORAGE_KEY } from './session';

describe('Session: pestañas e inactividad', () => {
  let session: Session;
  const usuario = {
    nombreCompleto: 'Ana Torres', nombreUsuario: 'atorres', rol: 'FARMACIA' as const,
    debeCambiarContrasena: false, versionPoliticasAceptada: '1.0', fechaAceptacionPoliticas: null,
  };

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('hraeo.session', JSON.stringify(usuario));
    localStorage.setItem(TOKEN_STORAGE_KEY, 'token-a');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    session = TestBed.inject(Session);
  });

  it('si otra pestaña cierra la sesión, aquí también se cierra con el motivo', () => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    session.sincronizarConOtraPestana();
    expect(session.usuarioActual()).toBeNull();
    expect(session.motivoSalida()).toContain('otra pestaña');
  });

  it('si otra pestaña entra con otro token, se toma el nuevo', () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'token-b');
    session.sincronizarConOtraPestana();
    expect(session.obtenerToken()).toBe('token-b');
  });

  it('la actividad se comparte y se mide en milisegundos', () => {
    localStorage.setItem('hraeo.actividad', String(Date.now() - 60_000));
    expect(session.inactivoDesde()).toBeGreaterThanOrEqual(60_000);
    session.marcarActividad();
    expect(session.inactivoDesde()).toBeLessThan(1000);
  });

  it('el login regresa a la pantalla donde estaba, nunca al login mismo', () => {
    expect(urlLogin('/farmacia/trazabilidad')).toBe('/login?volver=%2Ffarmacia%2Ftrazabilidad');
    expect(urlLogin('/login?volver=x')).toBe('/login');
    expect(urlLogin(undefined)).toBe('/login');
  });
});
