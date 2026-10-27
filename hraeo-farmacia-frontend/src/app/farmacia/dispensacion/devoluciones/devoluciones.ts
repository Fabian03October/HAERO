import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ROL_ETIQUETA, Session } from '../../../auth/session';
import { Topbar } from '../../../shared/topbar/topbar';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { leerGs1 } from '../../../shared/gs1';
import { mensajeDeError } from '../../../almacen/almacen-api';
import { FARMACIA_MENU } from '../../farmacia-nav';
import { Devolucion, EntregaEncontrada as EntregaApi, FarmaciaApi } from '../../farmacia-api';

// Partida de una entrega que se puede devolver (HU34).
interface PartidaEntregada {
  partidaId: number;
  clave: string;
  medicamento: string;
  lote: string;
  entregada: number;
  // Lo que se captura en esta devolución.
  devolver: number;
  danado: boolean;
}

// Entrega encontrada por folio, expediente o lote.
interface EntregaEncontrada {
  id: number;
  folio: string;
  receta: string;
  expediente: string | null;
  paciente: string;
  fecha: string;
  partidas: PartidaEntregada[];
}

// Devolución ya registrada en el día.
interface DevolucionRegistrada {
  folioEntrega: string;
  clave: string;
  lote: string;
  piezas: number;
  danado: boolean;
  hora: string;
}

// Farmacia · Devoluciones (HU34). La entrega se busca por su folio, el de la receta, el
// expediente o el lote; lo devuelto en buen estado regresa al mismo lote y lo dañado queda
// como merma (HU35).
@Component({
  selector: 'app-devoluciones',
  imports: [FormsModule, Topbar, Sidebar],
  templateUrl: './devoluciones.html',
  styleUrls: ['../../panel-farmacia/panel-farmacia.css', '../../farmacia-comun.css'],
})
export class Devoluciones {
  protected readonly session = inject(Session);
  protected readonly rolEtiqueta = ROL_ETIQUETA;
  readonly items = FARMACIA_MENU;

  readonly busqueda = signal('');
  readonly aviso = signal('');
  readonly entrega = signal<EntregaEncontrada | null>(null);
  readonly devolucionesHoy = signal<DevolucionRegistrada[]>([]);
  readonly motivo = signal('');
  private readonly api = inject(FarmaciaApi);
  // Si la búsqueda trae varias entregas (por ejemplo, por expediente), se elige una.
  readonly encontradas = signal<EntregaApi[]>([]);
  readonly buscando = signal(false);
  readonly guardando = signal(false);
  readonly error = signal('');

  constructor() {
    this.cargarDelDia();
  }

  private cargarDelDia(): void {
    this.api.devolucionesDelDia().subscribe({
      next: (lista) => this.devolucionesHoy.set(lista.map((d) => this.aRegistrada(d))),
      error: (error) => this.error.set(mensajeDeError(error, 'No se pudieron cargar las devoluciones de hoy.')),
    });
  }

  readonly partidas = computed(() =>
    (this.entrega()?.partidas ?? []).map((partida) => ({ ...partida, excede: partida.devolver > partida.entregada })),
  );

  readonly faltantes = computed(() => {
    const faltan: string[] = [];
    const partidas = this.partidas();
    if (!partidas.some((partida) => partida.devolver > 0)) faltan.push('las piezas que se devuelven');
    if (partidas.some((partida) => partida.excede)) faltan.push('corregir las partidas con más piezas de las entregadas');
    if (!this.motivo().trim()) faltan.push('el motivo de la devolución');
    return faltan;
  });

