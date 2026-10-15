import { Component, ElementRef, OnDestroy, afterNextRender, output, signal, viewChild } from '@angular/core';
import { BrowserMultiFormatReader, IScannerControls } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';

// Formatos de las cajas de medicamento (EAN/UPC y los de logística) y QR por si el proveedor lo trae.
const FORMATOS = [
  BarcodeFormat.EAN_13,
  BarcodeFormat.EAN_8,
  BarcodeFormat.UPC_A,
  BarcodeFormat.UPC_E,
  BarcodeFormat.CODE_128,
  BarcodeFormat.CODE_39,
  BarcodeFormat.ITF,
  BarcodeFormat.DATA_MATRIX,
  BarcodeFormat.QR_CODE,
];

/**
 * Lee un código de barras con la cámara del dispositivo (pensado para el teléfono
 * mientras no haya lector físico). Emite `leido` con el primer código que reconoce
 * y apaga la cámara. El navegador solo permite la cámara en HTTPS o en localhost.
 */
@Component({
  selector: 'app-escaner-camara',
  templateUrl: './escaner-camara.html',
  styleUrl: './escaner-camara.css',
})
export class EscanerCamara implements OnDestroy {
  readonly leido = output<string>();
  readonly cerrar = output<void>();

  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private controles: IScannerControls | null = null;

  readonly estado = signal<'iniciando' | 'leyendo' | 'error'>('iniciando');
  readonly error = signal('');
  readonly leyendoFoto = signal(false);
  readonly avisoFoto = signal('');

  private readonly lector = new BrowserMultiFormatReader(
    new Map<DecodeHintType, unknown>([
      [DecodeHintType.POSSIBLE_FORMATS, FORMATOS],
      [DecodeHintType.TRY_HARDER, true],
    ]),
    { delayBetweenScanAttempts: 150 },
  );

  constructor() {
    afterNextRender(() => this.iniciar());
  }

  private async iniciar(): Promise<void> {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      this.fallar('El navegador solo permite usar la cámara en una conexión segura. Abre la página con https:// (npm run start:celular).');
      return;
    }
    try {
      // Cámara trasera cuando existe (teléfono); en una laptop usa la que haya. Sin pedir
      // resolución el navegador da 640x480 y un DataMatrix pequeño (GS1) no se alcanza a leer.
      this.controles = await this.lector.decodeFromConstraints(
        { video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } } },
        this.video().nativeElement,
        (resultado) => {
          if (!resultado) return;
          this.emitir(resultado.getText());
        },
      );
      this.estado.set('leyendo');
      await this.enfocar();
    } catch (error) {
      const nombre = (error as { name?: string })?.name;
      if (nombre === 'NotAllowedError') this.fallar('No se dio permiso para usar la cámara. Actívalo en la configuración del navegador y vuelve a intentar.');
      else if (nombre === 'NotFoundError' || nombre === 'OverconstrainedError') this.fallar('No se encontró una cámara en este dispositivo.');
      else if (nombre === 'NotReadableError') this.fallar('La cámara está ocupada por otra aplicación. Ciérrala y vuelve a intentar.');
      else this.fallar('No se pudo abrir la cámara.');
    }
  }

  /**
   * Enfoque continuo y un poco de zoom cuando la cámara lo permite (Chrome en Android
   * sí; Safari en iPhone a veces no, y entonces no pasa nada). Con zoom la caja se
   * puede tener a 15-20 cm, donde la cámara sí enfoca, y el código se ve grande.
   */
  private async enfocar(): Promise<void> {
    const pista = (this.video().nativeElement.srcObject as MediaStream | null)?.getVideoTracks()[0];
    if (!pista?.getCapabilities) return;
    const capacidades = pista.getCapabilities() as MediaTrackCapabilities & {
      focusMode?: string[];
      zoom?: { min: number; max: number };
    };
    const ajustes: Record<string, unknown> = {};
    if (capacidades.focusMode?.includes('continuous')) ajustes['focusMode'] = 'continuous';
    if (capacidades.zoom && capacidades.zoom.max >= 2) ajustes['zoom'] = 2;
    if (!Object.keys(ajustes).length) return;
    try {
      await pista.applyConstraints({ advanced: [ajustes as MediaTrackConstraintSet] });
    } catch {
      // Si la cámara no acepta el ajuste se sigue leyendo igual.
    }
  }

  /**
   * Plan B para códigos pequeños o borrosos: se toma una foto con la cámara del
   * teléfono (enfoca sola y con mucha más resolución) y se lee el código de la foto.
   */
  async leerFoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo) return;
    this.leyendoFoto.set(true);
    this.avisoFoto.set('');
    const url = URL.createObjectURL(archivo);
    try {
      const resultado = await this.lector.decodeFromImageUrl(url);
      this.emitir(resultado.getText());
    } catch {
      this.avisoFoto.set('No se encontró el código en la foto. Tómala de cerca, con buena luz y sin reflejo, con el código al centro.');
    } finally {
      URL.revokeObjectURL(url);
      this.leyendoFoto.set(false);
    }
  }

  private emitir(texto: string): void {
    this.detener();
    navigator.vibrate?.(80);
    this.leido.emit(texto.trim());
  }

  cancelar(): void {
    this.detener();
    this.cerrar.emit();
  }

  ngOnDestroy(): void {
    this.detener();
  }

  private detener(): void {
    this.controles?.stop();
    this.controles = null;
  }

  private fallar(mensaje: string): void {
    this.detener();
    this.error.set(mensaje);
    this.estado.set('error');
  }
}
