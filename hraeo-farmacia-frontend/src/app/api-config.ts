// Dirección del backend.
// - Desde la misma computadora (localhost) se llama directo a http://localhost:8080,
//   como siempre; funciona con cualquier `ng serve`.
// - Desde otro dispositivo (por ejemplo un celular en la misma red) se usa /api, que
//   `ng serve` reenvía al backend de la computadora (ver proxy.conf.json). Para eso
//   el servidor se arranca con `npm run start:red`.
const enEstaComputadora = ['localhost', '127.0.0.1'].includes(globalThis.location?.hostname ?? 'localhost');

export const API_BASE_URL = enEstaComputadora ? 'http://localhost:8080/api' : '/api';