  buscar(): void {
    const texto = this.busqueda().trim();
    if (!texto) {
      this.aviso.set('Escribe el folio de la entrega, el expediente o escanea la caja devuelta.');
      return;
    }
    const gs1 = leerGs1(texto);
    if (gs1?.lote) this.busqueda.set(gs1.lote);
    // HU34 CA1: por folio de la entrega, folio de la receta, expediente o lote.
    this.buscando.set(true);
    this.entrega.set(null);
    this.encontradas.set([]);
    this.aviso.set('');
    this.api.buscarEntregas(this.busqueda().trim()).subscribe({
      next: (lista) => {
        this.buscando.set(false);
        if (lista.length === 1) this.elegirEntrega(lista[0]);
        else if (lista.length > 1) {
          this.encontradas.set(lista);
          this.aviso.set(`${lista.length} entregas coinciden; elige la que se devuelve.`);
        } else this.sinEntregas(this.busqueda().trim());
      },
      error: (error) => {
        this.buscando.set(false);
        this.aviso.set(mensajeDeError(error, 'No se pudo buscar la entrega.'));
      },
    });
  }

  // Solo se devuelve lo que Farmacia entregó en HAERO. Si el folio es de una receta de Intelisis
  // que aún no se entrega aquí, se explica en lugar de solo decir que no hay resultados.
  private sinEntregas(texto: string): void {
    const general = `No hay entregas en Farmacia con "${texto}". Busca por el folio de la entrega (E-000123), el folio de la receta, el expediente o el lote.`;
    if (!/^\d{4,9}$/.test(texto)) {
      this.aviso.set(general);
      return;
    }
    this.api.recetaDeIntelisis(texto).subscribe({
      next: (receta) =>
        this.aviso.set(
          `La receta ${receta.folio ?? texto} sí existe en Intelisis, pero todavía no se ha entregado en Farmacia, así que no hay nada que devolver. ` +
            'Primero se registra su entrega en Nueva entrega.',
        ),
      error: () => this.aviso.set(general),
    });
  }

  elegirEntrega(entrega: EntregaApi): void {
    this.encontradas.set([]);
    this.aviso.set('');
    this.motivo.set('');
    this.entrega.set({
      id: entrega.id,
      folio: entrega.folio,
      receta: entrega.receta,
      expediente: entrega.expediente,
      paciente: entrega.paciente,
      fecha: new Date(entrega.fecha).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' }),
      // Solo lo que todavía se puede devolver de cada lote.
      partidas: entrega.partidas.map((p) => ({
        partidaId: p.partidaId,
        clave: p.clave,
        medicamento: p.medicamento,
        lote: p.lote,
        entregada: p.entregada - p.devuelta,
        devolver: 0,
        danado: false,
      })),
    });
  }

  registrar(): void {
    const entrega = this.entrega();
    if (!entrega || this.faltantes().length || this.guardando()) return;
    this.guardando.set(true);
    this.error.set('');
    const partidas = entrega.partidas
      .filter((p) => p.devolver > 0)
      .map((p) => ({ partidaId: p.partidaId, piezas: p.devolver, danado: p.danado }));
    this.api.registrarDevolucion(entrega.id, this.motivo().trim(), partidas).subscribe({
      next: (registradas) => {
        this.guardando.set(false);
        const piezas = registradas.reduce((total, d) => total + d.piezas, 0);
        const posterior = registradas.some((d) => d.enResumen === 'DEVOLUCION');
        this.aviso.set(
          `Devolución de ${piezas} piezas registrada en la entrega ${entrega.folio}.` +
            (posterior ? ' Ese día ya se había dado de baja: aparece como "Devolucion Farmacia" en el resumen de hoy.' : ''),
        );
        this.entrega.set(null);
        this.busqueda.set('');
        this.cargarDelDia();
      },
      error: (error) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo registrar la devolución.'));
      },
    });
  }

  private aRegistrada(devolucion: Devolucion): DevolucionRegistrada {
    return {
      folioEntrega: devolucion.folioEntrega,
      clave: devolucion.clave,
      lote: devolucion.lote,
      piezas: devolucion.piezas,
      danado: devolucion.danado,
      hora: new Date(devolucion.fecha).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', hour12: false }),
    };
  }

  cambiarPartida(indice: number, cambios: Partial<PartidaEntregada>): void {
    this.entrega.update((actual) =>
      actual ? { ...actual, partidas: actual.partidas.map((partida, i) => (i === indice ? { ...partida, ...cambios } : partida)) } : actual,
    );
  }
}
