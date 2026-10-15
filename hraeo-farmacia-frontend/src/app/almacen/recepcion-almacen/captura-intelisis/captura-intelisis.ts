import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { AlmacenApi, RegistroIntelisis, TipoCapturaIntelisis, UbicacionCantidad, mensajeDeError } from '../../almacen-api';
import { InventarioAlmacen } from '../../inventario-almacen';
import { PedidosAlmacen } from '../../pedidos-almacen';
import { RotacionAlmacen } from '../../rotacion-almacen';
import { SolicitudesAlmacen, formatoCaducidad } from '../../solicitudes-almacen';
import { EscanerCamara } from '../../../shared/escaner-camara/escaner-camara';
import { leerGs1 } from '../../../shared/gs1';
import { Notificaciones } from '../../../shared/notificaciones/notificaciones';

type TipoMensaje = 'success' | 'error' | 'info';

// Captura con Intelisis (diseño aprobado), la misma para Registrar entrada (HU10) y
// para el alta inicial del inventario (HU21):
//   1) se escanea la caja (un código nuevo se asocia a su clave una sola vez),
//   2) se elige el registro de Intelisis: lote, caducidad, proveedor y pedido salen de ahí,
//   3) Almacén pone la ubicación y confirma las cajas que contó.
// Sin registro en Intelisis no se puede recibir.
@Component({
  selector: 'app-captura-intelisis',
  imports: [FormsModule, DecimalPipe, EscanerCamara],
  templateUrl: './captura-intelisis.html',
  styleUrls: ['../recepcion-comun.css', './captura-intelisis.css'],
})
export class CapturaIntelisis {
  readonly tipo = input<TipoCapturaIntelisis>('ENTRADA');

  private readonly api = inject(AlmacenApi);
  private readonly notificaciones = inject(Notificaciones);
  private readonly inventario = inject(InventarioAlmacen);
  private readonly pedidos = inject(PedidosAlmacen);
  private readonly rotacion = inject(RotacionAlmacen);
  protected readonly catalogo = inject(SolicitudesAlmacen);
  protected readonly formatoFecha = formatoCaducidad;

  readonly esAlta = computed(() => this.tipo() === 'ALTA_INICIAL');

  codigoEscaneado = '';
  readonly camaraAbierta = signal(false);
  // Código leído y si ya estaba asociado a una clave.
  readonly codigo = signal('');
  readonly codigoNuevo = signal(false);
  // Lote leído de un código GS1, para elegir solo el registro de ese lote.
  private loteGs1 = '';

  claveEscrita = '';
  readonly clave = signal('');
  readonly nombre = computed(() => this.catalogo.nombreMedicamento(this.clave()));

  readonly registros = signal<RegistroIntelisis[]>([]);
  readonly cargandoRegistros = signal(false);
  readonly disponibles = computed(() => this.registros().filter((registro) => !registro.bloqueo));
  readonly seleccionado = signal<RegistroIntelisis | null>(null);

  ubicaciones: UbicacionCantidad[] = [{ ubicacion: '', cajas: 0 }];
  observaciones = '';

  readonly mensaje = signal('');
  readonly tipoMensaje = signal<TipoMensaje>('info');
  readonly guardando = signal(false);

