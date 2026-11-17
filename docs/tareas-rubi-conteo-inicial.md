# Pantallas del conteo inicial (Farmacia primero)

**Para:** Rubi · **De:** Fabián · **Backend:** listo en `farmacia-backend` (migración V41)

## Qué cambia

El inventario inicial ya no se da de alta caja por caja. Ahora se hace en tres pasos:

1. **Registrar códigos.** Fabián y Rubi escanean cada caja y ponen su lote (y el estante, si quieren), **sin cantidades**.
2. **Capturar cantidades.** El personal capacitado escribe cuántas piezas hay de cada renglón. Pueden trabajar varias personas a la vez; todo se guarda en el servidor al momento.
3. **Guardar el inventario inicial.** Se da de alta todo de una vez. Es **una sola vez**: después ya no se puede contar ni dar de alta inicial, y **la pestaña debe desaparecer del menú**.

Se empieza con **Farmacia**. Almacén va después y usa las mismas rutas.

## Rutas de Farmacia

Base: `/api/farmacia/conteo-inicial`. Almacén es igual, con `/api/almacen/conteo-inicial`.

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /` | — | `Estado`: `{ area, cerrado, cerradoEn, cerradoPor, totalRenglones, sinCantidad, renglones: Renglon[] }` |
| `POST /renglones` | `{ codigo, clave?, lote, caducidad, ubicacion? }` | `201` con el `Renglon` |
| `PUT /renglones/{id}/cantidad` | `{ cantidad }` | `200` con el `Renglon` |
| `DELETE /renglones/{id}` | — | `204` |
| `POST /cerrar` | — | `200` con el `Estado` (ya con `cerrado: true`) |

`Renglon`: `{ id, codigo, clave, medicamento, piezasPorCaja, lote, caducidad, ubicacion, cantidad, registradoPor, registradoEn, contadoPor, contadoEn }`.
- En Farmacia, `cantidad` son **piezas**; en Almacén son **cajas**.
- `cantidad: null` quiere decir que todavía no se cuenta; `0` quiere decir que no había nada.

Errores (el texto viene en `mensaje`, como en el resto del sistema):

| Código | Cuándo | Qué hacer en la pantalla |
|---|---|---|
| `404` en `POST /renglones` | El código es nuevo | Abrir el buscador de clave (`BuscadorMedicamento`) y volver a mandar el mismo renglón con `clave` |
| `400` | Faltan datos, el lote ya está con otra caducidad, cantidad negativa, o al guardar faltan cantidades | Mostrar el mensaje junto al formulario |
| `409` | El inventario inicial ya se guardó | Mostrar el mensaje y mandar a Existencias |
| `403` | El usuario no es de Farmacia | — |

Escanear otra vez el mismo lote en el mismo estante **no lo duplica**: regresa el mismo renglón. El mismo lote en otro estante sí queda como otro renglón, y al guardar se suman.

## Pantallas

Propuesta: una sola página `/farmacia/inventario/conteo-inicial` con tres pestañas.

### 1. Registrar códigos
- Campo de escaneo siempre enfocado, como en Quimioterapia, con escaneo continuo y `pitido.ts`.
- Leer el código con `leerGs1()` (`shared/gs1.ts`):
  - Si es GS1, mandar `codigo = gtin`, `lote` y `caducidad` tal como vienen y **no dejar editarlos**.
  - Si no es GS1, mandar el código tal cual y pedir lote y caducidad (mes y año, con `ultimoDiaDelMes()`).
- Campo **Estante** opcional que se queda puesto entre caja y caja (como "mantener ubicación" en Almacén).
- Abajo, la lista de lo registrado, lo más nuevo arriba, con un botón **Quitar** (`DELETE`).

### 2. Capturar cantidades
- Tabla con estante, clave, medicamento, lote, caducidad y **Piezas**, con filtro por estante y búsqueda por clave, medicamento o lote.
- Cada renglón se guarda al dar **Enter** o salir del campo (`PUT`), y el cursor pasa al siguiente renglón. Que funcione rápido sin mouse.
- Mostrar quién contó cada renglón (`contadoPor`) y resaltar los que faltan (`cantidad === null`).
- Recargar con `GET` cada ~10 s, o con un botón, para ver lo que capturan los demás. No pisar el campo que el usuario está escribiendo.

### 3. Revisar y guardar
- Totales: renglones, claves, piezas y **faltan N** (`sinCantidad`).
- Avisar los lotes que están en varios estantes (se suman) y los renglones con 0 (no se dan de alta).
- Botón **Guardar inventario inicial**, deshabilitado mientras `sinCantidad > 0`.
- **Confirmación dentro de la página** (no `confirm()`), porque no se puede deshacer: "Se darán de alta N lotes con X piezas. Después ya no se podrá volver a hacer el inventario inicial."
- Si falla, el mensaje dice qué lote falló; no se guarda nada y se puede corregir y volver a intentar.

### 4. Ocultar del menú cuando ya se guardó
- En `farmacia-nav.ts`, quitar la entrada cuando `GET /api/farmacia/conteo-inicial` regrese `cerrado: true`. Hoy esa entrada es "Alta inicial" (`/farmacia/inventario/alta-inicial`).
- También **un guard en la ruta**, para que entrar escribiendo la URL mande a Existencias.
- El backend ya rechaza todo con `409` después de guardar, incluida la pantalla vieja `/alta-inicial`. Ocultarla es para que nadie la vea.

## Decidir juntos
- **Pantalla vieja "Alta inicial" de Farmacia:** propongo **reemplazarla** por el conteo y que el menú diga "Inventario inicial". Mientras el conteo esté abierto, la vieja sigue funcionando, y si se usan las dos se puede dar de alta un lote por dos lados.
- **"Enlazar código" desde Existencias** (lo que agregaste): no choca. Después de guardar el inventario inicial sigue sirviendo.

## Después (Almacén)
Es la misma página con `/api/almacen/conteo-inicial`, con estas diferencias:
- **Ubicación (anaquel) obligatoria.**
- El lote tiene que estar en la existencia de Intelisis. Para elegirlo, usar `GET /api/almacen/registros-intelisis?clave=…&tipo=ALTA_INICIAL`, que ya usa `captura-intelisis.ts`. La caducidad la pone el backend.
- La cantidad es en **cajas**.
- Al guardar Almacén, los lotes de Farmacia se ligan solos con su lote de Almacén para la trazabilidad.

## Para probar
- Usuario de Farmacia en una copia de la base, **nunca en la de producción**: al guardar, el área queda cerrada para siempre.
- Para volver a probar en una copia: `DELETE FROM conteo_inicial WHERE area = 'FARMACIA'` (y borrar los renglones si quieres empezar de cero).
