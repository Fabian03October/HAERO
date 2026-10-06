import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { SolicitudesAlmacen } from '../../solicitudes-almacen';
import { AlmacenApi, CrearMedicamentoRequest, mensajeDeError } from '../../almacen-api';
import { RenglonCatalogo, descargarPlantilla, interpretarFilas, leerFilas, revalidar } from './carga-catalogo';
import { Notificaciones } from '../../../shared/notificaciones/notificaciones';

// Altas simultáneas al guardar la carga masiva (no saturar el servidor).
const ALTAS_EN_PARALELO = 4;

type CampoEditable = 'clave' | 'nombreGenerico' | 'presentacion' | 'piezasPorCaja';

// Recepción · Catálogo de medicamentos. Pedidos, entradas, carga inicial y
// solicitudes de Farmacia solo aceptan claves que estén dadas de alta aquí.
// Se pueden dar de alta una por una o en bloque desde un Excel.
@Component({
  selector: 'app-catalogo-medicamentos',
  imports: [FormsModule],
  templateUrl: './catalogo-medicamentos.html',
  styleUrls: ['../recepcion-comun.css', '../pedidos/pedidos-recepcion.css', '../inventario-inicial.css', './catalogo-medicamentos.css'],
})
export class CatalogoMedicamentos {
  protected readonly datos = inject(SolicitudesAlmacen);
  private readonly notificaciones = inject(Notificaciones);
  private readonly api = inject(AlmacenApi);
  protected readonly descargarPlantilla = descargarPlantilla;

  // Signals: los mensajes cambian dentro de respuestas del backend (la app no usa Zone.js).
  readonly mensaje = signal('');
  readonly tipoMensaje = signal<'success' | 'error' | 'info'>('success');
  readonly guardando = signal(false);
  nuevo: CrearMedicamentoRequest = this.vacio();

