import { Component, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { AlmacenApi, RegistroIntelisis, TipoCapturaIntelisis, UbicacionCantidad, mensajeDeError } from '../../almacen-api';
import { InventarioAlmacen } from '../../inventario-almacen';
import { PedidosAlmacen } from '../../pedidos-almacen';
import { RotacionAlmacen } from '../../rotacion-almacen';
import { SolicitudesAlmacen, formatoCaducidad } from '../../solicitudes-almacen';
import { EscanerCamara } from '../../../shared/escaner-camara/escaner-camara';
import { DatosGs1, leerGs1 } from '../../../shared/gs1';
import { Notificaciones } from '../../../shared/notificaciones/notificaciones';

type TipoMensaje = 'success' | 'error' | 'info';

// Medicamentos que se muestran de una vez en el buscador de la clave ("Mostrar más" agrega otros tantos).
const MEDICAMENTOS_POR_PAGINA = 50;

// Preferencias de este navegador para la ubicación de trabajo.
const CLAVE_ULTIMA_UBICACION = 'haero.captura.ultimaUbicacion';
const CLAVE_MANTENER_UBICACION = 'haero.captura.mantenerUbicacion';

function leerGuardado(clave: string): string | null {
  try {
    return localStorage.getItem(clave);
  } catch {
    return null;
  }
}

function normalizar(valor: unknown): string {
  return String(valor ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase();
}

// Captura con Intelisis (diseño aprobado), la misma para Registrar entrada (HU10) y
// para el alta inicial del inventario (HU21):
//   1) se escanea la caja (un código nuevo se asocia a su clave una sola vez),
//   2) se elige el registro de Intelisis: lote, caducidad, proveedor y pedido salen de ahí,
//   3) Almacén pone la ubicación y confirma las cajas que contó.
// Sin registro en Intelisis no se puede recibir.
@Component({
  selector: 'app-captura-intelisis',
  imports: [FormsModule, DecimalPipe, NgTemplateOutlet, EscanerCamara],
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
  // Lo que trae un código GS1 además del producto (lote, caducidad, serie). El lote
  // sirve para elegir solo el registro de Intelisis de ese lote.
  readonly datosGs1 = signal<DatosGs1 | null>(null);
  private loteGs1 = '';

  // Buscador de la clave para un código nuevo: muestra nombre y presentación completos.
  readonly buscarClave = signal('');
  // La lista se abre al tocar el buscador; sin texto muestra todo el catálogo.
  readonly listaMedicamentosAbierta = signal(false);
  readonly limiteMedicamentos = signal(MEDICAMENTOS_POR_PAGINA);
  readonly medicamentosCoinciden = computed(() => {
    const palabras = normalizar(this.buscarClave()).split(/\s+/).filter(Boolean);
    const catalogo = this.catalogo.catalogo();
    if (!palabras.length) return catalogo;
    return catalogo.filter((medicamento) => {
      const todo = normalizar(`${medicamento.clave} ${medicamento.nombreGenerico} ${medicamento.presentacion ?? ''} ${medicamento.claveCucop ?? ''}`);
      return palabras.every((palabra) => todo.includes(palabra));
    });
  });
  readonly medicamentosEncontrados = computed(() => this.medicamentosCoinciden().slice(0, this.limiteMedicamentos()));

  escribirClave(texto: string): void {
    this.buscarClave.set(texto);
    this.limiteMedicamentos.set(MEDICAMENTOS_POR_PAGINA);
    this.listaMedicamentosAbierta.set(true);
  }

  mostrarMasMedicamentos(): void {
    this.limiteMedicamentos.update((limite) => limite + MEDICAMENTOS_POR_PAGINA);
  }
  readonly clave = signal('');
  readonly nombre = computed(() => this.catalogo.nombreMedicamento(this.clave()));

  readonly registros = signal<RegistroIntelisis[]>([]);
  readonly cargandoRegistros = signal(false);
  readonly disponibles = computed(() => this.registros().filter((registro) => !registro.bloqueo));
  readonly bloqueados = computed(() => this.registros().filter((registro) => !!registro.bloqueo));
  readonly seleccionado = signal<RegistroIntelisis | null>(null);

  // Búsqueda y filtro de la lista de registros (puede haber muchos lotes de una clave).
  readonly buscarRegistro = signal('');
  readonly filtroRegistros = signal<'DISPONIBLES' | 'BLOQUEADOS' | 'TODOS'>('DISPONIBLES');
  readonly registrosVisibles = computed(() => {
    const filtro = this.filtroRegistros();
    const texto = normalizar(this.buscarRegistro());
    const base = filtro === 'DISPONIBLES' ? this.disponibles() : filtro === 'BLOQUEADOS' ? this.bloqueados() : this.registros();
    if (!texto) return base;
    return base.filter((registro) =>
      normalizar(
        [registro.lote, registro.movimiento, registro.proveedor, registro.numeroPedido, registro.contrato, registro.referencia,
          registro.caducidad ? formatoCaducidad(registro.caducidad) : '', registro.fecha ? formatoCaducidad(registro.fecha) : '']
          .filter(Boolean)
          .join(' '),
      ).includes(texto),
    );
  });

  ubicaciones: UbicacionCantidad[] = [{ ubicacion: '', cajas: 0 }];
  observaciones = '';

  // Ubicación de trabajo: la de la caja anterior, para no escribirla en cada caja del mismo anaquel.
  readonly mantenerUbicacion = signal(leerGuardado(CLAVE_MANTENER_UBICACION) !== '0');
  readonly ultimaUbicacion = signal(leerGuardado(CLAVE_ULTIMA_UBICACION) ?? '');
  private readonly campoCodigo = viewChild<ElementRef<HTMLInputElement>>('campoCodigo');

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
    this.datosGs1.set(null);
    if (gs1?.gtin) {
      codigo = gs1.gtin;
      this.codigoEscaneado = codigo;
      this.loteGs1 = gs1.lote ?? '';
      this.datosGs1.set(gs1);
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
  elegirClave(clave: string): void {
    if (!this.catalogo.catalogo().some((medicamento) => medicamento.clave === clave)) {
      this.mostrarMensaje(`La clave ${clave} no está en el catálogo de Intelisis; no se puede registrar.`, 'error');
      return;
    }
    this.buscarClave.set('');
    this.listaMedicamentosAbierta.set(false);
    this.usarClave(clave);
  }

  /** Para un código nuevo: vuelve a buscar la clave (por si se eligió la que no era). */
  cambiarClave(): void {
    this.reiniciarClave();
    this.mensaje.set('');
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
        this.buscarRegistro.set('');
        const disponibles = this.disponibles();
        this.filtroRegistros.set(disponibles.length ? 'DISPONIBLES' : 'TODOS');
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
        // Igual, o el lote leído empieza con el de Intelisis: pasa cuando el lector no manda el
        // separador y al lote se le pega el número de serie ("IIP2307221QS1…").
        const leido = this.loteGs1.trim().toUpperCase();
        const mismoLote = (registro: RegistroIntelisis) => {
          const lote = registro.lote.trim().toUpperCase();
          return lote === leido || (lote.length >= 4 && leido.startsWith(lote) && /^(21|17|22)/.test(leido.slice(lote.length)));
        };
        const delLote = this.loteGs1 ? disponibles.filter(mismoLote) : [];
        if (this.loteGs1 && !registros.some(mismoLote)) {
          this.mostrarMensaje(
            `El lote de la caja (${this.loteGs1}) no aparece en Intelisis ${this.esAlta() ? 'con existencia' : 'entre las entradas recientes'} de ${clave}. ` +
              'Revisa que sea la misma caja; si es un lote vencido o ya consumido, Intelisis ya no lo tiene.',
            'error',
          );
          return;
        }
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
    // Volver a tocar el registro ya elegido no borra lo capturado.
    if (registro.bloqueo || this.esSeleccionado(registro)) return;
    this.seleccionado.set(registro);
    // Se propone lo que dice Intelisis; Almacén corrige con lo que contó.
    const cajas = registro.cajas !== null && Number.isInteger(Number(registro.cajas)) ? Number(registro.cajas) : 0;
    // La ubicación de la caja anterior se queda puesta (se da de alta anaquel por anaquel).
    this.ubicaciones = [{ ubicacion: this.mantenerUbicacion() ? this.ultimaUbicacion() : '', cajas }];
    this.observaciones = '';
    this.mensaje.set('');
  }

  /** Para resaltar la caducidad: ya vencido o a menos de 9 meses (mismo umbral que las alertas). */
  estadoCaducidad(registro: RegistroIntelisis): 'vencido' | 'pronto' | '' {
    if (!registro.caducidad) return '';
    const hoy = new Date().toLocaleDateString('en-CA');
    if (registro.caducidad < hoy) return 'vencido';
    const limite = new Date();
    limite.setMonth(limite.getMonth() + 9);
    return registro.caducidad < limite.toLocaleDateString('en-CA') ? 'pronto' : '';
  }

  /** Vuelve a la lista para elegir otro registro. */
  cambiarRegistro(): void {
    this.seleccionado.set(null);
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
          this.recordarUbicacion(ubicaciones[ubicaciones.length - 1].ubicacion);
          this.limpiar();
          this.enfocarEscaneo();
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

  /** Guarda la ubicación para la siguiente caja (también en este navegador, por si se recarga la página). */
  private recordarUbicacion(ubicacion: string): void {
    this.ultimaUbicacion.set(ubicacion);
    try {
      localStorage.setItem(CLAVE_ULTIMA_UBICACION, ubicacion);
    } catch {
      // Sin almacenamiento del navegador solo se recuerda mientras la página siga abierta.
    }
  }

  /** Deja de usar la ubicación de trabajo: la siguiente caja se captura desde cero. */
  olvidarUbicacion(): void {
    this.recordarUbicacion('');
    if (this.seleccionado()) this.ubicaciones = this.ubicaciones.map((fila) => ({ ...fila, ubicacion: '' }));
  }

  cambiarMantenerUbicacion(valor: boolean): void {
    this.mantenerUbicacion.set(valor);
    try {
      localStorage.setItem(CLAVE_MANTENER_UBICACION, valor ? '1' : '0');
    } catch {
      // Igual que arriba: solo dura mientras la página esté abierta.
    }
  }

  /** Después de guardar, el cursor queda listo para escanear la siguiente caja. */
  private enfocarEscaneo(): void {
    setTimeout(() => this.campoCodigo()?.nativeElement.focus());
  }

  /** Deja la pantalla lista para la siguiente caja. */
  limpiar(): void {
    this.codigoEscaneado = '';
    this.codigo.set('');
    this.codigoNuevo.set(false);
    this.loteGs1 = '';
    this.datosGs1.set(null);
    this.reiniciarClave();
  }

  private reiniciarClave(): void {
    this.buscarClave.set('');
    this.limiteMedicamentos.set(MEDICAMENTOS_POR_PAGINA);
    this.listaMedicamentosAbierta.set(false);
    this.clave.set('');
    this.registros.set([]);
    this.seleccionado.set(null);
    this.buscarRegistro.set('');
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
