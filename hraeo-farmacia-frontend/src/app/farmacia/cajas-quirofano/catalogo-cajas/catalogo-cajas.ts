import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { MarcaSeguridad } from '../../../shared/marca-seguridad/marca-seguridad';
import { SeguridadMedicamentos } from '../../../shared/seguridad-medicamentos';
import { Medicamento, mensajeDeError } from '../../../almacen/almacen-api';
import { BuscadorMedicamento } from '../../buscador-medicamento/buscador-medicamento';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { CajaDadaDeBaja, CajaQuirofano, CajasQuirofano, ContenidoCaja, VALES_QUIROFANO, lotesEnRiesgo } from '../cajas-quirofano';
import { datosVale, descargarValeExcel, descargarValePdf } from '../vale-documento';
import { cargarLogo } from '../../../almacen/despacho/salidas-registradas/comprobante-salidas';

const listado = (contenido: ContenidoCaja[]) => contenido.map((item) => `${item.clave}x${item.cantidad}${item.aparte ? '*' : ''}`).join(',');

// Farmacia · Catálogo de cajas de anestesia: cada caja (vale) con su código de barras y su listado
// fijo de medicamentos y cantidades. Un medicamento de red fría no se agrega a una caja. Cambiar el
// listado de una caja que ya existe pide justificación.
@Component({
  selector: 'app-catalogo-cajas',
  imports: [FormsModule, Topbar, Sidebar, MarcaSeguridad, BuscadorMedicamento],
  templateUrl: './catalogo-cajas.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css', '../cajas-quirofano.css'],
})
export class CatalogoCajas {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;
  protected readonly cajas = inject(CajasQuirofano);
  private readonly seguridad = inject(SeguridadMedicamentos);
  readonly vales = VALES_QUIROFANO;

  // Caja que se edita; null = caja nueva.
  readonly editando = signal<CajaQuirofano | null>(null);
  readonly codigo = signal('');
  readonly nombre = signal('');
  readonly servicio = signal('');
  readonly contenido = signal<ContenidoCaja[]>([]);
  readonly cantidad = signal<number | null>(null);
  readonly elegido = signal<Medicamento | null>(null);
  readonly justificacion = signal('');
  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'info' | 'error'>('info');
  readonly cargando = signal(true);
  readonly guardando = signal(false);

  readonly clavesEnLista = computed(() => this.contenido().map((item) => item.clave));
  readonly cambioDeListado = computed(() => {
    const caja = this.editando();
    return !!caja && listado(this.contenidoDe(caja)) !== listado(this.contenido());
  });

  readonly faltantes = computed(() => {
    const faltan: string[] = [];
    if (!this.nombre().trim()) faltan.push('el nombre de la caja');
    if (!this.servicio().trim()) faltan.push('el servicio o sala');
    if (!this.contenido().length) faltan.push('al menos un medicamento');
    if (this.cambioDeListado() && !this.justificacion().trim()) faltan.push('la justificación del cambio');
    return faltan;
  });

  // ---------- Dar de baja y reactivar ----------
  // Caja en la que se está escribiendo el motivo de la baja (o de la reactivación).
  readonly bajando = signal<string | null>(null);
  readonly reactivando = signal<string | null>(null);
  readonly motivo = signal('');
  readonly procesando = signal(false);
  readonly deBaja = signal<CajaDadaDeBaja[]>([]);
  readonly verBajas = signal(false);

  constructor() {
    this.seguridad.cargar();
    this.cajas.cargar().subscribe({
      next: () => this.cargando.set(false),
      error: (e) => {
        this.cargando.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudieron cargar las cajas.'), 'error');
      },
    });
    this.cargarBajas();
  }

  private cargarBajas(): void {
    this.cajas.dadasDeBaja().subscribe({ next: (lista) => this.deBaja.set(lista), error: () => this.deBaja.set([]) });
  }

  /** Piezas que trae la caja ahora (regresan al estante si se da de baja). */
  piezasDentro(caja: CajaQuirofano): number {
    return caja.renglones.reduce((total, r) => total + r.tiene, 0);
  }

  pedirBaja(caja: CajaQuirofano): void {
    this.reactivando.set(null);
    this.bajando.set(caja.codigo);
    this.motivo.set('');
  }

  pedirReactivar(codigo: string): void {
    this.bajando.set(null);
    this.reactivando.set(codigo);
    this.motivo.set('');
  }

  cancelarMotivo(): void {
    this.bajando.set(null);
    this.reactivando.set(null);
    this.motivo.set('');
  }