  readonly filtro = signal('');
  readonly medicamentos = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    const lista = [...this.datos.catalogo()].sort((a, b) => a.clave.localeCompare(b.clave));
    if (!texto) return lista;
    return lista.filter((item) => `${item.clave} ${item.nombreGenerico} ${item.presentacion}`.toLowerCase().includes(texto));
  });

  // ---------- Carga masiva desde Excel ----------
  readonly archivoNombre = signal('');
  readonly renglones = signal<RenglonCatalogo[]>([]);
  private readonly originales = signal<RenglonCatalogo[]>([]);
  readonly hayCambios = computed(() => JSON.stringify(this.renglones()) !== JSON.stringify(this.originales()));
  readonly porGuardar = computed(() => this.renglones().filter((renglon) => !renglon.error && !renglon.existente && renglon.estado !== 'guardado'));
  readonly conError = computed(() => this.renglones().filter((renglon) => renglon.error).length);
  readonly existentes = computed(() => this.renglones().filter((renglon) => renglon.existente).length);
  readonly progreso = signal({ hechos: 0, total: 0 });

  registrar(): void {
    const datos: CrearMedicamentoRequest = {
      clave: this.nuevo.clave.trim(),
      nombreGenerico: this.nuevo.nombreGenerico.trim(),
      presentacion: this.nuevo.presentacion.trim(),
      piezasPorCaja: this.nuevo.piezasPorCaja ? Number(this.nuevo.piezasPorCaja) : null,
      descripcion: this.nuevo.descripcion?.trim() || null,
    };
    if (!datos.clave || !datos.nombreGenerico || !datos.presentacion) {
      this.mostrarMensaje('Completa clave, nombre genérico y presentación.', 'error');
      return;
    }
    if (datos.piezasPorCaja !== null && (!Number.isInteger(datos.piezasPorCaja) || datos.piezasPorCaja! <= 0)) {
      this.mostrarMensaje('Las piezas por caja deben ser un número entero mayor a cero.', 'error');
      return;
    }

    this.guardando.set(true);
    this.api.crearMedicamento(datos).subscribe({
      next: (medicamento) => {
        this.guardando.set(false);
        this.mostrarMensaje(`Medicamento ${medicamento.clave} · ${medicamento.nombreGenerico} agregado al catálogo.`, 'success', 'Medicamento agregado');
        this.nuevo = this.vacio();
        this.datos.recargar();
      },
      error: (error) => {
        this.guardando.set(false);
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo registrar el medicamento.'), 'error');
      },
    });
  }

  seleccionarArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    // Se limpia el input para que al volver a elegir el mismo archivo se dispare otra vez el cambio.
    input.value = '';
    if (!archivo) return;
    const esTexto = /\.(csv|txt)$/i.test(archivo.name);
    const lector = new FileReader();
    lector.onload = () => {
      try {
        const resultado = interpretarFilas(leerFilas(esTexto ? String(lector.result ?? '') : (lector.result as ArrayBuffer)), this.clavesCatalogo());
        if ('error' in resultado) {
          this.descartar();
          this.mostrarMensaje(resultado.error, 'error');
          return;
        }
        this.archivoNombre.set(archivo.name);
        this.renglones.set(resultado.renglones);
        this.originales.set(resultado.renglones.map((renglon) => ({ ...renglon })));
        this.avisarRevision();
      } catch {
        this.descartar();
        this.mostrarMensaje('No se pudo leer el archivo. Verifica que sea un Excel (.xlsx o .xls) o un CSV válido.', 'error');
      }
    };
    if (esTexto) lector.readAsText(archivo);
    else lector.readAsArrayBuffer(archivo);
  }

  /** Corrige un dato de un renglón en la vista previa y vuelve a validar todo (por las claves repetidas). */
  corregir(id: number, campo: CampoEditable, valor: string): void {
    const texto = String(valor ?? '').trim();
    const renglones = this.renglones().map((renglon) => {
      if (renglon.id !== id) return renglon;
      if (campo === 'piezasPorCaja') return { ...renglon, piezasPorCaja: texto ? Number(texto) : null };
      return { ...renglon, [campo]: texto };
    });
    this.renglones.set(revalidar(renglones, this.clavesCatalogo()));
    this.avisarRevision();
  }

  quitarRenglon(id: number): void {
    const renglones = this.renglones().filter((renglon) => renglon.id !== id);
    if (!renglones.length) {
      this.descartar();
      return;
    }
    this.renglones.set(revalidar(renglones, this.clavesCatalogo()));
    this.avisarRevision();
  }

  restaurar(): void {
    this.renglones.set(revalidar(this.originales(), this.clavesCatalogo()));
    this.avisarRevision();
  }

  descartar(): void {
    this.renglones.set([]);
    this.originales.set([]);
    this.archivoNombre.set('');
    this.mostrarMensaje('', 'info');
  }

  /**
   * Da de alta los renglones correctos que aún no están en el catálogo, unos
   * cuantos a la vez. Cada alta es independiente: si alguna falla, las demás se
   * guardan igual y la que falló queda marcada para corregirla y reintentar.
   */
  async guardarCarga(): Promise<void> {
    const pendientes = this.porGuardar();
    if (!pendientes.length || this.guardando()) return;
    this.guardando.set(true);
    this.progreso.set({ hechos: 0, total: pendientes.length });
    let guardados = 0;
    let fallidos = 0;

    const cola = [...pendientes];
    const trabajar = async () => {
      for (let renglon = cola.shift(); renglon; renglon = cola.shift()) {
        const { clave, nombreGenerico, presentacion, piezasPorCaja, descripcion } = renglon;
        try {
          await firstValueFrom(this.api.crearMedicamento({ clave, nombreGenerico, presentacion, piezasPorCaja, descripcion }));
          guardados++;
          this.actualizarRenglon(renglon.id, { estado: 'guardado', error: undefined });
        } catch (error) {
          // 409: otra persona la dio de alta mientras tanto; se toma como existente.
          if (error instanceof HttpErrorResponse && error.status === 409) {
            this.actualizarRenglon(renglon.id, { existente: true });
          } else {
            fallidos++;
            this.actualizarRenglon(renglon.id, { estado: 'fallo', error: mensajeDeError(error, 'No se pudo dar de alta') });
          }
        }
        this.progreso.update((actual) => ({ ...actual, hechos: actual.hechos + 1 }));
      }
    };
    await Promise.all(Array.from({ length: ALTAS_EN_PARALELO }, trabajar));

    this.guardando.set(false);
    this.datos.recargar();
    if (!fallidos) {
      const omitidas = this.existentes();
      this.descartar();
      this.mostrarMensaje(
        `Catálogo actualizado: ${guardados} medicamento(s) dado(s) de alta.` + (omitidas ? ` ${omitidas} clave(s) ya existían y se omitieron.` : ''),
        'success', 'Catálogo actualizado'
      );
    } else {
      this.mostrarMensaje(`Se dieron de alta ${guardados} medicamento(s); ${fallidos} no se pudieron guardar. Corrígelos en la tabla y vuelve a guardar.`, 'error');
    }
  }

  private actualizarRenglon(id: number, cambios: Partial<RenglonCatalogo>): void {
    this.renglones.update((renglones) => renglones.map((renglon) => (renglon.id === id ? { ...renglon, ...cambios } : renglon)));
  }

  private clavesCatalogo(): string[] {
    return this.datos.catalogo().map((medicamento) => medicamento.clave);
  }

  private avisarRevision(): void {
    const partes = [`${this.porGuardar().length} por dar de alta`];
    if (this.existentes()) partes.push(`${this.existentes()} ya existen y se omitirán`);
    if (this.conError()) partes.push(`${this.conError()} con error (corrígelos en la tabla o quítalos)`);
    this.mostrarMensaje(`Revisa la vista previa: ${partes.join(', ')}.`, this.conError() ? 'error' : 'info');
  }

  private mostrarMensaje(texto: string, tipo: 'success' | 'error' | 'info', titulo = 'Listo'): void {
    // Lo que sí se hizo se avisa con un popup; errores e indicaciones se quedan junto al formulario.
    if (tipo === 'success') {
      this.mensaje.set('');
      this.notificaciones.exito(titulo, texto);
      return;
    }
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }

  private vacio(): CrearMedicamentoRequest {
    return { clave: '', nombreGenerico: '', presentacion: '', piezasPorCaja: null, descripcion: '' };
  }
}