  escanearCodigo(): void {
    let codigo = this.codigoEscaneado.trim();
    if (!codigo) {
      this.mostrarMensaje('Escanea o captura el código de barras de la caja.', 'error');
      return;
    }
    // Código GS1 (DataMatrix o GS1-128): el producto es su GTIN y trae el lote.
    const gs1 = leerGs1(codigo);
    this.loteGs1 = '';
    if (gs1?.gtin) {
      codigo = gs1.gtin;
      this.codigoEscaneado = codigo;
      this.loteGs1 = gs1.lote ?? '';
    }
    this.reiniciarClave();
    this.codigo.set(codigo);

    this.api.resolverCodigo(codigo).subscribe({
      next: (producto) => {
        this.codigoNuevo.set(false);
        this.usarClave(producto.clave);
      },
      error: (error) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.codigoNuevo.set(true);
          this.mostrarMensaje(`Código nuevo (${codigo}). Elige la clave de esta caja; al confirmar se asocia y la próxima vez se reconoce solo.`, 'info');
          return;
        }
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo consultar el código.'), 'error');
      },
    });
  }

  codigoDesdeCamara(codigo: string): void {
    this.camaraAbierta.set(false);
    this.codigoEscaneado = codigo;
    this.escanearCodigo();
  }

  /** Clave elegida para un código nuevo. Solo se aceptan claves del catálogo de Intelisis. */
  elegirClave(valor: string): void {
    const clave = (valor ?? '').split('·')[0].trim();
    if (!clave) return;
    if (!this.catalogo.catalogo().some((medicamento) => medicamento.clave === clave)) {
      this.mostrarMensaje(`La clave ${clave} no está en el catálogo de Intelisis; no se puede registrar.`, 'error');
      return;
    }
    this.claveEscrita = clave;
    this.usarClave(clave);
  }

  private usarClave(clave: string): void {
    this.clave.set(clave);
    this.seleccionado.set(null);
    this.registros.set([]);
    this.cargandoRegistros.set(true);
    this.mensaje.set('');
    this.api.registrosIntelisis(clave, this.tipo()).subscribe({
      next: (registros) => {
        this.cargandoRegistros.set(false);
        this.registros.set(registros);
        const disponibles = this.disponibles();
        if (!registros.length) {
          this.mostrarMensaje(
            this.esAlta()
              ? `Intelisis no tiene existencia de ${clave} en el almacén de medicamentos. Hasta que la tenga no se puede dar de alta.`
              : `Intelisis no tiene registrada ninguna entrada reciente de ${clave}. No se puede recibir hasta que la capturen en Intelisis.`,
            'error',
          );
          return;
        }
        // Se elige solo cuando no hay duda: el lote del código GS1 o el único disponible.
        const delLote = this.loteGs1 ? disponibles.filter((registro) => registro.lote.toUpperCase() === this.loteGs1.toUpperCase()) : [];
        if (delLote.length === 1) this.elegir(delLote[0]);
        else if (disponibles.length === 1) this.elegir(disponibles[0]);
        else if (!disponibles.length) this.mostrarMensaje('Todos los registros de Intelisis de esta clave están bloqueados; revisa el motivo de cada uno.', 'error');
      },
      error: (error) => {
        this.cargandoRegistros.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudieron leer los registros de Intelisis.'), 'error');
      },
    });
  }

  elegir(registro: RegistroIntelisis): void {
    if (registro.bloqueo) return;
    this.seleccionado.set(registro);
    // Se propone lo que dice Intelisis; Almacén corrige con lo que contó.
    const cajas = registro.cajas !== null && Number.isInteger(Number(registro.cajas)) ? Number(registro.cajas) : 0;
    this.ubicaciones = [{ ubicacion: '', cajas }];
    this.observaciones = '';
    this.mensaje.set('');
  }

  esSeleccionado(registro: RegistroIntelisis): boolean {
    const actual = this.seleccionado();
    return !!actual && actual.origen === registro.origen && actual.idIntelisis === registro.idIntelisis && actual.lote === registro.lote;
  }

  agregarUbicacion(): void {
    this.ubicaciones = [...this.ubicaciones, { ubicacion: '', cajas: 0 }];
  }

  quitarUbicacion(indice: number): void {
    this.ubicaciones = this.ubicaciones.filter((_, i) => i !== indice);
  }

  totalCajas(): number {
    return this.ubicaciones.reduce((total, fila) => total + (Number(fila.cajas) || 0), 0);
  }

  /** Diferencia entre lo contado y lo que dice Intelisis (null si Intelisis no sabe las cajas). */
  diferencia(): number | null {
    const cajas = this.seleccionado()?.cajas;
    return cajas === null || cajas === undefined ? null : Math.round((this.totalCajas() - Number(cajas)) * 100) / 100;
  }

  confirmar(): void {
    const registro = this.seleccionado();
    if (!this.codigo() || !registro) {
      this.mostrarMensaje('Escanea la caja y elige el registro de Intelisis.', 'error');
      return;
    }
    const ubicaciones = this.ubicaciones.map((fila) => ({ ubicacion: fila.ubicacion.trim(), cajas: Number(fila.cajas) }));
    if (ubicaciones.some((fila) => !fila.ubicacion || !Number.isInteger(fila.cajas) || fila.cajas <= 0)) {
      this.mostrarMensaje('En cada ubicación pon el nombre y las cajas que contaste (entero mayor a cero).', 'error');
      return;
    }
    const total = ubicaciones.reduce((suma, fila) => suma + fila.cajas, 0);
    const diferencia = this.diferencia();

    this.guardando.set(true);
    this.api
      .registrarEntrada({
        codigo: this.codigo(),
        clave: this.clave(),
        origen: registro.origen,
        idIntelisis: registro.idIntelisis,
        lote: registro.lote,
        ubicaciones,
        observaciones: this.observaciones.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          const nota = diferencia ? ` Diferencia contra Intelisis: ${diferencia > 0 ? '+' : ''}${diferencia} cajas (quedó registrada).` : '';
          this.mostrarMensaje(
            `${this.esAlta() ? 'Alta registrada' : 'Entrada registrada'}: ${this.clave()} lote ${registro.lote}, ${total} cajas en ${ubicaciones.map((fila) => fila.ubicacion).join(', ')}.${nota}`,
            'success',
            this.esAlta() ? 'Alta inicial registrada' : 'Entrada registrada',
          );
          this.limpiar();
          this.inventario.recargar();
          this.pedidos.recargar();
          this.rotacion.recargar();
          // La clave ya tiene código de barras: queda dada de alta en el catálogo.
          this.catalogo.recargar();
        },
        error: (error) => {
          this.guardando.set(false);
          this.mostrarMensaje(mensajeDeError(error, 'No se pudo registrar.'), 'error');
        },
      });
  }

  /** Deja la pantalla lista para la siguiente caja. */
  limpiar(): void {
    this.codigoEscaneado = '';
    this.codigo.set('');
    this.codigoNuevo.set(false);
    this.loteGs1 = '';
    this.reiniciarClave();
  }

  private reiniciarClave(): void {
    this.claveEscrita = '';
    this.clave.set('');
    this.registros.set([]);
    this.seleccionado.set(null);
    this.ubicaciones = [{ ubicacion: '', cajas: 0 }];
    this.observaciones = '';
  }

  private mostrarMensaje(texto: string, tipo: TipoMensaje, titulo = 'Listo'): void {
    // Lo que sí se hizo se avisa con un popup; errores e indicaciones se quedan junto al formulario.
    if (tipo === 'success') {
      this.mensaje.set('');
      this.notificaciones.exito(titulo, texto);
      return;
    }
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
