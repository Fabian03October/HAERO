// Sonidos cortos para el escaneo continuo, hechos con el audio del navegador (sin archivos):
// "ok" = pip agudo, "completo" = dos pips subiendo, "error" = tono grave más largo.
// Si el navegador no deja reproducir audio, no pasa nada.

let contexto: AudioContext | null = null;

function tono(frecuencia: number, inicio: number, duracion: number, tipo: OscillatorType = 'sine'): void {
  if (!contexto) return;
  const oscilador = contexto.createOscillator();
  const volumen = contexto.createGain();
  oscilador.type = tipo;
  oscilador.frequency.value = frecuencia;
  const t = contexto.currentTime + inicio;
  volumen.gain.setValueAtTime(0.0001, t);
  volumen.gain.exponentialRampToValueAtTime(0.25, t + 0.01);
  volumen.gain.exponentialRampToValueAtTime(0.0001, t + duracion);
  oscilador.connect(volumen).connect(contexto.destination);
  oscilador.start(t);
  oscilador.stop(t + duracion + 0.02);
}

export function pitido(tipo: 'ok' | 'completo' | 'error'): void {
  try {
    contexto ??= new AudioContext();
    if (contexto.state === 'suspended') void contexto.resume();
    if (tipo === 'ok') tono(1320, 0, 0.09);
    else if (tipo === 'completo') {
      tono(1320, 0, 0.08);
      tono(1760, 0.1, 0.14);
    } else tono(220, 0, 0.35, 'square');
  } catch {
    // Sin audio: el color y el mensaje siguen avisando.
  }
}
