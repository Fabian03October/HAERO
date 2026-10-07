import { Notificaciones } from './notificaciones';

describe('Notificaciones', () => {
  it('el aviso de éxito se cierra solo', () => {
    vi.useFakeTimers();
    const notificaciones = new Notificaciones();

    notificaciones.exito('Solicitud enviada', 'SOL-0005');
    expect(notificaciones.aviso()).toEqual({ tipo: 'exito', titulo: 'Solicitud enviada', mensaje: 'SOL-0005' });

    vi.advanceTimersByTime(4000);
    expect(notificaciones.aviso()).toBeNull();
    vi.useRealTimers();
  });

  it('el aviso de error espera a que el usuario lo cierre', () => {
    vi.useFakeTimers();
    const notificaciones = new Notificaciones();

    notificaciones.error('No se pudo guardar');
    vi.advanceTimersByTime(10000);
    expect(notificaciones.aviso()?.tipo).toBe('error');

    notificaciones.cerrarAviso();
    expect(notificaciones.aviso()).toBeNull();
    vi.useRealTimers();
  });

  it('confirmar responde lo que elige el usuario', async () => {
    const notificaciones = new Notificaciones();

    const aceptada = notificaciones.confirmar({ titulo: '¿Seguro?', mensaje: 'Se desactivará el usuario' });
    notificaciones.responder(true);
    expect(await aceptada).toBe(true);

    const cancelada = notificaciones.confirmar({ titulo: '¿Seguro?', mensaje: 'Otra vez' });
    notificaciones.responder(false);
    expect(await cancelada).toBe(false);
    expect(notificaciones.confirmacion()).toBeNull();
  });

  it('una confirmación nueva cancela la que seguía abierta', async () => {
    const notificaciones = new Notificaciones();

    const primera = notificaciones.confirmar({ titulo: 'Primera', mensaje: '' });
    notificaciones.confirmar({ titulo: 'Segunda', mensaje: '' });

    expect(await primera).toBe(false);
    expect(notificaciones.confirmacion()?.titulo).toBe('Segunda');
  });
});
