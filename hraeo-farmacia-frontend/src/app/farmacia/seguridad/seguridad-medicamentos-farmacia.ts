import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { MarcaSeguridad } from '../../shared/marca-seguridad/marca-seguridad';
import { SeguridadMedicamentos } from '../../shared/seguridad-medicamentos';
import { AlmacenApi, Medicamento, mensajeDeError } from '../../almacen/almacen-api';
import { FarmaciaApi } from '../farmacia-api';
import { FARMACIA_MENU } from '../farmacia-nav';

type Filtro = 'ALTO' | 'FRIA' | 'DIFERENTE' | 'TODAS';
type OpcionRiesgo = 'INTELISIS' | 'SI' | 'NO';

// Renglones que se pintan a la vez; con el buscador se llega a cualquier clave.
const RENGLONES = 80;

// Farmacia · Seguridad del paciente: qué claves son de alto riesgo (lista ISMP: electrolitos
// concentrados, quimioterapia, citotóxicos…) y cuáles son de red fría. Parte de lo que dice
// Intelisis (riesgo = ALTO) y Farmacia lo corrige clave por clave. Las etiquetas se ven en todas
// las pantallas donde aparece la clave.
@Component({
  selector: 'app-seguridad-medicamentos-farmacia',
  imports: [FormsModule, Topbar, Sidebar, MarcaSeguridad],
  templateUrl: './seguridad-medicamentos-farmacia.html',
  styleUrls: ['../panel-farmacia/panel-farmacia.css', '../farmacia-comun.css', './seguridad-medicamentos-farmacia.css'],
})
export class SeguridadMedicamentosFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;
  private readonly farmaciaApi = inject(FarmaciaApi);
  private readonly seguridad = inject(SeguridadMedicamentos);
  // Supervisión solo consulta.
  readonly puedeEditar = computed(() => this.session.usuarioActual()?.rol === 'FARMACIA');

  readonly catalogo = signal<Medicamento[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly aviso = signal('');
  readonly guardando = signal<string | null>(null);
  readonly filtro = signal<Filtro>('ALTO');
  readonly busqueda = signal('');

  readonly filtros = computed(() => {
    const lista = this.catalogo();
    return [
      { valor: 'ALTO' as Filtro, etiqueta: 'Alto riesgo', total: lista.filter((m) => m.altoRiesgo).length },
      { valor: 'FRIA' as Filtro, etiqueta: 'Red fría', total: lista.filter((m) => m.redFria).length },
      { valor: 'DIFERENTE' as Filtro, etiqueta: 'Corregidas por Farmacia', total: lista.filter((m) => m.altoRiesgoMarcado !== null).length },
      { valor: 'TODAS' as Filtro, etiqueta: 'Todas', total: lista.length },
    ];
  });

  readonly filtradas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return this.catalogo()
      .filter((m) => {
        if (this.filtro() === 'ALTO' && !m.altoRiesgo) return false;
        if (this.filtro() === 'FRIA' && !m.redFria) return false;
        if (this.filtro() === 'DIFERENTE' && m.altoRiesgoMarcado === null) return false;
        return !texto || `${m.clave} ${m.nombreGenerico} ${m.grupoTerapeutico ?? ''}`.toLowerCase().includes(texto);
      })
      .slice(0, RENGLONES);
  });

  constructor() {
    inject(AlmacenApi).listarMedicamentos().subscribe({
      next: (lista) => {
        this.catalogo.set([...lista].sort((a, b) => a.nombreGenerico.localeCompare(b.nombreGenerico)));
        this.cargando.set(false);
      },
      error: (error) => {
        this.cargando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo cargar el catálogo de medicamentos.'));
      },
    });
  }

  opcionRiesgo(m: Medicamento): OpcionRiesgo {
    if (m.altoRiesgoMarcado === null) return 'INTELISIS';
    return m.altoRiesgoMarcado ? 'SI' : 'NO';
  }

  cambiarRiesgo(m: Medicamento, opcion: OpcionRiesgo): void {
    this.guardar(m, opcion === 'INTELISIS' ? null : opcion === 'SI', m.redFria);
  }

  cambiarRedFria(m: Medicamento, redFria: boolean): void {
    this.guardar(m, m.altoRiesgoMarcado, redFria);
  }

  private guardar(m: Medicamento, altoRiesgo: boolean | null, redFria: boolean): void {
    this.guardando.set(m.clave);
    this.error.set('');
    this.farmaciaApi.actualizarSeguridad(m.clave, altoRiesgo, redFria).subscribe({
      next: (actualizado) => {
        this.guardando.set(null);
        this.catalogo.update((lista) => lista.map((item) => (item.clave === actualizado.clave ? actualizado : item)));
        this.seguridad.actualizar(actualizado);
        this.aviso.set(`${actualizado.clave}: ${actualizado.altoRiesgo ? 'alto riesgo' : 'sin alto riesgo'}${actualizado.redFria ? ' · red fría' : ''}.`);
      },
      error: (error) => {
        this.guardando.set(null);
        this.error.set(mensajeDeError(error, 'No se pudo guardar la marca.'));
      },
    });
  }
}
