import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { mensajeDeError } from '../../../almacen/almacen-api';
import { cargarLogo } from '../../../almacen/despacho/salidas-registradas/comprobante-salidas';
import { CajaQuirofano, CajasQuirofano, TURNOS, ValeCaja } from '../cajas-quirofano';
import { RenglonVale, datosVale, descargarValeExcel, descargarValePdf } from '../vale-documento';

// "Vale de medicamentos para el uso en el quirófano", con el acomodo del formato de Farmacia.
// ?caja=CAJA-001&vale=12: el vale de esa salida (con lo utilizado por paciente y la devolución).
// ?caja=CAJA-001: vale en blanco con lo que trae la caja ahora, para llenarlo a mano.
// Se abre en otra pestaña y manda a imprimir solo.
@Component({
  selector: 'app-imprimir-vale',
  templateUrl: './imprimir-vale.html',
  styleUrl: './imprimir-vale.css',
})
export class ImprimirVale {
  private readonly cajas = inject(CajasQuirofano);
  readonly turnos = TURNOS;
  readonly caja = signal<CajaQuirofano | null>(null);
  readonly vale = signal<ValeCaja | null>(null);
  readonly cargando = signal(true);
  readonly error = signal('');

  // Los mismos datos que el vale en PDF y en Excel (vale-documento.ts).
  readonly datos = computed(() => {
    const caja = this.caja();
    return caja ? datosVale(caja, this.vale()) : null;
  });
  readonly renglones = computed<RenglonVale[]>(() => this.datos()?.renglones ?? []);

  async descargarPdf(): Promise<void> {
    const datos = this.datos();
    if (datos) descargarValePdf(datos, await cargarLogo());
  }

  descargarExcel(): void {
    const datos = this.datos();
    if (datos) descargarValeExcel(datos);
  }

  constructor() {
    const parametros = inject(ActivatedRoute).snapshot.queryParamMap;
    const codigo = parametros.get('caja') ?? '';
    const valeId = Number(parametros.get('vale')) || null;
    this.cajas.detalle(codigo).subscribe({
      next: (caja) => {
        this.caja.set(caja);
        if (valeId) {
          const vale = caja.vales.find((v) => v.id === valeId) ?? null;
          if (!vale) this.error.set('No se encontró ese vale en la caja (solo se guardan los últimos 10).');
          this.vale.set(vale);
        }
        this.cargando.set(false);
        if (!this.error()) setTimeout(() => window.print(), 300);
      },
      error: (e) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(e, 'No se pudo preparar el vale.'));
      },
    });
  }

  imprimir(): void {
    window.print();
  }

  fecha(valor: string | null | undefined): string {
    if (!valor) return '';
    const [anio, mes, dia] = valor.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
  }
}
