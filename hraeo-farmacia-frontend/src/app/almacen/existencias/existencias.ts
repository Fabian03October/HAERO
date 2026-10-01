import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { InventarioAlmacen } from '../inventario-almacen';
import { SolicitudesAlmacen, estaVencido, formatoCaducidad } from '../solicitudes-almacen';

interface GrupoLote {
  clave: string;
  medicamento: string;
  lote: string;
  caducidad: string;
  total: number;
  fefo: boolean;
  vencido: boolean;
  loteOrigen?: string;
  ubicaciones: { ubicacion: string; cajas: number }[];
}

// Despacho · Existencias por ubicación (HU12). Cada lote muestra su desglose
// por ubicación física y se marca con FEFO el que debe salir primero (HU15).
@Component({
  selector: 'app-existencias',
  imports: [FormsModule, RouterLink],
  templateUrl: './existencias.html',
  styleUrls: ['../recepcion-almacen/recepcion-comun.css', '../despacho/despacho-comun.css', './existencias.css'],
})
export class Existencias {
  protected readonly inventario = inject(InventarioAlmacen);
  private readonly solicitudes = inject(SolicitudesAlmacen);
  protected readonly formatoCaducidad = formatoCaducidad;

  readonly filtroClave = signal('');
  readonly filtroLote = signal('');
  readonly filtroUbicacion = signal('');

  readonly ubicaciones = computed(() => [...new Set(this.inventario.lotes().map((lote) => lote.ubicacion))].sort());

  readonly grupos = computed(() => {
    const lotes = [...this.inventario.lotes()].sort(
      (a, b) => a.clave.localeCompare(b.clave) || a.caducidad.localeCompare(b.caducidad) || a.lote.localeCompare(b.lote) || a.ubicacion.localeCompare(b.ubicacion),
    );

    // Caducidad FEFO por clave: la más próxima que todavía no vence.
    const fefoPorClave = new Map<string, string>();
    for (const lote of lotes) {
      if (!estaVencido(lote.caducidad) && !fefoPorClave.has(lote.clave)) fefoPorClave.set(lote.clave, lote.caducidad);
    }

    const clave = this.filtroClave().trim().toLowerCase();
    const numeroLote = this.filtroLote().trim().toLowerCase();
    const ubicacion = this.filtroUbicacion();
    const grupos = new Map<string, GrupoLote>();
    for (const lote of lotes) {
      const llave = `${lote.clave}|${lote.lote}`;
      let grupo = grupos.get(llave);
      if (!grupo) {
        grupo = {
          clave: lote.clave,
          medicamento: this.solicitudes.nombreMedicamento(lote.clave),
          lote: lote.lote,
          caducidad: lote.caducidad,
          total: 0,
          fefo: fefoPorClave.get(lote.clave) === lote.caducidad,
          vencido: estaVencido(lote.caducidad),
          loteOrigen: lote.loteOrigen,
          ubicaciones: [],
        };
        grupos.set(llave, grupo);
      }
      // El total es del lote completo aunque se filtre por ubicación (CA1).
      grupo.total += lote.cajas;
      if (!ubicacion || lote.ubicacion === ubicacion) grupo.ubicaciones.push({ ubicacion: lote.ubicacion, cajas: lote.cajas });
    }

    return [...grupos.values()].filter(
      (grupo) =>
        grupo.ubicaciones.length &&
        (!clave || grupo.clave.toLowerCase().includes(clave) || grupo.medicamento.toLowerCase().includes(clave)) &&
        (!numeroLote || grupo.lote.toLowerCase().includes(numeroLote)),
    );
  });

  readonly hayFiltros = computed(() => !!(this.filtroClave() || this.filtroLote() || this.filtroUbicacion()));

  limpiarFiltros(): void {
    this.filtroClave.set('');
    this.filtroLote.set('');
    this.filtroUbicacion.set('');
  }
}
