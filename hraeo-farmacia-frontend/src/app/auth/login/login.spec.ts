import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Login } from './login';
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
});
