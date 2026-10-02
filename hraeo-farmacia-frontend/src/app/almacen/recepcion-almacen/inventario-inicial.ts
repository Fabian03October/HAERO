import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import * as XLSX from 'xlsx';
import { InventarioAlmacen, LoteInventario } from '../inventario-almacen';
import { SolicitudesAlmacen, formatoCaducidad } from '../solicitudes-almacen';
import { AlmacenApi, CargaInicialResponse, mensajeDeError } from '../almacen-api';
import { RotacionAlmacen } from '../rotacion-almacen';

interface RenglonInventario extends LoteInventario {
  cpm?: number;
  error?: string;
}

type Campo = 'clave' | 'descripcion' | 'lote' | 'caducidad' | 'cantidad' | 'consumo' | 'ubicacion' | 'fuente' | 'fechaIngreso' | 'proveedor';

type TipoMensaje = 'success' | 'error' | 'info';

// Encabezados reconocidos por columna lógica (normalizados: minúsculas, sin acentos
// ni puntos). Coinciden con la hoja del Drive de Almacén y aceptan variantes.
const ENCABEZADOS: Record<Campo, string[]> = {
  clave: ['clave', 'clave material', 'material', 'codigo', 'codigo material'],
  descripcion: ['descripcion', 'medicamento', 'nombre', 'descripcion del medicamento'],
  lote: ['lote'],
  caducidad: ['caducidad', 'fecha caducidad', 'fecha de caducidad', 'vence', 'vencimiento'],
  cantidad: ['cantidad', 'cajas', 'cantidad en cajas', 'existencia', 'unidades', 'numero de cajas', 'no cajas', 'piezas'],
  consumo: ['consumo prom', 'consumo promedio', 'consumo promedio mensual', 'cpm', 'prom'],
  ubicacion: ['ubicacion', 'ubicacion fisica', 'localizacion', 'sector'],
  fuente: ['fuente', 'fuente de financiamiento', 'origen del recurso'],
  fechaIngreso: ['fecha de ingreso', 'fecha ingreso', 'ingreso', 'fecha de entrada'],
  proveedor: ['proveedor', 'laboratorio'],
};

// El backend también exige el proveedor de cada lote.
const OBLIGATORIOS: Campo[] = ['clave', 'lote', 'caducidad', 'cantidad', 'ubicacion', 'proveedor'];

const NOMBRE_CAMPO: Record<Campo, string> = {
  clave: 'Clave',
  descripcion: 'Descripción',
  lote: 'Lote',
  caducidad: 'Caducidad',
  cantidad: 'Cantidad',
  consumo: 'Consumo prom.',
  ubicacion: 'Ubicación',
  fuente: 'Fuente',
  fechaIngreso: 'Fecha de ingreso',
  proveedor: 'Proveedor',
};

// Mismo orden que la hoja del Drive; Proveedor al final porque la hoja no lo trae.
const ENCABEZADOS_EXCEL = ['CLAVE', 'DESCRIPCION', 'LOTE', 'CADUCIDAD', 'CANTIDAD', 'CONSUMO PROM.', 'UBICACION', 'FUENTE', 'FECHA DE INGRESO', 'PROVEEDOR'];

// Recepción · Inventario (HU21). Es una página hija de Recepción, por eso no
// incluye barra superior ni menú lateral (los pone el contenedor).
// El estado usa signals: la app no usa Zone.js y el archivo se lee de forma
// asíncrona, así la vista previa aparece en cuanto termina la lectura.
@Component({
  selector: 'app-inventario-inicial',
  imports: [FormsModule],
  templateUrl: './inventario-inicial.html',
  styleUrl: './inventario-inicial.css',
})
export class InventarioInicial {
  protected readonly inventario = inject(InventarioAlmacen);
  protected readonly rotacion = inject(RotacionAlmacen);
  private readonly catalogo = inject(SolicitudesAlmacen);
  private readonly api = inject(AlmacenApi);
  protected readonly formatoFecha = formatoCaducidad;
  readonly guardando = signal(false);

  readonly filtro = signal('');
  readonly lotesFiltrados = computed(() => {
    const texto = this.normalizar(this.filtro());
    const lotes = this.inventario.lotes();
    if (!texto) return lotes;
    return lotes.filter((lote) =>
      this.normalizar(`${lote.clave} ${lote.descripcion ?? ''} ${lote.lote} ${lote.ubicacion} ${lote.fuente ?? ''} ${lote.proveedor}`).includes(texto),
    );
  });

