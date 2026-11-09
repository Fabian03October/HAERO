import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AlmacenApi, Medicamento } from '../../almacen/almacen-api';
import { MarcaSeguridad } from '../../shared/marca-seguridad/marca-seguridad';

// Medicamentos que se muestran a la vez en la búsqueda.
const RESULTADOS = 8;

// Sin acentos y en minúsculas, para que "acido" encuentre "ÁCIDO".
const normalizar = (texto: string) => texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

// Buscador de claves del catálogo para Farmacia. Por omisión solo muestra las claves dadas de alta
// en HAERO (las que tienen código de caja; las demás no tienen inventario aquí). En el alta inicial
// se muestra todo el catálogo de Intelisis, porque ahí es donde se dan de alta.
@Component({
  selector: 'app-buscador-medicamento',
  imports: [MarcaSeguridad, FormsModule],
  templateUrl: './buscador-medicamento.html',
  styleUrl: './buscador-medicamento.css',
})
export class BuscadorMedicamento {
  // Claves que ya no deben ofrecerse (por ejemplo, las que ya están en la receta).
  readonly excluir = input<string[]>([]);
  readonly placeholder = input('Busca por clave o nombre');
  // false = todo el catálogo de Intelisis, aunque la clave no tenga código ni existencia (alta inicial).
  readonly soloDadasDeAlta = input(true);
  readonly elegido = output<Medicamento>();

  private readonly todas = signal<Medicamento[]>([]);
  readonly catalogo = computed(() => (this.soloDadasDeAlta() ? this.todas().filter((item) => item.dadoDeAlta) : this.todas()));
  readonly error = signal(false);
  readonly busqueda = signal('');

  readonly resultados = computed(() => {
    const texto = normalizar(this.busqueda().trim());
    if (!texto) return [];
    const fuera = new Set(this.excluir());
    return this.catalogo()
      .filter((item) => !fuera.has(item.clave) && normalizar(`${item.clave} ${item.nombreGenerico}`).includes(texto))
      .slice(0, RESULTADOS);
  });

  constructor() {
    inject(AlmacenApi).listarMedicamentos().subscribe({
      next: (lista) => this.todas.set(lista),
      error: () => this.error.set(true),
    });
  }

  elegir(medicamento: Medicamento): void {
    this.busqueda.set('');
    this.elegido.emit(medicamento);
  }
}