  confirmarBaja(caja: CajaQuirofano): void {
    const motivo = this.motivo().trim();
    if (!motivo) return this.mostrar('Escribe por qué se da de baja la caja.', 'error');
    if (this.procesando()) return;
    this.procesando.set(true);
    const piezas = this.piezasDentro(caja);
    this.cajas.darDeBaja(caja.codigo, motivo).subscribe({
      next: () => {
        this.procesando.set(false);
        this.cancelarMotivo();
        if (this.editando()?.codigo === caja.codigo) this.nueva();
        this.cargarBajas();
        this.mostrar(
          `Caja ${caja.codigo} dada de baja.` + (piezas ? ` Sus ${piezas} piezas regresaron al estante de Farmacia.` : '') +
            ' Su historial se conserva; si fue un error, reactívala abajo.',
          'info',
        );
      },
      error: (e) => {
        this.procesando.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudo dar de baja la caja.'), 'error');
      },
    });
  }

  confirmarReactivar(codigo: string): void {
    const motivo = this.motivo().trim();
    if (!motivo) return this.mostrar('Escribe por qué se vuelve a usar la caja.', 'error');
    if (this.procesando()) return;
    this.procesando.set(true);
    this.cajas.reactivar(codigo, motivo).subscribe({
      next: (caja) => {
        this.procesando.set(false);
        this.cancelarMotivo();
        this.cargarBajas();
        this.mostrar(`Caja ${caja.codigo} reactivada. Regresa vacía: ármala en "Cajas de anestesia".`, 'info');
      },
      error: (e) => {
        this.procesando.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudo reactivar la caja.'), 'error');
      },
    });
  }

  /** Llena la caja nueva con el listado de uno de los 3 vales; después se ajustan las cantidades. */
  usarVale(id: string): void {
    const vale = this.vales.find((v) => v.id === id);
    if (!vale) return;
    this.nombre.set(`Caja ${vale.nombre} `);
    this.servicio.set(vale.servicio);
    // Lo de red fría sí va en el vale, pero no se guarda en la caja: se entrega aparte. El nombre que
    // trae el vale se queda como el nombre propio del renglón (el del catálogo viene en otros formatos).
    this.contenido.set(vale.contenido.map((item) => ({ ...item, descripcion: item.medicamento, aparte: !!item.aparte || this.seguridad.marcas(item.clave).redFria })));
    const aparte = this.contenido().filter((item) => item.aparte).map((item) => item.clave);
    this.mostrar(
      `Listado del vale ${vale.nombre}. Completa el nombre (ej. sala) y ajusta las cantidades de esta caja.` +
        (aparte.length ? ` ${aparte.join(', ')} es de red fría: va en el vale y se entrega aparte del refrigerador.` : ''),
      'info',
    );
  }

  elegir(medicamento: Medicamento): void {
    this.elegido.set(medicamento);
    // Las cajas se quedan fuera del refrigerador: lo de red fría va en el vale pero se entrega aparte.
    this.mostrar(
      this.seguridad.marcas(medicamento.clave).redFria
        ? `${medicamento.clave} es de red fría: queda en el vale pero no se guarda en la caja; sale del refrigerador al entregarla.`
        : '',
      'info',
    );
  }

  agregar(): void {
    const medicamento = this.elegido();
    const cantidad = this.cantidad();
    if (!medicamento) return this.mostrar('Busca y elige el medicamento.', 'error');
    if (!cantidad || cantidad <= 0 || !Number.isInteger(cantidad)) return this.mostrar('La cantidad debe ser un número entero de piezas.', 'error');
    const aparte = this.seguridad.marcas(medicamento.clave).redFria;
    this.contenido.update((lista) => [...lista, { clave: medicamento.clave, medicamento: medicamento.nombreGenerico, cantidad, aparte }]);
    this.elegido.set(null);
    this.cantidad.set(null);
  }

  cambiarCantidad(clave: string, valor: number | string | null): void {
    const cantidad = Math.max(1, Math.trunc(+(valor ?? 1)) || 1);
    this.contenido.update((lista) => lista.map((item) => (item.clave === clave ? { ...item, cantidad } : item)));
  }

  quitar(clave: string): void {
    this.contenido.update((lista) => lista.filter((item) => item.clave !== clave));
  }

  // ---------- Reescribir el nombre o cambiar el medicamento de un renglón ----------
  // Renglón (por clave) que se está reescribiendo o al que se le cambia el medicamento.
  readonly reescribiendo = signal<string | null>(null);
  readonly cambiandoClave = signal<string | null>(null);
  readonly textoNombre = signal('');

  /** El nombre que se ve en la caja y en el vale. */
  nombreDe(item: ContenidoCaja): string {
    return item.descripcion?.trim() || item.medicamento;
  }

  reescribir(item: ContenidoCaja): void {
    this.cambiandoClave.set(null);
    this.reescribiendo.set(item.clave);
    this.textoNombre.set(this.nombreDe(item));
  }

