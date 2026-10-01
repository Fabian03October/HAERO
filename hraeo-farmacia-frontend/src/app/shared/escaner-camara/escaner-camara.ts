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

  constructor() {
    afterNextRender(() => this.iniciar());
  }

  private async iniciar(): Promise<void> {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      this.fallar('El navegador solo permite usar la cámara en una conexión segura. Abre la página con https:// (npm run start:celular).');
      return;
    }
    const hints = new Map<DecodeHintType, unknown>([
      [DecodeHintType.POSSIBLE_FORMATS, FORMATOS],
      [DecodeHintType.TRY_HARDER, true],
    ]);
    const lector = new BrowserMultiFormatReader(hints, { delayBetweenScanAttempts: 150 });
    try {
      // Cámara trasera cuando existe (teléfono); en una laptop usa la que haya.
      this.controles = await lector.decodeFromConstraints(
        { video: { facingMode: { ideal: 'environment' } } },
        this.video().nativeElement,
        (resultado) => {
          if (!resultado) return;
          this.detener();
          navigator.vibrate?.(80);
          this.leido.emit(resultado.getText().trim());
        },
      );
      this.estado.set('leyendo');
    } catch (error) {
      const nombre = (error as { name?: string })?.name;
      if (nombre === 'NotAllowedError') this.fallar('No se dio permiso para usar la cámara. Actívalo en la configuración del navegador y vuelve a intentar.');
      else if (nombre === 'NotFoundError' || nombre === 'OverconstrainedError') this.fallar('No se encontró una cámara en este dispositivo.');
      else if (nombre === 'NotReadableError') this.fallar('La cámara está ocupada por otra aplicación. Ciérrala y vuelve a intentar.');
      else this.fallar('No se pudo abrir la cámara.');
    }
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
