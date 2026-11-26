import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { destinoAlEntrar, Login } from './login';
import { Session } from '../session';

describe('Login', () => {
  it('al abrirse con una sesión activa (flecha de atrás) la cierra', () => {
    const session = { usuarioActual: () => ({ nombreUsuario: 'atorres' }), cerrarSesion: vi.fn(), errorInicio: () => '', motivoSalida: () => '' };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: Session, useValue: session }] });
    TestBed.runInInjectionContext(() => new Login());
    expect(session.cerrarSesion).toHaveBeenCalledTimes(1);
  });

  it('sin sesión no cierra nada', () => {
    const session = { usuarioActual: () => null, cerrarSesion: vi.fn(), errorInicio: () => '', motivoSalida: () => '' };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: Session, useValue: session }] });
    TestBed.runInInjectionContext(() => new Login());
    expect(session.cerrarSesion).not.toHaveBeenCalled();
  });

  it('solo regresa a la pantalla anterior si es del módulo de quien entra', () => {
    expect(destinoAlEntrar('ADMIN', '/farmacia/hospitalizacion')).toBe('/admin');
    expect(destinoAlEntrar('FARMACIA', '/farmacia/hospitalizacion')).toBe('/farmacia/hospitalizacion');
    expect(destinoAlEntrar('ADMIN', '/politicas')).toBe('/politicas');
    expect(destinoAlEntrar('ALMACEN', null)).toBe('/almacen');
  });
});
