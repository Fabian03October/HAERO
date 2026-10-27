import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { EscanerCamara } from '../../../shared/escaner-camara/escaner-camara';
import { leerGs1 } from '../../../shared/gs1';
import { Medicamento, mensajeDeError, ultimoDiaDelMes } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { BuscadorMedicamento } from '../../buscador-medicamento/buscador-medicamento';
import { FarmaciaApi } from '../../farmacia-api';

// Caja del estante lista para darse de alta (HU25).
interface CajaAlta {
  id: number;
  clave: string;
  medicamento: string;
  lote: string;
  caducidad: string;
  piezas: number;
}

// Farmacia · Alta inicial (HU25): se arma la lista de lo que hay en los estantes escaneando
// cada caja y se guarda de una vez en el inventario de Farmacia. La clave se elige del catálogo porque el rol Farmacia no consulta los códigos de
// barras de Almacén; el lote y la caducidad salen del código GS1 o se escriben.
@Component({
  selector: 'app-alta-inicial-farmacia',
  imports: [FormsModule, Topbar, Sidebar, EscanerCamara, BuscadorMedicamento],
  templateUrl: './alta-inicial-farmacia.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css'],
})
export class AltaInicialFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;
  private readonly api = inject(FarmaciaApi);
  readonly guardando = signal(false);

  codigoEscaneado = '';
  readonly camaraAbierta = signal(false);
  private readonly campoCodigo = viewChild<ElementRef<HTMLInputElement>>('campoCodigo');

  // Caja que se está capturando.
  readonly medicamento = signal<Medicamento | null>(null);
  readonly lote = signal('');
  readonly caducidad = signal('');
  readonly piezas = signal<number | null>(null);
  readonly loteDelCodigo = signal(false);

  readonly cajas = signal<CajaAlta[]>([]);
  private siguienteId = 1;
  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'info' | 'error' | 'warn'>('info');

  private readonly hoy = (() => {
    const ahora = new Date();
    return `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(ahora.getDate()).padStart(2, '0')}`;
  })();

  readonly vencido = computed(() => !!this.caducidad() && this.caducidad() < this.hoy);
  readonly totalPiezas = computed(() => this.cajas().reduce((total, caja) => total + caja.piezas, 0));
  readonly totalClaves = computed(() => new Set(this.cajas().map((caja) => caja.clave)).size);

  escanear(): void {
    const codigo = this.codigoEscaneado.trim();
    if (!codigo) {
      this.mostrarMensaje('Escanea o captura el código de barras de la caja.', 'error');
      return;
    }
    const gs1 = leerGs1(codigo);
    if (gs1?.lote) {
      this.lote.set(gs1.lote);
      this.caducidad.set(gs1.caducidad ?? '');
      this.loteDelCodigo.set(true);
      this.mostrarMensaje(`Código GS1 leído: lote ${gs1.lote}. Elige la clave y escribe las piezas que contaste.`, 'info');
    } else {
      this.loteDelCodigo.set(false);
      this.mostrarMensaje('Este código no trae lote (no es GS1). Escribe el lote y la caducidad de la caja.', 'warn');
    }
    this.codigoEscaneado = '';
  }

  codigoDesdeCamara(codigo: string): void {
    this.camaraAbierta.set(false);
    this.codigoEscaneado = codigo;
    this.escanear();
  }

  agregar(): void {
    const medicamento = this.medicamento();
    const lote = this.lote().trim();
    const piezas = this.piezas();
    if (!medicamento) return this.mostrarMensaje('Elige la clave de la caja.', 'error');
    if (!lote || !this.caducidad()) return this.mostrarMensaje('Falta el lote o la caducidad de la caja.', 'error');
    if (!piezas || piezas <= 0 || !Number.isInteger(piezas)) return this.mostrarMensaje('Escribe las piezas que contaste (número entero mayor a cero).', 'error');

    // HU25 CA4: un mismo lote no se da de alta dos veces; si se vuelve a escanear, se suman las piezas.
    const repetida = this.cajas().find((caja) => caja.clave === medicamento.clave && caja.lote === lote);
    if (repetida) {
      this.cajas.update((lista) => lista.map((caja) => (caja.id === repetida.id ? { ...caja, piezas: caja.piezas + piezas } : caja)));
      this.mostrarMensaje(`El lote ${lote} ya estaba en la lista; se sumaron ${piezas} piezas.`, 'info');
    } else {
      this.cajas.update((lista) => [
        ...lista,
        { id: this.siguienteId++, clave: medicamento.clave, medicamento: medicamento.nombreGenerico, lote, caducidad: this.caducidad(), piezas },
      ]);
      this.mensaje.set('');
    }
    this.reiniciarCaja();
  }

  // Guarda toda la lista; cada alta queda con el usuario, la fecha y la hora.
  darDeAlta(): void {
    if (!this.cajas().length || this.guardando()) return;
    this.guardando.set(true);
    const partidas = this.cajas().map((caja) => ({
      clave: caja.clave,
      lote: caja.lote,
      caducidad: ultimoDiaDelMes(caja.caducidad),
      piezas: caja.piezas,
    }));
    this.api.altaInicial(partidas).subscribe({
      next: (resultado) => {
        this.guardando.set(false);
        this.cajas.set([]);
        this.mostrarMensaje(
          `Listo: se dieron de alta ${resultado.lotes} ${resultado.lotes === 1 ? 'lote' : 'lotes'} con ${resultado.piezas.toLocaleString('es-MX')} piezas.`,
          'info',
        );
      },
      error: (error) => {
        this.guardando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo dar de alta la lista.'), 'error');
      },
    });
  }

  quitar(id: number): void {
    this.cajas.update((lista) => lista.filter((caja) => caja.id !== id));
  }

  reiniciarCaja(): void {
    this.medicamento.set(null);
    this.lote.set('');
    this.caducidad.set('');
    this.piezas.set(null);
    this.loteDelCodigo.set(false);
    this.campoCodigo()?.nativeElement.focus();
  }

  fecha(valor: string): string {
    const [anio, mes, dia] = valor.split('-');
    return `${dia}/${mes}/${anio}`;
  }

  esVencida(caja: CajaAlta): boolean {
    return caja.caducidad < this.hoy;
  }

  private mostrarMensaje(texto: string, tipo: 'info' | 'error' | 'warn'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
