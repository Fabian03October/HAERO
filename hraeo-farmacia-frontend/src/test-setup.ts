// Node 22 o superior trae su propio `localStorage`, que sin --localstorage-file queda
// vacío (undefined) y tapa al del entorno de pruebas. Aquí se pone uno en memoria para
// que Session y las pruebas que lo usan funcionen igual que en el navegador.
class AlmacenEnMemoria implements Storage {
  private datos = new Map<string, string>();
  get length(): number {
    return this.datos.size;
  }
  clear(): void {
    this.datos.clear();
  }
  getItem(llave: string): string | null {
    return this.datos.has(llave) ? this.datos.get(llave)! : null;
  }
  key(indice: number): string | null {
    return [...this.datos.keys()][indice] ?? null;
  }
  removeItem(llave: string): void {
    this.datos.delete(llave);
  }
  setItem(llave: string, valor: string): void {
    this.datos.set(llave, String(valor));
  }
}

if (typeof globalThis.localStorage?.clear !== 'function') {
  Object.defineProperty(globalThis, 'localStorage', { value: new AlmacenEnMemoria(), configurable: true, writable: true });
}
