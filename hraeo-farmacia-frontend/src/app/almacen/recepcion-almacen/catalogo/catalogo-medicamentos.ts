import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SolicitudesAlmacen } from '../../solicitudes-almacen';
import { Medicamento } from '../../almacen-api';

// Se pintan de a poco: el catálogo de Intelisis son casi mil claves.
const FILAS_POR_TANDA = 200;

type FiltroAlta = 'TODOS' | 'ALTA' | 'SIN_ALTA';

// Recepción · Catálogo de medicamentos (solo consulta). El catálogo se lee de
// Intelisis: en HAERO no se captura nada. Lo único que HAERO le agrega a cada clave
// son sus códigos de barras, que se asocian al registrar la entrada de la caja; una
// clave con código está "dada de alta en HAERO" y solo esas entran al inventario.
@Component({
  selector: 'app-catalogo-medicamentos',
  imports: [FormsModule],
  templateUrl: './catalogo-medicamentos.html',
  styleUrls: ['../recepcion-comun.css', '../pedidos/pedidos-recepcion.css', '../inventario-inicial.css', './catalogo-medicamentos.css'],
  host: { '(document:keydown.escape)': 'cerrarDetalle()' },
})
export class CatalogoMedicamentos {
  protected readonly datos = inject(SolicitudesAlmacen);

  constructor() {
    // Al entrar se vuelve a leer: la sincronización con Intelisis pudo agregar claves.
    this.datos.recargar();
  }

  readonly filtro = signal('');
  readonly filtroAlta = signal<FiltroAlta>('TODOS');
  readonly limite = signal(FILAS_POR_TANDA);

  readonly filtrosAlta = computed(() => {
    const lista = this.datos.catalogo();
    const dadas = lista.filter((item) => item.dadoDeAlta).length;
    return [
      { valor: 'TODOS' as const, etiqueta: 'Todas', total: lista.length },
      { valor: 'ALTA' as const, etiqueta: 'Dadas de alta en HAERO', total: dadas },
      { valor: 'SIN_ALTA' as const, etiqueta: 'Sin código de barras', total: lista.length - dadas },
    ];
  });

  readonly medicamentos = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    const alta = this.filtroAlta();
    return [...this.datos.catalogo()]
      .filter((item) => alta === 'TODOS' || (alta === 'ALTA') === item.dadoDeAlta)
      .filter((item) => !texto ||
        [item.clave, item.claveCucop, item.nombreGenerico, item.presentacion, item.formaFarmaceutica, item.concentracion, item.grupoTerapeutico]
          .filter(Boolean).join(' ').toLowerCase().includes(texto))
      .sort((a, b) => a.clave.localeCompare(b.clave));
  });

  readonly visibles = computed(() => this.medicamentos().slice(0, this.limite()));

  filtrar(texto: string): void {
    this.filtro.set(texto);
    this.limite.set(FILAS_POR_TANDA);
  }

  elegirAlta(filtro: FiltroAlta): void {
    this.filtroAlta.set(filtro);
    this.limite.set(FILAS_POR_TANDA);
  }

  mostrarMas(): void {
    this.limite.update((limite) => limite + FILAS_POR_TANDA);
  }

  // ---------- Detalle de un medicamento ----------
  readonly detalle = signal<Medicamento | null>(null);

  verDetalle(medicamento: Medicamento): void {
    this.detalle.set(medicamento);
  }

  cerrarDetalle(): void {
    this.detalle.set(null);
  }

  /** Línea bajo el nombre: forma · concentración · presentación (lo que haya). */
  resumen(medicamento: Medicamento): string {
    return [medicamento.formaFarmaceutica, medicamento.concentracion, medicamento.presentacion].filter(Boolean).join(' · ');
  }

  siNo(valor: boolean | null): string {
    return valor === null || valor === undefined ? '—' : valor ? 'Sí' : 'No';
  }

  estatusTexto(estatus: string): string {
    return estatus === 'ALTA' ? 'Activo' : estatus === 'BAJA' ? 'Baja' : estatus;
  }

  fecha(valor: string): string {
    return new Date(valor).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' });
  }
}
