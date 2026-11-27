import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Session } from '../../../auth/session';
import { MarcaSeguridad } from '../../../shared/marca-seguridad/marca-seguridad';
import { SeguridadMedicamentos as MarcasSeguridad } from '../../../shared/seguridad-medicamentos';
import { AlmacenApi, Medicamento, mensajeDeError } from '../../almacen-api';
import { Paginacion, Paginador } from '../../../shared/paginador/paginador';

type Filtro = 'ALTO' | 'FRIA' | 'DIFERENTE' | 'TODAS';
type OpcionRiesgo = 'INTELISIS' | 'SI' | 'NO';


// Almacén · Seguridad del paciente: qué claves son de alto riesgo (lista ISMP: electrolitos
// concentrados, quimioterapia, citotóxicos…) y cuáles son de red fría. Parte de lo que dice
// Intelisis (riesgo = ALTO) y Almacén lo corrige clave por clave. Las etiquetas se ven en todas
// las pantallas donde aparece la clave (Farmacia las ve al surtir).
@Component({
  selector: 'app-seguridad-medicamentos',
  imports: [FormsModule, MarcaSeguridad, Paginador],
  templateUrl: './seguridad-medicamentos.html',
  styleUrls: ['../../../farmacia/panel-farmacia/panel-farmacia.css', '../../../farmacia/farmacia-comun.css', './seguridad-medicamentos.css'],
})
export class SeguridadMedicamentos {
  // Por páginas: el catálogo son casi mil claves.
  readonly pagClaves = new Paginacion(() => this.filtradas());

  private readonly session = inject(Session);
  private readonly api = inject(AlmacenApi);
  private readonly seguridad = inject(MarcasSeguridad);
  readonly puedeEditar = computed(() => this.session.usuarioActual()?.rol === 'ALMACEN');

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
      { valor: 'DIFERENTE' as Filtro, etiqueta: 'Corregidas en SICOMED', total: lista.filter((m) => m.altoRiesgoMarcado !== null).length },
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
      });
  });

  constructor() {
    this.api.listarMedicamentos().subscribe({
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
    this.api.actualizarSeguridad(m.clave, altoRiesgo, redFria).subscribe({
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
