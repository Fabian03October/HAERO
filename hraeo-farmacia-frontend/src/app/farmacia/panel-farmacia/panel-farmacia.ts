import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ROL_ETIQUETA, Session } from '../../auth/session';
import { Topbar } from '../../shared/topbar/topbar';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { folioSolicitud } from '../../almacen/almacen-api';
import { FARMACIA_MENU } from '../farmacia-nav';
import { FarmaciaApi } from '../farmacia-api';

// Renglones que se muestran en las tablas del Inicio; el resto se ve en su pantalla.
const RENGLONES_EN_INICIO = 5;

// Lo entregado en el día por clave de Intelisis y lote, para la baja por volumen (HU31).
interface RenglonResumen {
  claveIntelisis: string;
  medicamento: string;
  lote: string;
  piezas: number;
  pacientes: number;
  dadoDeBaja: boolean;
}

// Despacho de Almacén que Farmacia aún no recibe (HU26).
interface DespachoPorRecibir {
  folio: string;
  claves: number;
  cajas: number;
  fecha: string;
  medicamentos: string;
}

// Lote de Farmacia en orden de caducidad (HU27, HU30).
interface LoteFarmacia {
  clave: string;
  medicamento: string;
  lote: string;
  caducidad: string;
  piezas: number;
  fefo: boolean;
  porCaducar: boolean;
}

// Día ya entregado que falta dar de baja en Intelisis (HU31).
interface DiaPendiente {
  fecha: string;
  claves: number;
  piezas: number;
  pacientes: number;
}

// Inicio de Farmacia (Iteración 3): resumen del día con el mismo acomodo que el Inicio de Almacén.
// Cada tarjeta se llena con su historia (HU26 a HU32); si una consulta falla, la tarjeta queda vacía.
@Component({
  selector: 'app-panel-farmacia',
  imports: [RouterLink, Topbar, Sidebar],
  templateUrl: './panel-farmacia.html',
  styleUrl: './panel-farmacia.css',
})
export class PanelFarmacia {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;

  readonly items = FARMACIA_MENU;

  readonly resumenDia = signal<RenglonResumen[]>([]);
  readonly despachos = signal<DespachoPorRecibir[]>([]);
  readonly lotes = signal<LoteFarmacia[]>([]);
  readonly diasPendientes = signal<DiaPendiente[]>([]);
  // Resultado de la verificación del día anterior contra Intelisis (HU32); null mientras no exista.
  readonly verificacionAyer = signal<'CUADRA' | 'DIFERENCIA' | null>(null);
  // Lo entregado hoy (HU28): pacientes atendidos, piezas y recetas que quedaron parciales.
  readonly entregasHoy = signal<{ entregas: number; pacientes: number; parciales: number } | null>(null);
  private readonly api = inject(FarmaciaApi);

  constructor() {
    const hoy = this.diaIso(new Date());
    this.api.corte(hoy).subscribe({
      next: (corte) => {
        const dadoDeBaja = corte.estado !== 'PENDIENTE';
        this.resumenDia.set(corte.consumo.map((r) => ({ ...r, dadoDeBaja })));
        this.api.recetas().subscribe({
          next: (recetas) =>
            this.entregasHoy.set({
              entregas: corte.totalPiezas,
              pacientes: corte.totalPacientes,
              parciales: recetas.filter((r) => r.estado === 'PARCIAL').length,
            }),
        });
        const primero = corte.diasPendientes[0];
        if (primero) {
          this.api.corte(primero).subscribe((dia) =>
            this.diasPendientes.set([{ fecha: this.fechaCorta(primero), claves: dia.totalClaves, piezas: dia.totalPiezas, pacientes: dia.totalPacientes }]),
          );
        }
      },
    });
    const ayer = new Date();
    ayer.setDate(ayer.getDate() - 1);
    this.api.corte(this.diaIso(ayer)).subscribe((corte) =>
      this.verificacionAyer.set(corte.estado === 'VERIFICADO' ? 'CUADRA' : null),
    );
    this.api.porRecibir().subscribe((lista) =>
      this.despachos.set(
        lista.map((d) => ({
          folio: folioSolicitud(d.solicitudId),
          claves: 1,
          cajas: d.cajasDespachadas,
          fecha: new Date(d.partidas[0]?.fechaDespacho ?? d.fechaSolicitud).toLocaleDateString('es-MX'),
          medicamentos: d.medicamento,
        })),
      ),
    );
    this.api.existencias().subscribe((lotes) =>
      this.lotes.set(
        lotes
          .filter((l) => l.estado !== 'VENCIDO')
          .sort((a, b) => a.caducidad.localeCompare(b.caducidad))
          .map((l) => ({ clave: l.clave, medicamento: l.medicamento, lote: l.lote, caducidad: l.caducidad, piezas: l.piezas, fefo: l.estado === 'FEFO', porCaducar: l.porCaducar })),
      ),
    );
  }

  private diaIso(fecha: Date): string {
    return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
  }

  private fechaCorta(dia: string): string {
    const [anio, mes, d] = dia.split('-');
    return `${d}/${mes}/${anio}`;
  }

  readonly fechaHoy = (() => {
    const fecha = new Date().toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    return fecha.charAt(0).toUpperCase() + fecha.slice(1);
  })();

  readonly saludo = (() => {
    const hora = new Date().getHours();
    if (hora < 12) return 'Buenos días';
    if (hora < 19) return 'Buenas tardes';
    return 'Buenas noches';
  })();

  readonly primerNombre = computed(() => (this.session.usuarioActual()?.nombreCompleto ?? '').split(' ')[0]);

  readonly resumenEnInicio = computed(() => this.resumenDia().slice(0, RENGLONES_EN_INICIO));
  readonly lotesEnInicio = computed(() => this.lotes().slice(0, RENGLONES_EN_INICIO));
  readonly despachosEnInicio = computed(() => this.despachos().slice(0, RENGLONES_EN_INICIO));
  readonly diaPendiente = computed(() => this.diasPendientes()[0] ?? null);

  readonly indicadores = computed(() => {
    const entregas = this.entregasHoy();
    const lotes = this.lotes();
    return [
      {
        titulo: 'Pacientes atendidos hoy',
        historia: 'HU28',
        valor: entregas?.pacientes ?? null,
        detalle: entregas ? `${entregas.entregas.toLocaleString('es-MX')} piezas · ${entregas.parciales} recetas parciales` : 'Se llena con la dispensación',
        ruta: '/farmacia/dispensacion/nueva',
        tono: 'info',
      },
      {
        titulo: 'Despachos por recibir',
        historia: 'HU26',
        valor: this.despachos().length || null,
        detalle: 'Despachados por Almacén sin recibir',
        ruta: '/farmacia/inventario/recepcion',
        tono: 'warning',
      },
      {
        titulo: 'Piezas en Farmacia',
        historia: 'HU27',
        valor: lotes.length ? lotes.reduce((total, lote) => total + lote.piezas, 0) : null,
        detalle: lotes.length ? `${lotes.length} lotes · ${new Set(lotes.map((lote) => lote.clave)).size} claves` : 'Se llena con el alta inicial y la recepción',
        ruta: '/farmacia/inventario/existencias',
        tono: 'success',
      },
      {
        titulo: 'Lotes por caducar',
        historia: 'HU27',
        valor: lotes.length ? lotes.filter((lote) => lote.porCaducar).length : null,
        detalle: 'En los próximos 9 meses',
        ruta: '/farmacia/inventario/existencias',
        tono: 'danger',
      },
    ];
  });

  numero(valor: number | null): string {
    return valor === null ? '—' : valor.toLocaleString('es-MX');
  }
}
