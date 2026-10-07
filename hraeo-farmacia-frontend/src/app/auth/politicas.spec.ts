import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PoliticasUso, VERSION_POLITICAS } from './politicas';
import { Session, UsuarioSesion } from './session';

const usuario = (version: string | null): UsuarioSesion => ({
  nombreCompleto: 'Rubí Morales',
  nombreUsuario: 'rubi',
  rol: 'FARMACIA',
  debeCambiarContrasena: false,
  versionPoliticasAceptada: version,
  fechaAceptacionPoliticas: version ? '2026-10-07T09:00:00' : null,
});

describe('PoliticasUso (aceptación guardada en el servidor)', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  });

  it('pide aceptar si el usuario nunca aceptó o aceptó otra versión', () => {
    const politicas = TestBed.inject(PoliticasUso);
    expect(politicas.debeAceptar(usuario(null))).toBe(true);
    expect(politicas.debeAceptar(usuario('0.9'))).toBe(true);
    expect(politicas.debeAceptar(usuario(VERSION_POLITICAS))).toBe(false);
  });

  it('la aceptación vigente sale de los datos del servidor', () => {
    const politicas = TestBed.inject(PoliticasUso);
    expect(politicas.aceptacionVigente(usuario(VERSION_POLITICAS))).toEqual({ version: VERSION_POLITICAS, fecha: '2026-10-07T09:00:00' });
    expect(politicas.aceptacionVigente(usuario('0.9'))).toBeUndefined();
  });

  it('aceptar la registra en el servidor y actualiza la sesión', async () => {
    const session = TestBed.inject(Session);
    const http = TestBed.inject(HttpTestingController);
    const iniciar = session.iniciarSesion('rubi', 'clave');
    http.expectOne((req) => req.url.endsWith('/auth/login')).flush({
      token: 'token', rol: 'FARMACIA', debeCambiarContrasena: false, nombreCompleto: 'Rubí Morales',
      versionPoliticasAceptada: null, fechaAceptacionPoliticas: null,
    });
    await iniciar;

    const aceptar = TestBed.inject(PoliticasUso).aceptar();
    const peticion = http.expectOne((req) => req.url.endsWith('/usuarios/me/politicas'));
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ version: VERSION_POLITICAS });
    peticion.flush({ version: VERSION_POLITICAS, fecha: '2026-10-07T15:00:00' });

    expect(await aceptar).toBeNull();
    expect(session.usuarioActual()?.versionPoliticasAceptada).toBe(VERSION_POLITICAS);
    http.verify();
  });

  it('el login guarda el motivo del rechazo, como la cuenta bloqueada', async () => {
    const session = TestBed.inject(Session);
    const http = TestBed.inject(HttpTestingController);
    const iniciar = session.iniciarSesion('rubi', 'mala');
    http.expectOne((req) => req.url.endsWith('/auth/login')).flush(
      { mensaje: 'Cuenta bloqueada por varios intentos fallidos. Intenta de nuevo en 10 minutos' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(await iniciar).toBeNull();
    expect(session.errorInicio()).toBe('Cuenta bloqueada por varios intentos fallidos. Intenta de nuevo en 10 minutos.');
  });
});