  readonly archivoNombre = signal('');
  readonly renglones = signal<RenglonInventario[]>([]);
  readonly mensaje = signal('');
  readonly tipoMensaje = signal<TipoMensaje>('info');
  readonly errores = computed(() => this.renglones().filter((renglon) => renglon.error).length);
  readonly validos = computed(() => this.renglones().length - this.errores());

  seleccionarArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    // Se limpia el input para que al volver a elegir el mismo archivo se dispare otra vez el cambio.
    input.value = '';
    if (!archivo) return;
    this.archivoNombre.set(archivo.name);
    this.renglones.set([]);
    const lector = new FileReader();
    const nombre = archivo.name.toLowerCase();
    if (nombre.endsWith('.csv') || nombre.endsWith('.txt')) {
      lector.onload = () => this.validarContenido(String(lector.result ?? ''));
      lector.readAsText(archivo);
    } else {
      lector.onload = () => this.validarExcel(lector.result as ArrayBuffer);
      lector.readAsArrayBuffer(archivo);
    }
  }

  validarContenido(contenido: string): void {
    const lineas = contenido.split(/\r?\n/).filter((linea) => linea.trim());
    this.validarFilas(lineas.map((linea) => linea.split(',').map((valor) => valor.trim())));
  }

  validarExcel(contenido: ArrayBuffer): void {
    try {
      const libro = XLSX.read(new Uint8Array(contenido), { type: 'array', cellDates: true });
      const hoja = libro.Sheets[libro.SheetNames[0]];
      const filas = XLSX.utils.sheet_to_json<unknown[]>(hoja, { header: 1, blankrows: false, defval: '' }) as unknown[][];
      this.validarFilas(filas);
    } catch {
      this.renglones.set([]);
      this.mostrarMensaje('No se pudo leer el archivo. Verifica que sea un Excel (.xlsx o .xls) válido.', 'error');
    }
  }

  private validarFilas(filas: unknown[][]): void {
    const datos = filas.filter((fila) => fila.some((valor) => String(valor ?? '').trim()));
    if (datos.length < 2) {
      this.renglones.set([]);
      this.mostrarMensaje('El archivo debe contener encabezados y al menos un renglón.', 'error');
      return;
    }
    const encabezados = datos[0].map((valor) => this.normalizar(valor).replace(/[.:]/g, '').replace(/\s+/g, ' ').trim());
    const campos = Object.keys(ENCABEZADOS) as Campo[];
    const indices = {} as Record<Campo, number>;
    campos.forEach((campo) => {
      indices[campo] = encabezados.findIndex((cabecera) => ENCABEZADOS[campo].includes(cabecera));
    });
    const faltantes = OBLIGATORIOS.filter((campo) => indices[campo] < 0);
    if (faltantes.length) {
      this.renglones.set([]);
      this.mostrarMensaje(
        `Al archivo le faltan estas columnas: ${faltantes.map((campo) => NOMBRE_CAMPO[campo]).join(', ')}. Se necesitan al menos Clave, Lote, Caducidad, Cantidad, Ubicación y Proveedor.`,
        'error',
      );
      return;
    }

    // Si el catálogo no se pudo leer, la clave la valida el backend al guardar.
    const claves = new Set(this.catalogo.catalogo().map((medicamento) => medicamento.clave));
    this.renglones.set(
      datos.slice(1).map((fila) => {
        const leer = (campo: Campo) => (indices[campo] < 0 ? '' : this.texto(fila[indices[campo]]));
        const fecha = (campo: Campo) => (indices[campo] < 0 ? '' : this.fecha(fila[indices[campo]]));
        const cajas = Number(leer('cantidad').replace(/[\s,]/g, ''));
        const consumo = Number(leer('consumo').replace(/[\s,]/g, ''));
        const renglon: RenglonInventario = {
          clave: leer('clave'),
          descripcion: leer('descripcion') || undefined,
          lote: leer('lote'),
          caducidad: fecha('caducidad'),
          cajas,
          cpm: Number.isFinite(consumo) && consumo > 0 ? consumo : undefined,
          ubicacion: leer('ubicacion'),
          fuente: leer('fuente') || undefined,
          fechaIngreso: fecha('fechaIngreso') || undefined,
          proveedor: leer('proveedor'),
        };
        const faltan = [
          !renglon.clave && 'clave',
          !renglon.lote && 'lote',
          !renglon.caducidad && 'caducidad',
          !renglon.ubicacion && 'ubicación',
          !renglon.proveedor && 'proveedor',
        ].filter(Boolean);
        if (faltan.length) renglon.error = `Falta ${faltan.join(', ')}`;
        else if (!Number.isInteger(cajas) || cajas <= 0) renglon.error = 'Cantidad no válida';
        else if (claves.size && !claves.has(renglon.clave)) renglon.error = 'Clave no registrada en el catálogo';
        return renglon;
      }),
    );
    this.mostrarMensaje(
      this.errores()
        ? `Revisa la vista previa: ${this.errores()} renglón(es) tienen errores y no se guardarán. Los ${this.validos()} renglón(es) correctos sí se pueden guardar.`
        : `Revisa la vista previa. Si todo está bien, presiona "Guardar datos".`,
      this.errores() ? 'error' : 'info',
    );
  }

  /**
   * Envía los renglones correctos al backend como CSV (clave, lote, caducidad,
   * proveedor, ubicacion, cajas, consumo_promedio). El backend guarda todo o
   * nada: si rechaza algún renglón, se marca en la vista previa y no se guarda
   * ninguno. El consumo promedio (columna opcional) lo guarda el backend en el
   * medicamento y sirve de respaldo del CPM mientras no haya salidas propias (HU16).
   */
  guardarDatos(): void {
    const validos = this.renglones().filter((renglon) => !renglon.error);
    if (!validos.length) {
      this.mostrarMensaje('No hay renglones correctos para guardar. Corrige el archivo y vuelve a subirlo.', 'error');
      return;
    }

    const csv = [
      'clave,lote,caducidad,proveedor,ubicacion,cajas,consumo_promedio',
      ...validos.map((renglon) =>
        [renglon.clave, renglon.lote, fechaCompleta(renglon.caducidad), renglon.proveedor, renglon.ubicacion, renglon.cajas, renglon.cpm ?? '']
          .map(sinComas)
          .join(','),
      ),
    ].join('\n');

    this.guardando.set(true);
    this.api.cargarInventarioInicial(csv, this.archivoNombre().replace(/\.[^.]+$/, '') + '.csv').subscribe({
      next: (respuesta) => {
        this.guardando.set(false);
        this.terminarCarga(respuesta, validos);
      },
      error: (error) => {
        this.guardando.set(false);
        // Con renglones inválidos el backend responde 400 con el detalle de cada error.
        const respuesta = error instanceof HttpErrorResponse ? (error.error as CargaInicialResponse | null) : null;
        if (respuesta && Array.isArray(respuesta.errores)) {
          this.terminarCarga(respuesta, validos);
          return;
        }
        this.mostrarMensaje(mensajeDeError(error, 'No se pudo guardar el inventario.'), 'error');
      },
    });
  }

  private terminarCarga(respuesta: CargaInicialResponse, enviados: RenglonInventario[]): void {
    if (!respuesta.exito) {
      // La fila 2 del CSV es el primer renglón enviado.
      const errores = new Map(respuesta.errores.map((error) => [enviados[error.fila - 2], error.mensaje]));
      this.renglones.set(this.renglones().map((renglon) => (errores.has(renglon) ? { ...renglon, error: errores.get(renglon) } : renglon)));
      this.mostrarMensaje(
        `El servidor rechazó ${respuesta.errores.length} renglón(es) y no guardó nada. Corrígelos en el archivo (o quítalos) y vuelve a guardar.`,
        'error',
      );
      return;
    }

    const omitidos = this.errores();
    this.renglones.set([]);
    this.archivoNombre.set('');
    this.inventario.recargar();
    // El consumo promedio de la hoja ya lo guardó el backend por clave (HU16); refresca el CPM.
    this.rotacion.recargar();
    this.mostrarMensaje(
      `Datos guardados: ${respuesta.lotesCreados} lote(s) registrados en el inventario.` + (omitidos ? ` Se omitieron ${omitidos} renglón(es) con error.` : ''),
      'success',
    );
  }

  // Descarga lo que se ve en la tabla (respeta la búsqueda), con las columnas de la hoja del Drive.
  descargarInventario(): void {
    const filas = this.lotesFiltrados().map((lote) => [
      lote.clave,
      lote.descripcion ?? '',
      lote.lote,
      formatoCaducidad(lote.caducidad),
      lote.cajas,
      this.rotacion.buscar(lote.clave)?.cpm ?? '',
      lote.ubicacion,
      lote.fuente ?? '',
      lote.fechaIngreso ? formatoCaducidad(lote.fechaIngreso) : '',
      lote.proveedor,
    ]);
    this.escribirExcel([ENCABEZADOS_EXCEL, ...filas], `inventario-almacen-${new Date().toLocaleDateString('en-CA')}.xlsx`);
  }

  private escribirExcel(filas: (string | number)[][], nombreArchivo: string): void {
    const hoja = XLSX.utils.aoa_to_sheet(filas);
    hoja['!cols'] = [{ wch: 18 }, { wch: 48 }, { wch: 14 }, { wch: 12 }, { wch: 10 }, { wch: 14 }, { wch: 16 }, { wch: 10 }, { wch: 16 }, { wch: 24 }];
    const libro = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(libro, hoja, 'Inventario');
    XLSX.writeFile(libro, nombreArchivo);
  }

  private texto(valor: unknown): string {
    return String(valor ?? '').trim();
  }

  /**
   * Fechas de la hoja → AAAA-MM-DD (o AAAA-MM si solo trae mes). Acepta celdas de
   * fecha de Excel, texto D/M/AAAA como en el Drive, M/AAAA y AAAA-MM(-DD).
   */
  private fecha(valor: unknown): string {
    const aTexto = (fecha: Date) => fecha.toLocaleDateString('en-CA');
    // Se suman 12 h para evitar que el desfase de zona horaria regrese la fecha al día anterior.
    if (valor instanceof Date) return aTexto(new Date(valor.getTime() + 12 * 60 * 60 * 1000));
    // Número de serie de Excel (días desde 1899-12-30) cuando la celda no tiene formato de fecha.
    if (typeof valor === 'number' && valor > 20000) return aTexto(new Date(Math.round((valor - 25569) * 86400000) + 12 * 60 * 60 * 1000));

    const texto = String(valor ?? '').trim();
    const dosDigitos = (numero: string) => numero.padStart(2, '0');
    const anioCompleto = (anio: string) => (anio.length === 2 ? `20${anio}` : anio);
    let partes = texto.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{2}|\d{4})$/);
    if (partes) {
      const [, dia, mes, anio] = partes;
      if (Number(mes) >= 1 && Number(mes) <= 12 && Number(dia) >= 1 && Number(dia) <= 31) return `${anioCompleto(anio)}-${dosDigitos(mes)}-${dosDigitos(dia)}`;
      return '';
    }
    partes = texto.match(/^(\d{1,2})[/-](\d{4})$/);
    if (partes) return Number(partes[1]) >= 1 && Number(partes[1]) <= 12 ? `${partes[2]}-${dosDigitos(partes[1])}` : '';
    partes = texto.match(/^(\d{4})-(\d{1,2})(?:-(\d{1,2}))?$/);
    if (partes) return partes[3] ? `${partes[1]}-${dosDigitos(partes[2])}-${dosDigitos(partes[3])}` : `${partes[1]}-${dosDigitos(partes[2])}`;
    return '';
  }

  private normalizar(valor: unknown): string {
    return String(valor ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase();
  }

  private mostrarMensaje(texto: string, tipo: TipoMensaje): void {
    this.mensaje.set(texto);
    this.tipoMensaje.set(tipo);
  }
}

// El backend necesita la fecha completa; si la hoja solo trae mes (AAAA-MM) se usa el último día.
function fechaCompleta(fecha: string): string {
  if (fecha.length > 7) return fecha;
  const [anio, mes] = fecha.split('-').map(Number);
  return `${fecha}-${String(new Date(anio, mes, 0).getDate()).padStart(2, '0')}`;
}

// El backend separa las columnas por coma sin comillas: una coma dentro de un valor lo partiría.
function sinComas(valor: string | number): string {
  return String(valor).replace(/,/g, ' ').trim();
}