  guardarNombre(clave: string): void {
    const texto = this.textoNombre().trim();
    if (texto.length > 200) return this.mostrar('El nombre no puede pasar de 200 caracteres.', 'error');
    this.contenido.update((lista) => lista.map((item) => (item.clave === clave ? { ...item, descripcion: texto || null } : item)));
    this.reescribiendo.set(null);
    this.mostrar('Nombre cambiado. Guarda los cambios de la caja para que quede.', 'info');
  }

  /** Vuelve al nombre del catálogo de Intelisis. */
  usarNombreCatalogo(clave: string): void {
    this.contenido.update((lista) => lista.map((item) => (item.clave === clave ? { ...item, descripcion: null } : item)));
    this.reescribiendo.set(null);
  }

  pedirCambioMedicamento(clave: string): void {
    this.reescribiendo.set(null);
    this.cambiandoClave.set(clave);
  }

  /** Cambia la clave del renglón (si estaba mal) y conserva las piezas; el nombre propio se borra. */
  cambiarMedicamento(clave: string, medicamento: Medicamento): void {
    const aparte = this.seguridad.marcas(medicamento.clave).redFria;
    this.contenido.update((lista) =>
      lista.map((item) => (item.clave === clave ? { clave: medicamento.clave, medicamento: medicamento.nombreGenerico, cantidad: item.cantidad, aparte, descripcion: null } : item)),
    );
    this.cambiandoClave.set(null);
    this.mostrar(
      `${clave} se cambió por ${medicamento.clave}.` + (this.editando() ? ' Escribe la justificación del cambio y guarda.' : '') +
        (aparte ? ` ${medicamento.clave} es de red fría: va en el vale y se entrega aparte.` : ''),
      'info',
    );
  }

  editar(caja: CajaQuirofano): void {
    this.reescribiendo.set(null);
    this.cambiandoClave.set(null);
    this.editando.set(caja);
    this.codigo.set(caja.codigo);
    this.nombre.set(caja.nombre);
    this.servicio.set(caja.servicio);
    this.contenido.set(this.contenidoDe(caja));
    this.justificacion.set('');
    this.mensaje.set('');
  }

  nueva(): void {
    this.editando.set(null);
    this.codigo.set('');
    this.nombre.set('');
    this.servicio.set('');
    this.contenido.set([]);
    this.justificacion.set('');
    this.elegido.set(null);
    this.cantidad.set(null);
  }

  guardar(): void {
    if (this.faltantes().length || this.guardando()) return;
    const caja = this.editando();
    this.guardando.set(true);
    const peticion = caja
      ? this.cajas.cambiar(caja.codigo, this.nombre().trim(), this.servicio().trim(), this.contenido(), this.justificacion().trim())
      : this.cajas.crear(this.codigo().trim(), this.nombre().trim(), this.servicio().trim(), this.contenido());
    peticion.subscribe({
      next: (guardada) => {
        this.guardando.set(false);
        this.nueva();
        this.recienGuardada.set(guardada);
        this.mostrar(
          caja
            ? `Caja ${guardada.codigo} actualizada.`
            : `Caja ${guardada.codigo} dada de alta. Pega su código en la caja y ármala en "Cajas de anestesia".`,
          'info',
        );
      },
      error: (e) => {
        this.guardando.set(false);
        this.mostrar(mensajeDeError(e, 'No se pudo guardar la caja.'), 'error');
      },
    });
  }

  readonly riesgo = lotesEnRiesgo;

  vencidos(lotes: ReturnType<typeof lotesEnRiesgo>): boolean {
    return lotes.some((l) => l.alerta === 'VENCIDO');
  }

  textoRiesgo(lotes: ReturnType<typeof lotesEnRiesgo>): string {
    return lotes.map((l) => `${l.clave} lote ${l.lote} (${l.caducidad.slice(8, 10)}/${l.caducidad.slice(5, 7)}/${l.caducidad.slice(0, 4)})`).join(', ');
  }

  // ---------- Vale de la caja (en blanco, con las cantidades de su listado) ----------
  readonly recienGuardada = signal<CajaQuirofano | null>(null);

  async valePdf(caja: CajaQuirofano): Promise<void> {
    descargarValePdf(datosVale(caja, null), await cargarLogo());
  }

  valeExcel(caja: CajaQuirofano): void {
    descargarValeExcel(datosVale(caja, null));
  }

  imprimirVale(caja: CajaQuirofano): void {
    window.open(`/farmacia/quirofano/vale?caja=${encodeURIComponent(caja.codigo)}`, '_blank');
  }

  private contenidoDe(caja: CajaQuirofano): ContenidoCaja[] {
    return caja.renglones.map((r) => ({ clave: r.clave, medicamento: r.nombreCatalogo ?? r.medicamento, cantidad: r.cantidad, aparte: r.aparte, descripcion: r.descripcion }));
  }

  private mostrar(texto: string, tipo: 'info' | 'error'): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}
