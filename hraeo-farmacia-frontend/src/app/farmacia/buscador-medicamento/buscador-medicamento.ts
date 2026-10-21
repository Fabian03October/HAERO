import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AlmacenApi, Medicamento } from '../../almacen/almacen-api';

// Medicamentos que se muestran a la vez en la búsqueda.
const RESULTADOS = 8;

// Buscador de claves del catálogo para Farmacia. Solo muestra las claves dadas de alta en
// HAERO (las demás nunca llegan a Farmacia) y emite la que se elige.
@Component({
  selector: 'app-buscador-medicamento',
  imports: [FormsModule],
  templateUrl: './buscador-medicamento.html',
  styleUrl: './buscador-medicamento.css',
})
export class BuscadorMedicamento {
  // Claves que ya no deben ofrecerse (por ejemplo, las que ya están en la receta).
  readonly excluir = input<string[]>([]);
  readonly placeholder = input('Busca por clave o nombre');
  readonly elegido = output<Medicamento>();

  readonly catalogo = signal<Medicamento[]>([]);
  readonly error = signal(false);
  readonly busqueda = signal('');

  readonly resultados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    if (!texto) return [];
    const fuera = new Set(this.excluir());
    return this.catalogo()
      .filter((item) => !fuera.has(item.clave) && `${item.clave} ${item.nombreGenerico}`.toLowerCase().includes(texto))
      .slice(0, RESULTADOS);
  });

  constructor() {
    inject(AlmacenApi).listarMedicamentos().subscribe({
      next: (lista) => this.catalogo.set(lista.filter((item) => item.dadoDeAlta)),
      error: () => this.error.set(true),
    });
  }

  elegir(medicamento: Medicamento): void {
    this.busqueda.set('');
    this.elegido.emit(medicamento);
  }
}
