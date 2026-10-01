0Iteración 2 — Módulo de Almacén de Medicamentos (HRAEO)
3.3 Iteración 2 — Módulo de Almacén de Medicamentos
La Iteración 2 comprende el desarrollo de las historias de usuario HU10 a HU20,
correspondientes al módulo de Almacén de Medicamentos del sistema web para el control de
almacén y dispensación del Servicio de Farmacia Hospitalaria del Hospital Regional de Alta
Especialidad de Oaxaca (HRAEO). Estas historias cubren la recepción de medicamento con
lector de código de barras, el control de existencias por ubicación, la atención de solicitudes de
Farmacia con bloqueo FEFO, el cálculo automático del consumo promedio mensual (CPM), las
alertas de stock y los movimientos especiales (canjes, caducados, préstamos y transferencias).
Historias de usuario
Historia de usuario
Número: HU10 Usuario: DESPACHADOR DE ALMACÉN
Nombre de la historia: Recepción de medicamento Dependencia para su desarrollo: HU01 (acceso al
en Almacén módulo de Almacén)
Prioridad en el negocio: Alta Riesgo en desarrollo: Medio
Puntos asignados: 5 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como despachador de Almacén, quiero registrar la entrada de un lote de medicamento confirmando con un
disparo de la pistola lectora de código de barras los datos que el sistema ya tiene precargados del pedido (clave,
proveedor y cantidad esperada), y completando solo número de lote, caducidad, cantidad recibida en cajas y
ubicación física, para llevar el control del inventario que hoy se hace en un Excel/Drive compartido. En esta iteración
la validación automática contra el sistema de Contratos queda fuera de alcance y se contempla para una iteración
posterior.
Criterios de
aceptación
CA1 El despachador puede registrar una entrada escaneando el código de barras de la caja; el
sistema identifica la clave y el proveedor y precarga los datos del pedido, y el despachador
confirma lote, cantidad en cajas, caducidad y una o varias ubicaciones donde se coloca el
lote.
CA2 El sistema no permite guardar una entrada sin los campos obligatorios (clave, lote,
cantidad, caducidad).

CA3 La cantidad se maneja en la presentación de compra (caja) tal como llega, no en unidades
sueltas ni en unidosis.
CA4 Cada entrada queda asociada al usuario que la registró y a la fecha y hora del registro.
CA5 El código de barras del lote, si se captura, se guarda asociado a ese lote específico y no a
la clave del medicamento, ya que una misma clave puede tener hasta cinco proveedores
distintos, cada uno con su propio código.
Nota: HU10 se actualizó conforme a la observación de que el despachador solo confirma los datos precargados con
un disparo de la pistola lectora.
Historia de usuario
Número: HU11 Usuario: ENCARGADA / DESPACHADOR DE
ALMACÉN
Nombre de la historia: Marcar un pedido como Dependencia para su desarrollo: HU10
cancelado
Prioridad en el negocio: Media Riesgo en desarrollo: Bajo
Puntos asignados: 2 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como encargada de Almacén, quiero marcar manualmente un pedido como cancelado cuando el nivel
central (FONSABI) me notifique que fue cancelado, para evitar que el despachador intente recibirlo por error. El
aviso de cancelación se recibe hoy por fuera del sistema (correo o llamada); no se contempla en esta iteración una
integración automática con la plataforma central.
Criterios de aceptación
CA1 Un usuario de Almacén con permiso puede cambiar
el estatus de un pedido registrado a “cancelado”,
indicando el motivo.
CA2 Un pedido marcado como cancelado no puede
seleccionarse al registrar una entrada (HU10).
CA3 Si se intenta recibir un pedido cancelado, el sistema
muestra un aviso y no permite continuar.
CA4 El cambio de estatus queda registrado con fecha,
hora y usuario que lo realizó.
Historia de usuario
Número: HU12 Usuario: DESPACHADOR DE ALMACÉN

Nombre de la historia: Consulta y control de Dependencia para su desarrollo: HU10
existencias por ubicación
Prioridad en el negocio: Alta Riesgo en desarrollo: Medio
Puntos asignados: 3 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como despachador de Almacén, quiero consultar cuánto medicamento hay y en qué ubicaciones físicas
(sector) está repartido cada lote, para saber exactamente de dónde tomarlo al despachar, replicando el control que
hoy llevan de forma manual porque el espacio es reducido y un mismo medicamento puede estar en varias
ubicaciones a la vez.
Criterios de aceptación
CA1 La consulta de existencias muestra, para cada lote,
el desglose de cantidad por ubicación y no solo el
total general.
CA2 Un mismo lote puede tener existencia registrada en
más de una ubicación de forma simultánea.
CA3 Al registrar una salida (HU14), el descuento se
aplica sobre la ubicación específica elegida y no
sobre el total del lote.
CA4 La consulta permite filtrar por clave, lote o ubicación.
Historia de usuario
Número: HU13 Usuario: PERSONAL DE FARMACIA
Nombre de la historia: Solicitud de medicamento de Dependencia para su desarrollo: HU02 (acceso al
Farmacia a Almacén módulo de Farmacia)
Prioridad en el negocio: Alta Riesgo en desarrollo: Bajo
Puntos asignados: 3 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como personal de Farmacia, quiero generar una solicitud de medicamento indicando la clave y la cantidad
que necesito, para que Almacén la reciba precargada en el sistema y no tenga que volver a capturar la información,
evitando el problema actual donde se despachan medicamentos que Almacén no ha movido formalmente en su
propio control.
Criterios de aceptación

CA1 Farmacia puede generar una solicitud capturando
clave y cantidad solicitada; el sistema registra
automáticamente fecha y quién la generó.
CA2 La solicitud queda visible para Almacén con estatus
“pendiente” hasta que sea atendida.
CA3 Farmacia puede consultar el estatus de sus
solicitudes (pendiente, atendida).
CA4 Una solicitud no modifica el inventario de Almacén
por sí sola; el descuento ocurre hasta que Almacén
la valida (HU14).
Historia de usuario
Número: HU14 Usuario: DESPACHADOR DE ALMACÉN
Nombre de la historia: Validación y despacho de Dependencia para su desarrollo: HU12, HU13
solicitud por Almacén
Prioridad en el negocio: Alta Riesgo en desarrollo: Alto
Puntos asignados: 5 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como despachador de Almacén, quiero validar una solicitud de Farmacia eligiendo el lote y la ubicación de
donde se va a despachar, para confirmar la salida sin que Farmacia tenga que indicar el lote, y evitar el problema
actual donde las bajas de Farmacia no coinciden con lo que Almacén realmente tiene registrado.
Criterios de aceptación
CA1 El despachador ve la lista de solicitudes pendientes
y, al abrir una, el sistema sugiere el lote y ubicación
con la caducidad más próxima disponible para esa
clave.
CA2 El despachador puede confirmar el lote y ubicación
sugeridos o, si es necesario, elegir otro lote
disponible de la misma clave, respetando la regla de
HU15.
CA3 Al confirmar el despacho, se descuenta la cantidad
exacta de la ubicación elegida y la solicitud cambia a
estatus “atendida”.
CA4 La cantidad despachada no puede ser mayor a la
existencia disponible en el lote y ubicación
seleccionados.

CA5 El movimiento de salida queda registrado con fecha,
usuario, lote, cantidad y ubicación de origen.
Historia de usuario
Número: HU15 Usuario: DESPACHADOR DE ALMACÉN
Nombre de la historia: Bloqueo de despacho fuera Dependencia para su desarrollo: HU14
de orden de caducidad (FEFO)
Prioridad en el negocio: Alta Riesgo en desarrollo: Alto
Puntos asignados: 5 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como despachador de Almacén, quiero que el sistema me impida despachar un lote de caducidad más
lejana mientras exista stock de un lote con caducidad más próxima de la misma clave, para cumplir con la política de
primeras caducidades, primeras salidas (FEFO) y evitar que se venzan medicamentos por un mal manejo del orden
de salida.
Criterios de aceptación
CA1 Al validar una solicitud (HU14), el sistema no permite
seleccionar un lote si existe otro lote de la misma
clave con caducidad más próxima y con existencia
disponible en alguna ubicación.
CA2 El bloqueo aplica incluso si el despachador intenta
forzar la selección manualmente.
CA3 Si dos lotes tienen la misma fecha de caducidad, el
sistema permite elegir cualquiera de los dos.
CA4 El sistema muestra claramente cuál es el lote de
caducidad más próxima disponible antes de que el
despachador confirme.
Historia de usuario
Número: HU16 Usuario: ENCARGADA DE ALMACÉN
Nombre de la historia: Cálculo de consumo Dependencia para su desarrollo: HU14
promedio mensual (CPM) por clave
Prioridad en el negocio: Alta Riesgo en desarrollo: Alto
Puntos asignados: 8 Iteración asignada: Iteración 2

Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como encargada de Almacén, quiero que el sistema calcule automáticamente el CPM (consumo promedio
mensual) de cada clave con base en los movimientos de salida realmente registrados, para dejar de calcularlo
manualmente cada mes, proceso que actualmente le toma cerca de 15 días para unas 700 claves.
Criterios de aceptación
CA1 El sistema calcula el CPM de cada clave a partir del
histórico de salidas reales registradas en el sistema,
sin depender de una captura manual.
CA2 El CPM se actualiza de forma continua conforme se
registran nuevas salidas, no solo al cierre del mes.
CA3 El CPM de una clave es consultable de forma
individual y también en un listado general.
CA4 El cálculo excluye del consumo los movimientos que
no son salidas normales a Farmacia (por ejemplo,
préstamos, transferencias o bajas por caducidad),
para no distorsionar la rotación real.
Historia de usuario
Número: HU17 Usuario: ENCARGADA DE ALMACÉN /
SUPERVISIÓN
Nombre de la historia: Alertas de stock mínimo y Dependencia para su desarrollo: HU16
máximo
Prioridad en el negocio: Alta Riesgo en desarrollo: Medio
Puntos asignados: 5 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como encargada de Almacén, quiero que el sistema me alerte cuando una clave llegue a su stock mínimo (3
meses de abasto) o a su stock máximo (6 meses de abasto), calculados sobre su CPM, para actuar a tiempo sin
tener que revisar manualmente cada clave, y que esas alertas también estén disponibles para el asesor externo
(Jefe de Farmacia) sin depender de que yo esté presente para generarlas.
Criterios de aceptación
CA1 El sistema calcula el stock mínimo (3 meses de
CPM) y el stock máximo (6 meses de CPM) de cada
clave de forma automática.

CA2 Cuando la existencia de una clave cae al nivel
mínimo o por debajo, el sistema genera una alerta
de riesgo de desabasto.
CA3 Cuando la existencia de una clave llega al nivel
máximo o lo supera, el sistema genera una alerta de
riesgo de sobreabasto.
CA4 Las alertas quedan visibles en el sistema para los
roles de Almacén y Supervisión en cualquier
momento, sin necesidad de que alguien esté
conectado cuando se generan.
CA5 El listado de alertas permite identificar rápidamente
qué claves están en riesgo y desde cuándo.
Historia de usuario
Número: HU18 Usuario: ENCARGADA DE ALMACÉN
Nombre de la historia: Registro de canje de lote Dependencia para su desarrollo: HU10, HU12
Prioridad en el negocio: Media Riesgo en desarrollo: Medio
Puntos asignados: 3 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como encargada de Almacén, quiero registrar el canje de un lote próximo a caducar (menos de 9 meses)
por un lote nuevo del proveedor, para mantener la trazabilidad entre el lote original y el que lo sustituye, tal como se
hace hoy con la carta de canje.
Criterios de aceptación
CA1 El sistema permite registrar un canje indicando el
lote de origen y los datos del lote nuevo que lo
sustituye (número de lote, caducidad, cantidad).
CA2 Al confirmar el canje, el lote de origen se retira del
inventario disponible y el lote nuevo queda dado de
alta con su propia existencia y ubicación.
CA3 El sistema conserva el enlace entre el lote de origen
y el lote nuevo, consultable como parte de la
trazabilidad de ese medicamento.
CA4 Un lote ya canjeado no puede seleccionarse en una
solicitud de salida (HU14).

Historia de usuario
Número: HU19 Usuario: DESPACHADOR / ENCARGADA DE
ALMACÉN
Nombre de la historia: Apartado de medicamentos Dependencia para su desarrollo: HU15
caducados
Prioridad en el negocio: Media Riesgo en desarrollo: Bajo
Puntos asignados: 3 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como despachador de Almacén, quiero mover un lote que ya caducó a un apartado separado del inventario
disponible, para que nunca se mezcle con el stock viable y siempre se pueda diferenciar cuánto hay disponible y
cuánto está en el área de caducados, tal como se maneja físicamente hoy.
Criterios de aceptación
CA1 El sistema permite mover un lote vencido a un
estatus/ubicación de “caducados”, distinto del
inventario disponible.
CA2 Un lote en el apartado de caducados no se
contabiliza en las existencias disponibles ni puede
seleccionarse en una solicitud de salida.
CA3 El apartado de caducados se puede consultar de
forma independiente, con el detalle de clave, lote,
cantidad y fecha en que caducó.
CA4 El sistema no mueve lotes al apartado de caducados
de forma automática sin que el despachador lo
confirme, para evitar errores.
Historia de usuario
Número: HU20 Usuario: ENCARGADA DE ALMACÉN
Nombre de la historia: Registro de préstamos y Dependencia para su desarrollo: HU12
transferencias con otras instituciones
Prioridad en el negocio: Media Riesgo en desarrollo: Bajo
Puntos asignados: 3 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como encargada de Almacén, quiero registrar la salida o entrada de medicamento por préstamo o
transferencia con otra institución pública, indicando con cuál institución fue el movimiento, para llevar el control de
este tipo de operaciones por separado del traspaso normal a Farmacia.

Criterios de aceptación
CA1 El sistema permite registrar un movimiento de tipo
“préstamo” o “transferencia”, indicando clave, lote,
cantidad, institución involucrada y si es salida (se
presta o transfiere) o entrada (se recibe de otra
institución).
CA2 Estos movimientos afectan la existencia del lote y
ubicación correspondientes, igual que una salida o
entrada normal.
CA3 Los reportes de movimientos distinguen los
préstamos y transferencias de los traspasos internos
a Farmacia.
CA4 El sistema conserva el nombre de la institución como
parte del historial del movimiento.
4. Historias propuestas (por validar con la encargada de Almacén)
Historia de usuario
Número: HU21 (propuesta) Usuario: ENCARGADA DE ALMACÉN
Nombre de la historia: Carga inicial de inventario Dependencia para su desarrollo: HU01
Prioridad en el negocio: Alta Riesgo en desarrollo: Medio
Puntos asignados: 3 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como encargada de Almacén, quiero importar las existencias que hoy se llevan en el Excel/Drive
compartido, para que el sistema arranque con el inventario real y no haya que capturar las cerca de 700 claves una
por una.
Criterios de aceptación
CA1 El sistema acepta un archivo con clave, lote,
caducidad, proveedor, ubicación y cantidad en cajas
por renglón.
CA2 Antes de guardar, el sistema muestra los renglones
con error y no importa nada hasta que se corrijan o
se descarten.
CA3 Cada renglón importado genera un movimiento de
carga inicial, que no cuenta como consumo para el
CPM.

CA4 La carga queda registrada con usuario, fecha y hora.
Historia de usuario
Número: HU22 (propuesta) Usuario: ENCARGADA DE ALMACÉN
Nombre de la historia: Registro de pedido con su Dependencia para su desarrollo: HU01
detalle
Prioridad en el negocio: Alta Riesgo en desarrollo: Bajo
Puntos asignados: 3 Iteración asignada: Iteración 2
Responsable: Fabian de Jesús Jiménez Castillejos
Detalle: Como encargada de Almacén, quiero registrar cada pedido con su número, fecha, proveedor y las claves y
cantidades en cajas que se esperan, para que al llegar el medicamento el despachador solo confirme lo recibido con
la pistola lectora (HU10). Mientras no exista la integración con FONSABI, el pedido se captura manualmente.
Criterios de aceptación
CA1 El pedido se registra con número de pedido, fecha,
proveedor y al menos una partida (clave y cantidad
esperada en cajas).
CA2 No se permite registrar dos pedidos con el mismo
número.
CA3 El pedido queda activo y disponible para el
despachador al registrar entradas (HU10).
CA4 El registro queda asociado al usuario, fecha y hora.
5. Ajustes sugeridos a las historias
Las historias se conservan como se levantaron. Estos son los ajustes que el diseño de la iteración ya contempla y
que conviene validar con la encargada para agregarlos como criterios:
HU Ajuste sugerido Motivo
HU14 Permitir completar la cantidad con El prototipo de la bandeja ya lo
otra ubicación o con el siguiente propone cuando la ubicación no
lote FEFO, y confirmar entregas alcanza.
parciales (estatus “parcial”).
HU15 Un lote vencido no se sugiere ni se Si no, un lote vencido sería el
despacha aunque aún no se haya primero en la sugerencia FEFO.
movido a caducados.

HU16 Definir la fórmula: cajas Sin ventana definida, cada quien
despachadas a Farmacia en los puede calcular un CPM distinto.
últimos 6 meses cerrados, entre 6.
HU17 Mostrar existencia actual y “en Es la forma de cumplir CA4 y
alerta desde”; guardar las alertas CA5.
en base de datos.
HU13 Incluir una pantalla mínima de Sin pantalla no se puede ejecutar
Farmacia en esta iteración. la prueba de aceptación de HU13.
3.3.1 Planeación
En la sesión de planificación de la Iteración 2 se seleccionaron las historias de usuario HU10 a
HU20 y se proponen dos historias nuevas (HU21 y HU22) detectadas durante el diseño, sin las
cuales el módulo no puede operar el primer día. La priorización sigue el criterio de
dependencias funcionales: primero se registran pedidos y existencias, después se atienden
solicitudes, y al final se calculan rotación y alertas, porque el CPM depende de las salidas ya
registradas.
No. Historia de usuario Categoría S.P. Prioridad Responsable
Módulo: Recepción
HU22 Registro de pedido con su detalle Alimenta 3 Alta Fabián Jiménez
(propuesta)
HU10 Recepción de medicamento con Alimenta 5 Alta Fabián Jiménez
lector de código de barras
HU11 Marcar un pedido como cancelado Alimenta 2 Media Fabián Jiménez
HU21 Carga inicial de inventario Alimenta 3 Alta Fabián Jiménez
(propuesta)
Módulo: Existencias y despacho

HU12 Consulta y control de existencias por Documenta 3 Alta Fabián Jiménez
ubicación
HU13 Solicitud de medicamento de Alimenta 3 Alta Fabián Jiménez
Farmacia a Almacén
HU14 Validación y despacho de solicitud Procesa 5 Alta Fabián Jiménez
por Almacén
HU15 Bloqueo de despacho fuera de orden Procesa 5 Alta Fabián Jiménez
de caducidad (FEFO)
Módulo: Rotación y alertas
HU16 Cálculo de consumo promedio Procesa 8 Alta Fabián Jiménez
mensual (CPM) por clave
HU17 Alertas de stock mínimo y máximo Procesa 5 Alta Fabián Jiménez
Módulo: Movimientos especiales
HU18 Registro de canje de lote Alimenta 3 Media Fabián Jiménez
HU19 Apartado de medicamentos Procesa 3 Media Fabián Jiménez
caducados
HU20 Registro de préstamos y Alimenta 3 Media Fabián Jiménez
transferencias con otras instituciones
Total 45 S.P. originales + 6 S.P.
propuestos = 51 S.P.
Tabla X. Resumen de historias de usuario a trabajar en la Iteración 2.
Carga por residente: Fabián Jiménez, 51 puntos; Rubi Morales, 0 puntos (concentra su carga

en la Iteración 3, de Farmacia).
Velocidad y duración: falta definir cuántas semanas dura la iteración y la velocidad estimada del
equipo. Como referencia, 51 puntos para un solo residente es una carga alta; si la velocidad
real queda por debajo, conviene mover HU18, HU19 y HU20 (prioridad media, 9 puntos) a un
incremento posterior sin afectar el resto del módulo.
3.3.2 Análisis
Las trece historias de la Iteración 2 cubren cuatro responsabilidades del módulo de Almacén:
recibir el medicamento (HU22, HU10, HU11, HU21), saber dónde está y entregarlo a Farmacia
en orden de caducidad (HU12 a HU15), medir su rotación (HU16, HU17) y registrar los
movimientos que no son entrada ni salida normal (HU18 a HU20).
Dependencia con iteraciones futuras
HU Nombre Dependencia con iteraciones futuras
HU22 Registro de pedido Es el punto donde se conectará FONSABI: en una iteración
posterior los pedidos llegarán de ese sistema en lugar de
capturarse.
HU10 Recepción con lector Cada lote recibido es la materia prima de la dispensación de la
Iteración 3.
HU11 Cancelar pedido Se sustituirá por el bloqueo automático cuando exista la integración
con FONSABI.
HU21 Carga inicial Sin inventario inicial, la Iteración 3 no tendría existencias que
dispensar.
HU12 Existencias por ubicación Farmacia consultará en la Iteración 3 la existencia de Almacén
antes de solicitar.
HU13 Solicitud de Farmacia La pantalla mínima de esta iteración se integra al módulo completo
de Farmacia en la Iteración 3.
HU14 Validación y despacho Lo despachado es lo que entra al inventario de Farmacia en la
Iteración 3; así las bajas de ambas áreas coinciden.

HU15 Bloqueo FEFO La misma regla se reutilizará en la dispensación de Farmacia.
HU16 CPM Base de los reportes de Supervisión en iteraciones posteriores.
HU17 Alertas de stock Base del tablero de Supervisión.
HU18 Canje de lote La trazabilidad lote origen → lote nuevo se consulta en los reportes
de trazabilidad.
HU19 Caducados Alimenta el reporte de mermas.
HU20 Préstamos y transferencias Alimenta los reportes de movimientos con otras instituciones.
Tabla X. Análisis de historias de usuario de la Iteración 2.
3.3.3 Diseño
Propósito y alcance del diseño
Este apartado recoge el diseño de la Iteración 2, que corresponde a la etapa de Diseño de la
metodología XP: un diseño simple de las entidades de inventario, las cartas CRC de las clases
del módulo de Almacén, y el prototipo de las pantallas de recepción, consulta de existencias,
solicitudes y alertas. Se apoya en las historias de usuario HU10 a HU20 y en la base de
autenticación construida en la Iteración 1. El alcance de esta iteración es únicamente el módulo
de Almacén de Medicamentos; el módulo de Farmacia/Dispensación se aborda en la Iteración
3.
Decisiones técnicas que guían el diseño
● El código de barras se asocia al lote y no a la clave, porque una misma clave puede
tener hasta cinco proveedores distintos, cada uno con su propio código.
● La unidad de manejo es la caja (presentación de compra tal como llega), no unidosis ni
piezas sueltas.
● Las salidas de Almacén nunca son directas: siempre pasan por una solicitud de
Farmacia que Almacén valida y despacha (HU13, HU14).
● El bloqueo de caducidades (FEFO) es una regla dura del sistema, no una alerta que se
pueda ignorar (HU15).

● El CPM se calcula de forma continua a partir de movimientos reales; no se captura
manualmente.
● La integración con el sistema de Contratos/FONSABI (cotejo automático de entradas y
bloqueo automático de órdenes canceladas) queda fuera de esta iteración; HU10 y
HU11 operan de forma autocontenida, con captura y marcado manual.
● Supuestos por confirmar con el asesor externo
● El stock mínimo es de 3 meses de abasto y el máximo de 6 meses, calculados sobre el
CPM de cada clave.
● Un lote puede estar repartido en varias ubicaciones dentro del almacén.
● Las alertas de stock mínimo y máximo se muestran también al rol Supervisión, no solo a
Almacén.
Si alguno cambia, solo se ajusta el punto correspondiente del modelo o de las pantallas.
3.3.3.1 Modelo de datos
Diagrama entidad-relación
El modelo de esta iteración tiene seis tablas. La relación entre pedido y lote es opcional
(punteada) porque un lote puede llegar sin pedido asociado — por ejemplo, el que resulta de un
canje (HU18). La relación entre solicitud y movimiento también es opcional, porque solo se
llena hasta que Almacén despacha la solicitud (HU14).

Ilustración X. Modelo entidad-relación del módulo de Almacén.
Diccionario de datos
Tabla medicamento
Campo Tipo Restricciones Descripción
clave VARCHAR(10) Llave primaria Clave del medicamento,
tal como la maneja el
hospital.
nombre_generico VARCHAR(150) Obligatorio Nombre genérico del
medicamento.
presentacion VARCHAR(80) Obligatorio Presentación de compra
(por ejemplo, caja con
20 tabletas).
piezas_por_caja INT Opcional Dato de referencia; el
sistema nunca convierte
cantidades a piezas
sueltas.

| descripcion  | VARCHAR(200)  | Opcional  | Texto adicional para  |
| ------------ | ------------- | --------- | --------------------- |
identificar el
medicamento.
Tabla lote
| Campo  | Tipo  | Restricciones    | Descripción              |
| ------ | ----- | ---------------- | ------------------------ |
| id     | INT   | Llave primaria,  | Identificador del lote.  |
autoincremental
medicamento_clave  VARCHAR(10)  Obligatorio, llave foránea  Clave a la que
|              |              | a medicamento.clave  | pertenece el lote.  |
| ------------ | ------------ | -------------------- | ------------------- |
| numero_lote  | VARCHAR(40)  | Obligatorio          | Número de lote      |
asignado por el
proveedor.
| caducidad  | DATE  | Obligatorio  | Fecha de caducidad  |
| ---------- | ----- | ------------ | ------------------- |
del lote.
| proveedor  | VARCHAR(120)  | Obligatorio  | Laboratorio o  |
| ---------- | ------------- | ------------ | -------------- |
proveedor que entregó
el lote.
| codigo_barras  | VARCHAR(60)  | Opcional  | Código de barras  |
| -------------- | ------------ | --------- | ----------------- |
propio de este lote y
este proveedor.
estatus  VARCHAR  Obligatorio: DISPONIBLE,  Estado actual del lote.
CANJEADO o
CADUCADO
pedido_id  INT  Opcional, llave foránea a  Pedido de origen; nulo
|     |     | pedido.id  | si el lote viene de un  |
| --- | --- | ---------- | ----------------------- |
canje.
Tabla existencia
| Campo  | Tipo  | Restricciones    | Descripción          |
| ------ | ----- | ---------------- | -------------------- |
| id     | INT   | Llave primaria,  | Identificador de la  |
|        |       | autoincremental  | existencia.          |
lote_id  INT  Obligatorio, llave foránea  Lote al que pertenece
|            |              | a lote.id    | esta existencia.       |
| ---------- | ------------ | ------------ | ---------------------- |
| ubicacion  | VARCHAR(40)  | Obligatorio  | Ubicación física (por  |
ejemplo, “Sector 5”).
cantidad_cajas  INT  Obligatorio, mayor o igual  Cantidad disponible de
|     |     | a cero  | ese lote en esa  |
| --- | --- | ------- | ---------------- |
ubicación.
Tabla movimiento

| Campo  | Tipo  | Restricciones    | Descripción        |
| ------ | ----- | ---------------- | ------------------ |
| id     | INT   | Llave primaria,  | Identificador del  |
|        |       | autoincremental  | movimiento.        |
existencia_id  INT  Obligatorio, llave foránea  Existencia que afecta el
|       |          | a existencia.id        | movimiento.     |
| ----- | -------- | ---------------------- | --------------- |
| tipo  | VARCHAR  | Obligatorio: ENTRADA,  | Naturaleza del  |
|       |          | SALIDA, CANJE,         | movimiento.     |
PRESTAMO o
TRANSFERENCIA
| cantidad_cajas  | INT  | Obligatorio  | Cantidad que entra o  |
| --------------- | ---- | ------------ | --------------------- |
sale con este
movimiento.
| fecha  | TIMESTAMP  | Obligatorio  | Fecha y hora en que se  |
| ------ | ---------- | ------------ | ----------------------- |
registró.
usuario_id  INT  Obligatorio, llave foránea  Quién registró el
|     |     | a usuario.id  | movimiento (tabla  |
| --- | --- | ------------- | ------------------ |
usuario, Iteración 1).
institucion_externa  VARCHAR(120)  Opcional  Institución involucrada;
solo aplica a préstamo
o transferencia.
Tabla solicitud
| Campo  | Tipo  | Restricciones    | Descripción          |
| ------ | ----- | ---------------- | -------------------- |
| id     | INT   | Llave primaria,  | Identificador de la  |
|        |       | autoincremental  | solicitud.           |
medicamento_clave  VARCHAR(10)  Obligatorio, llave foránea  Clave solicitada por
|                      |      | a medicamento.clave  | Farmacia.          |
| -------------------- | ---- | -------------------- | ------------------ |
| cantidad_solicitada  | INT  | Obligatorio          | Cantidad de cajas  |
solicitada.
estatus  VARCHAR  Obligatorio: PENDIENTE  Estado de la solicitud.
o ATENDIDA
| fecha  | TIMESTAMP  | Obligatorio  | Fecha y hora en que  |
| ------ | ---------- | ------------ | -------------------- |
se generó.
usuario_id  INT  Obligatorio, llave foránea  Quién generó la
|     |     | a usuario.id  | solicitud, en Farmacia.  |
| --- | --- | ------------- | ------------------------ |

movimiento_id INT Opcional, llave foránea a Movimiento de salida
movimiento.id que la atendió; nulo
mientras está
pendiente.
Tabla pedido
Campo Tipo Restricciones Descripción
id INT Llave primaria, Identificador del pedido.
autoincremental
numero_pedido VARCHAR(30) Obligatorio, único Número de pedido con
el que se identifica en
Almacén.
estatus VARCHAR Obligatorio: ACTIVO o Se marca CANCELADO
CANCELADO manualmente (HU11).
fecha DATE Obligatorio Fecha del pedido.
Reglas del modelo
• Un lote solo puede seleccionarse en una solicitud si no existe otro lote de la misma clave
con caducidad más próxima y existencia disponible (regla FEFO, HU15).
• La cantidad de una existencia nunca puede quedar en negativo; si el despacho excede lo
disponible en una ubicación, el sistema lo rechaza (HU14).
• Un lote con estatus CANJEADO o CADUCADO no puede seleccionarse en ninguna
solicitud de salida (HU18, HU19).
• El stock mínimo y máximo de cada clave se recalculan a partir del CPM; no se capturan
manualmente (HU16, HU17).
• Un pedido con estatus CANCELADO no puede asociarse a ninguna entrada nueva
(HU11).
• Todo movimiento queda asociado al usuario que lo generó, sin excepción, para conservar
la trazabilidad.
3.3.3.2 Flujo de solicitud, validación y despacho
La siguiente ilustración muestra el camino desde que Farmacia genera una solicitud hasta que
Almacén la despacha. El sistema siempre sugiere primero el lote de caducidad más próxima
(FEFO); solo permite elegir otro lote de la misma clave si también respeta ese orden, y verifica
que la ubicación elegida tenga existencia suficiente antes de confirmar.

Ilustración X. Flujo de solicitud, validación y despacho.
3.3.3.3 Cartas CRC
Cada carta indica la clase, lo que debe hacer (responsabilidades) y con qué otras clases
trabaja (colaboradores). Las diez primeras son del backend (Spring Boot) y la última del
frontend (Angular). Los nombres se pueden ajustar al programar, pero las responsabilidades
deben conservarse.
Clase: Medicamento · Backend (Spring Boot) · Historias: HU10, HU12, HU13, HU16, HU17
entidad
Responsabilidades Colaboradores

• Guardar el catálogo de claves: nombre genérico, • Lote • Solicitud
presentación y piezas por caja (dato de referencia,
no se usa para convertir cantidades). • Ser la
referencia única de cada clave para lotes y
solicitudes.
Clase: Lote · Backend (Spring Boot) · entidad Historias: HU10, HU11, HU15, HU18, HU19
Responsabilidades Colaboradores
• Guardar los datos de cada lote recibido: número, • Medicamento • Pedido • Existencia
caducidad, proveedor y código de barras propio de
ese lote. • Conocer su estatus: disponible, canjeado
o caducado. • Saber a qué pedido pertenece, cuando
aplica.
Clase: Existencia · Backend (Spring Boot) · entidad Historias: HU12, HU14, HU15
Responsabilidades Colaboradores
• Guardar cuánta cantidad de un lote hay en cada • Lote • Movimiento
ubicación física. • Ser la unidad exacta que se
descuenta al confirmar un despacho. • Nunca quedar
en una cantidad negativa.
Clase: Movimiento · Backend (Spring Boot) · entidad Historias: HU14, HU16, HU18, HU19, HU20
Responsabilidades Colaboradores
• Registrar cada entrada, salida, canje, préstamo o • Existencia • ServicioRotacion
transferencia, con cantidad, fecha y usuario. • Ser la
fuente de datos real para el cálculo del CPM. •
Conservar el nombre de la institución externa
cuando es préstamo o transferencia.
Clase: Solicitud · Backend (Spring Boot) · entidad Historias: HU13, HU14
Responsabilidades Colaboradores
• Guardar la petición de Farmacia: clave, cantidad • Medicamento • Movimiento
solicitada y estatus (pendiente o atendida). •
Enlazarse con el movimiento que la atendió una vez
que Almacén la despacha.

Clase: Pedido · Backend (Spring Boot) · entidad Historias: HU10, HU11
Responsabilidades Colaboradores
• Guardar el número y el estatus (activo o cancelado) • Lote
del pedido contra el que se reciben lotes. • El estatus
se marca manualmente en esta iteración; no
depende de ningún sistema externo.
Clase: ServicioRecepcion · Backend (Spring Boot) · Historias: HU10, HU11
servicio
Responsabilidades Colaboradores
• Validar que los campos obligatorios de una entrada • Lote • Existencia • Pedido
estén completos. • Rechazar la recepción si el
pedido asociado está cancelado. • Crear el lote y su
existencia inicial en la ubicación indicada.
Clase: ServicioDespacho · Backend (Spring Boot) · Historias: HU12, HU13, HU14, HU15
servicio
Responsabilidades Colaboradores
• Sugerir el lote de caducidad más próxima con • Existencia • Solicitud • Movimiento
existencia disponible (regla FEFO). • Bloquear la
selección de un lote fuera de ese orden, incluso si se
fuerza manualmente. • Descontar la cantidad exacta
de la ubicación elegida al confirmar el despacho.
Clase: ServicioRotacion · Backend (Spring Boot) · Historias: HU16, HU17
servicio
Responsabilidades Colaboradores
• Calcular el CPM de cada clave a partir de los • Movimiento • Medicamento
movimientos de salida reales, sin captura manual. •
Calcular el stock mínimo (3 meses) y máximo (6
meses) de cada clave sobre su CPM. • Generar una
alerta cuando una clave cruza su mínimo o su
máximo.
Clase: ServicioMovimientosEspeciales · Backend Historias: HU18, HU19, HU20
(Spring Boot) · servicio
Responsabilidades Colaboradores

• Registrar un canje conservando el enlace entre el • Lote • Existencia • Movimiento
lote de origen y el lote nuevo. • Mover un lote vencido
al apartado de caducados sin mezclarlo con el
inventario disponible. • Registrar préstamos y
transferencias con otras instituciones, con su propio
movimiento.
Clase: Pantallas de Almacén · Frontend (Angular) · Historias: HU10 a HU20
componentes
Responsabilidades Colaboradores
• Mostrar el formulario de recepción y el bloqueo por • ServicioSesion (Iteración 1) • GuardaRutas
pedido cancelado. • Mostrar la consulta de (Iteración 1)
existencias por ubicación con el lote FEFO marcado.
• Mostrar la bandeja de solicitudes pendientes con su
pantalla de validación. • Mostrar el panel de alertas
de stock mínimo y máximo.
3.3.3.4 Prototipos de pantallas
Son prototipos sencillos, sin diseño gráfico final, para validar con la encargada de Almacén qué
se ve y qué se puede hacer en cada pantalla antes de programar. Los nombres y cantidades
son datos de ejemplo.
Registrar entrada
El mismo formulario sirve para una entrada normal y para mostrar el bloqueo cuando el pedido
fue cancelado por el nivel central (HU10, HU11).
Ilustración X. Registrar entrada de medicamento.

Diseño final:
Aquí va la foto de: la pantalla real de Registrar entrada ya programada (entrada válida y aviso
de pedido cancelado).
Ilustración X. Diseño final de Registrar entrada.
Existencias por ubicación
Muestra el desglose de cada lote por ubicación física y marca con la etiqueta FEFO el lote que
debe salir primero por su caducidad (HU12, HU15).
Ilustración X. Existencias por ubicación.
Diseño final:
Aquí va la foto de: la pantalla real de Existencias por ubicación, con la etiqueta FEFO en el lote
que sale primero.
Ilustración X. Diseño final de Existencias por ubicación.
Bandeja de solicitudes y validación
A la izquierda, las solicitudes pendientes de Farmacia; a la derecha, el detalle con el lote
sugerido por FEFO y el aviso cuando la ubicación no alcanza para la cantidad solicitada (HU13,
HU14, HU15).

Ilustración X. Bandeja de solicitudes y validación de lote.
Diseño final:
Aquí va la foto de: la pantalla real de la Bandeja de solicitudes, con el lote sugerido por FEFO y
el aviso de existencia insuficiente.
Ilustración X. Diseño final de la Bandeja de solicitudes y validación.
Alertas de stock mínimo y máximo
Muestra el CPM de cada clave junto con su stock mínimo, su stock máximo y el estatus
resultante (HU16, HU17).
Ilustración X. Alertas de stock mínimo y máximo.
Diseño final:
Aquí va la foto de: la pantalla real de Alertas de stock mínimo y máximo.
Ilustración X. Diseño final de Alertas de stock mínimo y máximo.

3.3.3.5 Trazabilidad: historias, clases y pantallas
Permite comprobar que cada historia de usuario tiene clases y pantallas que la cubren, y sirve
de guía para repartir el trabajo de codificación. HU18, HU19 y HU20 son formularios sencillos
sobre las mismas clases del backend; su pantalla se detalla al programarlas, sin requerir un
prototipo aparte en esta etapa.
| Historia  | Clases involucradas        | Pantallas          |
| --------- | -------------------------- | ------------------ |
| HU10      | Pedido, Lote, Existencia,  | Registrar entrada  |
ServicioRecepcion, Pantallas de
Almacén
| HU11  | Pedido, ServicioRecepcion  | Registrar entrada (pedido  |
| ----- | -------------------------- | -------------------------- |
cancelado)
HU12  Existencia, Lote, Pantallas de  Existencias por ubicación
Almacén
HU13  Solicitud, Medicamento, Pantallas de  Generar solicitud (pantalla de
|     | Almacén  | Farmacia, Iteración 3)  |
| --- | -------- | ----------------------- |
HU14  Solicitud, Existencia, Movimiento,  Bandeja de solicitudes y
|     | ServicioDespacho  | validación  |
| --- | ----------------- | ----------- |
HU15  Existencia, Lote, ServicioDespacho  Bandeja de solicitudes y
validación (bloqueo FEFO)
HU16  Movimiento, Medicamento,  Alertas de stock (columna CPM)
ServicioRotacion
HU17  Medicamento, ServicioRotacion  Alertas de stock mínimo y
máximo
HU18  Lote, ServicioMovimientosEspeciales  Registro de canje (se detalla en
codificación)
| HU19  | Lote, Existencia,              | Apartado de caducados (se       |
| ----- | ------------------------------ | ------------------------------- |
|       | ServicioMovimientosEspeciales  | detalla en codificación)        |
| HU20  | Movimiento,                    | Préstamos y transferencias (se  |
|       | ServicioMovimientosEspeciales  | detalla en codificación)        |
3.3.3.6 Especificación de endpoints REST
Los endpoints se organizan por módulo. Todos requieren el token de sesión de la Iteración 1 en
el encabezado Authorization: Bearer {token}, y cada uno valida el rol: Almacén (despachador y
encargada), Farmacia o Supervisión. Si en la Iteración 1 se usó otra convención de rutas o de
verbos, se ajusta a esa.
Módulo de Pedidos — /api/almacen/pedidos (rol: encargada)

| Método  Endpoint  |     | Body / Params  | Retorna  | Descripción  |
| ----------------- | --- | -------------- | -------- | ------------ |
GET  /api/almacen/pedidos  ?estatus=ACTIVO  200: lista de  Lista pedidos;
|     |     |     | pedidos con  | el           |
| --- | --- | --- | ------------ | ------------ |
|     |     |     | avance de    | despachador  |
|     |     |     | recepción    | también lo   |
consulta para
elegir en la
recepción.
| GET  /api/almacen/pedidos/{id}  |     | —   | 200: pedido     | Detalle con  |
| ------------------------------- | --- | --- | --------------- | ------------ |
|                                 |     |     | con partidas ·  | cantidades   |
|                                 |     |     | 404             | esperadas y  |
recibidas.
POST  /api/almacen/pedidos  { numeroPedido,  201: pedido ·  Registra el
|     |     | fecha, proveedor,     | 400: "Ya    | pedido   |
| --- | --- | --------------------- | ----------- | -------- |
|     |     | partidas:[{clave,     | existe un   | (HU22).  |
|     |     | cantidadEsperada}] }  | pedido con  |          |
ese número"
PATCH  /api/almacen/pedidos/{id}/cancelar  { motivo }  200: pedido  Cancela con
|     |     |     | CANCELADO      | motivo, fecha  |
| --- | --- | --- | -------------- | -------------- |
|     |     |     | · 400: motivo  | y usuario      |
|     |     |     | vacío          | (HU11).        |
Módulo de Recepción — /api/almacen (rol: despachador)
| Método  | Endpoint  | Body / Params  | Retorna  | Descripción  |
| ------- | --------- | -------------- | -------- | ------------ |
GET  /api/almacen/codigos-bar —  200: { clave,  Resuelve lo que
|     | ras/{codigo}  |     | nombre,             | leyó la pistola  |
| --- | ------------- | --- | ------------------- | ---------------- |
|     |               |     | proveedor } · 404:  | (HU10 CA2).      |
código no
registrado
POST  /api/almacen/codigos-bar { codigo, clave,  201 · 400: código  Asocia un código
|     | ras  | proveedor }  | ya asociado  | nuevo una sola  |
| --- | ---- | ------------ | ------------ | --------------- |
vez (HU10 CA3).
POST  /api/almacen/entradas  { pedidoId, clave,  201: lote y  Registra la
|     |     | proveedor,         | existencias · 400:  | entrada en una  |
| --- | --- | ------------------ | ------------------- | --------------- |
|     |     | numeroLote,        | pedido              | transacción     |
|     |     | caducidad,         | cancelado, clave    | (HU10, HU11).   |
|     |     | ubicaciones:[{ubic | fuera del pedido    |                 |
|     |     | acion, cajas}] }   | o campo faltante    |                 |

POST  /api/almacen/carga-inicial archivo (multipart)  200: renglones  Revisa el archivo
|     | /validar  |     | válidos y      | sin guardar (HU21  |     |
| --- | --------- | --- | -------------- | ------------------ | --- |
|     |           |     | renglones con  | CA2).              |     |
error
POST  /api/almacen/carga-inicial { idValidacion }  201: resumen de  Importa lo
|     | /confirmar  |     | lo importado  | validado (HU21).  |     |
| --- | ----------- | --- | ------------- | ----------------- | --- |
Módulo de Existencias y despacho (rol: despachador; Farmacia solo en /api/farmacia)
| Método  Endpoint  |     | Body / Params  |     | Retorna  | Descripción  |
| ----------------- | --- | -------------- | --- | -------- | ------------ |
GET  /api/almacen/existencias  ?clave=&lote=&ubicacion=  200:  Consulta de
|     |     |     |     | renglones   | existencias  |
| --- | --- | --- | --- | ----------- | ------------ |
|     |     |     |     | por lote y  | (HU12).      |
ubicación
con
bandera
fefo
POST  /api/farmacia/solicitudes  { clave, cantidad }  201:  Farmacia
|     |     |     |     | solicitud  | genera la  |
| --- | --- | --- | --- | ---------- | ---------- |
|     |     |     |     | PENDIENT   | solicitud  |
|     |     |     |     | E · 400:   | (HU13).    |
clave
inexistente
o cantidad
≤ 0
| GET  /api/farmacia/solicitudes  |     | ?mias=true  |     | 200:         | Farmacia      |
| ------------------------------- | --- | ----------- | --- | ------------ | ------------- |
|                                 |     |             |     | solicitudes  | consulta sus  |
|                                 |     |             |     | con estatus  | solicitudes   |
|                                 |     |             |     | y cantidad   | (HU13 CA3).   |
atendida
GET  /api/almacen/solicitudes  ?estatus=PENDIENTE,PAR 200:  Bandeja del
|     |     | CIAL  |     | bandeja de   | despachador  |
| --- | --- | ----- | --- | ------------ | ------------ |
|     |     |       |     | solicitudes  | (HU14 CA1).  |
GET  /api/almacen/solicitudes/{id}/sugerencia  —  200:  Sugerencia
|     |     |     |     | partidas   | FEFO    |
| --- | --- | --- | --- | ---------- | ------- |
|     |     |     |     | sugeridas  | (HU14,  |
|     |     |     |     | en orden   | HU15).  |
FEFO
hasta cubrir
la cantidad

POST  /api/almacen/solicitudes/{id}/despachar  { partidas:[{existenciaId,  200:  Despacha
|     |     | cajas}], parcial }  |     | solicitud   | en una        |
| --- | --- | ------------------- | --- | ----------- | ------------- |
|     |     |                     |     | ATENDIDA    | transacción;  |
|     |     |                     |     | o PARCIAL   | valida FEFO   |
|     |     |                     |     | · 409:      | en el         |
|     |     |                     |     | "Existe un  | backend       |
|     |     |                     |     | lote con    | (HU14,        |
|     |     |                     |     | caducidad   | HU15).        |
más
próxima" ·
400:
existencia
insuficiente
Módulo de Rotación y alertas (rol: encargada; Supervisión solo lectura)
| Método  | Endpoint  | Body / Params  | Retorna  | Descripción  |     |
| ------- | --------- | -------------- | -------- | ------------ | --- |
GET  /api/almacen/rotacion  ?clave=  200: clave, CPM,  CPM individual o
|     |     |     | existencia,  | general (HU16).  |     |
| --- | --- | --- | ------------ | ---------------- | --- |
mínimo, máximo,
historial suficiente
GET  /api/almacen/alertas  ?activas=true  200: alertas con  Panel de alertas
|     |     |     | tipo y fecha de  | (HU17).  |     |
| --- | --- | --- | ---------------- | -------- | --- |
inicio
Módulo de Movimientos especiales (rol: encargada; caducados también despachador)
| Método  | Endpoint  | Body / Params  | Retorna  | Descripción  |     |
| ------- | --------- | -------------- | -------- | ------------ | --- |
POST  /api/almacen/canjes  { loteOrigenId,  201: lote nuevo  Registra el canje
|     |     | numeroLote,         | · 400: lote no  | (HU18).  |     |
| --- | --- | ------------------- | --------------- | -------- | --- |
|     |     | caducidad,          | elegible (más   |          |     |
|     |     | ubicaciones:[{ubica | de 9 meses o    |          |     |
|     |     | cion, cajas}] }     | no              |          |     |
DISPONIBLE)
GET  /api/almacen/caducados  ?pendientes=true  200: lotes  Consulta y
|     |     |     | vencidos por   | sugerencia  |     |
| --- | --- | --- | -------------- | ----------- | --- |
|     |     |     | mover o ya en  | (HU19).     |     |
caducados
POST  /api/almacen/caducados  { loteId }  200: lote  Mueve el lote
|     |     |     | CADUCADO  | con confirmación  |     |
| --- | --- | --- | --------- | ----------------- | --- |
(HU19).

POST  /api/almacen/movimientos-exte { tipo, sentido,  201:  Préstamos y
|     | rnos  |     | existenciaId o        | movimiento ·      | transferencias  |
| --- | ----- | --- | --------------------- | ----------------- | --------------- |
|     |       |     | datos del lote,       | 400: institución  | (HU20).         |
|     |       |     | cajas, institucion }  | vacía o           |                 |
existencia
insuficiente
Tabla X. Especificación de endpoints de la Iteración 2.
3.3.3.7 Ajustes sugeridos al diseño (por validar)
El diseño anterior se conserva tal como se elaboró. Estos ajustes surgieron al revisarlo contra
las historias y los prototipos; conviene validarlos con el asesor antes de codificar.
| Elemento  |     | Ajuste sugerido  |     | Motivo  |     |
| --------- | --- | ---------------- | --- | ------- | --- |
Código de barras  Guardarlo en una tabla  El mismo código se repite
|     |     | propia (código → clave y   |     | en todos los lotes de un   |     |
| --- | --- | -------------------------- | --- | -------------------------- | --- |
|     |     | proveedor) en lugar de en  |     | proveedor; en lote, un     |     |
|     |     | lote.                      |     | escaneo devolvería varios  |     |
lotes.
| Pedido  |     | Agregar pedido_detalle       |     | Permite precargar los datos  |     |
| ------- | --- | ---------------------------- | --- | ---------------------------- | --- |
|         |     | (clave y cajas esperadas) y  |     | al escanear (HU10) y         |     |
|         |     | los campos motivo, fecha y   |     | cumplir HU11 CA1 y CA4.      |     |
usuario de cancelación.
Solicitud y movimiento  Que cada movimiento  El prototipo de la bandeja
|       |     | apunte a su solicitud (uno a  |     | propone completar con otra  |     |
| ----- | --- | ----------------------------- | --- | --------------------------- | --- |
|       |     | muchos) y agregar el estatus  |     | ubicación o una entrega     |     |
|       |     | PARCIAL.                      |     | parcial.                    |     |
| Lote  |     | Agregar lote_origen_id.       |     | HU18 CA3 pide conservar     |     |
el enlace del canje.
Movimiento  Agregar el campo sentido  HU20 registra préstamos
|     |     | (entrada o salida) y el tipo  |     | de entrada y de salida;  |     |
| --- | --- | ----------------------------- | --- | ------------------------ | --- |
|     |     | BAJA_CADUCIDAD.               |     | HU19 necesita su         |     |
movimiento.
| Alertas  |     | Guardarlas en una tabla       |     | HU17 CA4 y CA5 piden        |     |
| -------- | --- | ----------------------------- | --- | --------------------------- | --- |
|          |     | alerta con fecha de inicio y  |     | verlas sin nadie conectado  |     |
|          |     | fin.                          |     | y saber desde cuándo.       |     |

Regla FEFO Excluir lotes vencidos Si no, un lote vencido sería
aunque no se hayan movido el primero en la sugerencia.
a caducados.
Prototipo de alertas Corregir valores (CPM 180 Los valores actuales no
→ mínimo 540, máximo cumplen la regla de 3 y 6
1,080) y agregar existencia meses.
actual y “en alerta desde”.
Prototipo de bandeja Cambiar el aviso a El lote L-7940 solo está en
“Completar con el siguiente una ubicación.
lote FEFO” o “Confirmar
entrega parcial”.
Prototipo de entrada Agregar el campo de HU10 se actualizó para
escaneo con la pistola usar la pistola.
lectora.
Tabla X. Ajustes sugeridos al diseño.
Modelo de datos con los ajustes sugeridos
Ilustración X. Modelo de datos con los ajustes sugeridos (en color, las tablas nuevas).

Flujo sugerido de recepción con pistola lectora
Ilustración X. Flujo sugerido de recepción con pistola lectora.
3.3.4 Codificación
En esta sección se documenta la implementación técnica de las historias de la Iteración 2. El
código sigue el patrón Controller → Service → Repository establecido en la Iteración 1. Se
presentan los componentes más representativos de cada módulo con su ruta, una captura del
fragmento clave y la descripción de su responsabilidad.
3.3.4.1 Backend — Spring Boot
PedidoController.java — Endpoints de pedidos (HU22, HU11)
El controlador expone el registro, la consulta y la cancelación de pedidos y delega todo al

service; no contiene lógica de negocio.
Ruta: (completar, por ejemplo controller/almacen/PedidoController.java)
📷 Aquí va la foto de: la clase PedidoController.java mostrando los métodos de registrar, listar y
cancelar pedido.
Ilustración X. PedidoController.java
RecepcionServiceImpl.java — Recepción con pistola lectora (HU10, HU11)
Resuelve el código escaneado, valida que el pedido esté ACTIVO y que la clave venga en él, y
guarda lote, existencia y movimiento ENTRADA en una sola transacción.
Ruta: (completar)
📷 Aquí va la foto de: el método que registra la entrada, donde se vean la validación de pedido
cancelado, la validación de clave del pedido y la anotación @Transactional.
Ilustración X. RecepcionServiceImpl.java
DespachoServiceImpl.java — Sugerencia y bloqueo FEFO (HU14, HU15)
Es la clase más importante de la iteración: ordena los lotes por caducidad, excluye vencidos y
canjeados, rechaza en el backend cualquier partida fuera de orden y descuenta todas las
partidas en una sola transacción.
Ruta: (completar)
📷 Aquí va la foto de: el método que calcula la sugerencia FEFO (la consulta ordenada por
caducidad).
Ilustración X. Sugerencia FEFO en DespachoServiceImpl.java
📷 Aquí va la foto de: el método despachar(), donde se vea la validación FEFO, la validación
de existencia suficiente y el cambio de estatus de la solicitud.
Ilustración X. Método despachar() en DespachoServiceImpl.java
RotacionServiceImpl.java — CPM y alertas (HU16, HU17)
Calcula el CPM con las salidas a Farmacia de los últimos meses cerrados y abre o cierra
alertas desde un proceso programado con @Scheduled.
Ruta: (completar)
📷 Aquí va la foto de: el método que calcula el CPM (la consulta que suma solo las salidas a
Farmacia).
Ilustración X. Cálculo del CPM en RotacionServiceImpl.java
📷 Aquí va la foto de: el método con @Scheduled que genera y cierra las alertas.
Ilustración X. Proceso programado de alertas
MovimientoEspecialServiceImpl.java — Canjes, caducados, préstamos y transferencias (HU18,
HU19, HU20)

Ruta: (completar)
📷 Aquí va la foto de: el método de canje, donde se vea cómo el lote nuevo guarda su
lote_origen_id y el lote original pasa a CANJEADO.
Ilustración X. Registro de canje en MovimientoEspecialServiceImpl.java
3.3.4.2 Frontend — Angular
almacen.service.ts — Métodos HTTP del módulo
Concentra las llamadas HTTP del módulo de Almacén para que los componentes no repitan
lógica de comunicación con el backend.
Ruta: (completar)
📷 Aquí va la foto de: el servicio almacen.service.ts con los métodos de entradas, existencias,
solicitudes y despacho.
Ilustración X. almacen.service.ts
Componente de recepción — Captura de la pistola lectora
Mantiene el foco en el campo de escaneo y, al recibir el Enter que envía la pistola, consulta el
código y precarga la partida del pedido.
Ruta: (completar)
📷 Aquí va la foto de: el método del componente que recibe la lectura de la pistola (evento
keyup.enter o similar) y llama al servicio.
Ilustración X. Componente de recepción
Componente de despacho — Bandeja y validación
Ruta: (completar)
📷 Aquí va la foto de: el método que muestra el lote FEFO sugerido y el aviso cuando la
ubicación no alcanza.
Ilustración X. Componente de despacho
3.3.5 Pruebas unitarias
Las pruebas unitarias verifican el comportamiento aislado de los servicios. Cada prueba usa
mocks de los repositorios, sin base de datos real, y sigue el patrón AAA: Arrange para preparar
el escenario, Act para ejecutar el método y Assert para verificar el resultado. En XP se escriben
antes o junto con el código; esta es la lista planeada.
ID HU Escenario Clase bajo prueba Resultado esperado

PU-01  HU22  Registrar pedido  PedidoServiceImpl  HTTP 400 · save()
|     |     | con número  |     | nunca llamado  |
| --- | --- | ----------- | --- | -------------- |
duplicado
PU-02  HU11  Registrar  RecepcionServiceImpl  HTTP 400 · no se
|     |     | entrada contra  |     | crea lote ni existencia  |
| --- | --- | --------------- | --- | ------------------------ |
pedido
CANCELADO
PU-03  HU10  Escanear código  RecepcionServiceImpl  HTTP 404 con
|     |     | no registrado  |     | indicación de asociar  |
| --- | --- | -------------- | --- | ---------------------- |
el código
PU-04  HU10  Entrada con  RecepcionServiceImpl  HTTP 400 · nada
|     |     | clave que no  |     | guardado  |
| --- | --- | ------------- | --- | --------- |
viene en el
pedido
PU-05  HU10  Entrada válida  RecepcionServiceImpl  1 lote, 2 existencias,
|     |     | repartida en dos  |     | 2 movimientos  |
| --- | --- | ----------------- | --- | -------------- |
|     |     | ubicaciones       |     | ENTRADA        |
PU-06  HU14  Despacho con  DespachoServiceImpl  HTTP 400 · ninguna
|     |     | partida mayor a  |     | existencia modificada  |
| --- | --- | ---------------- | --- | ---------------------- |
la existencia
PU-07  HU15  Despachar lote  DespachoServiceImpl  HTTP 409 · nada
|     |     | de caducidad  |     | descontado  |
| --- | --- | ------------- | --- | ----------- |
más lejana
habiendo uno
más próximo
PU-08  HU15  Dos lotes con la  DespachoServiceImpl  HTTP 200 con
|     |     | misma  |     | cualquiera de los dos  |
| --- | --- | ------ | --- | ---------------------- |
caducidad
PU-09  HU15  Lote vencido aún  DespachoServiceImpl  No aparece en la
|     |     | marcado     |     | sugerencia ni se   |
| --- | --- | ----------- | --- | ------------------ |
|     |     | DISPONIBLE  |     | permite despachar  |
PU-10  HU14  Entrega parcial  DespachoServiceImpl  Solicitud PARCIAL ·
cantidad_atendida
correcta
| PU-11  | HU16  | CPM con   | RotacionServiceImpl  | Solo cuentan las    |
| ------ | ----- | --------- | -------------------- | ------------------- |
|        |       | salidas,  |                      | salidas a Farmacia  |
préstamos y
bajas mezclados

PU-12 HU17 Existencia por RotacionServiceImpl Se crea alerta
debajo de CPM MINIMO con
× 3 sin alerta fecha_inicio
abierta
PU-13 HU17 Clave que RotacionServiceImpl La alerta se cierra
vuelve al rango con fecha_fin
con alerta
abierta
PU-14 HU18 Canje de lote MovimientoEspecialServiceImpl HTTP 400 · lote sigue
con más de 9 DISPONIBLE
meses de vida
PU-15 HU21 Archivo con una CargaInicialServiceImpl Renglón marcado
clave inexistente con error · nada
importado
Tabla X. Pruebas unitarias planeadas de la Iteración 2.
Ejemplo en formato AAA — PU-07
PU-07 despachar() rechaza un lote fuera de orden FEFO
[HU15]
Clase bajo prueba DespachoServiceImpl
Arrange (preparación) Lote L-7940 (caducidad 02/2027, 10 cajas en Sector 3)
y lote L-8821 (caducidad 07/2028, 40 cajas en Sector
5), ambos de la clave 0134 y DISPONIBLE. Solicitud
PENDIENTE de 20 cajas. El despachador envía una
partida de 20 cajas de L-8821.
Act (ejecución) despachoService.despachar(solicitudId, partidas)
Assert (verificación) Estatus 409 · mensaje "Existe un lote con caducidad
más próxima" · existenciaRepository.save() y
movimientoRepository.save() nunca llamados · la
solicitud sigue PENDIENTE.
Resultado esperado ✔ El bloqueo FEFO se aplica en el backend aunque la
pantalla lo permita.
Evidencia de las pruebas unitarias
Para cada prueba se agrega la captura del método de prueba (con sus secciones Arrange, Act
y Assert) y, al final, la captura de la ejecución de todas las pruebas.
📷 Aquí va la foto de: la clase de prueba PedidoServiceImplTest con PU-01.
Ilustración X. Prueba unitaria PU-01 — pedido duplicado
📷 Aquí va la foto de: la clase de prueba RecepcionServiceImplTest con PU-02 a PU-05.

Ilustración X. Pruebas unitarias de recepción
📷 Aquí va la foto de: la clase de prueba DespachoServiceImplTest con PU-06 a PU-10 (sobre
todo PU-07, bloqueo FEFO).
Ilustración X. Pruebas unitarias de despacho y FEFO
📷 Aquí va la foto de: la clase de prueba RotacionServiceImplTest con PU-11 a PU-13.
Ilustración X. Pruebas unitarias de CPM y alertas
📷 Aquí va la foto de: las clases de prueba MovimientoEspecialServiceImplTest (PU-14) y
CargaInicialServiceImplTest (PU-15).
Ilustración X. Pruebas unitarias de movimientos especiales y carga inicial
📷 Aquí va la foto de: la ejecución de todas las pruebas en el IDE o con mvn test, donde se vea
el total de pruebas en verde (passed).
Ilustración X. Resultado de la ejecución de pruebas unitarias de la Iteración 2
3.3.5.1 Resumen de pruebas unitarias — Iteración 2
Se llena al ejecutar las pruebas: ID, HU, escenario, clase y resultado (✔ o ✘), igual que la
tabla de pruebas planeadas pero con el resultado real.
3.3.6 Pruebas de aceptación
Las pruebas de aceptación verifican que cada historia cumple los criterios acordados con la
encargada de Almacén y el asesor externo. Se derivan directamente de los criterios de
aceptación y se ejecutan al cierre de la iteración con los módulos integrados; la columna
Evaluación se llena en ese momento. La numeración continúa la de la Iteración 1.
| ID        | HU    | Caso de prueba           | Resultado esperado       |
| --------- | ----- | ------------------------ | ------------------------ |
| PA-I2-01  | HU22  | Registrar un pedido con  | El pedido aparece        |
|           |       | dos partidas             | ACTIVO con sus partidas  |
y cantidad recibida en
cero.
| PA-I2-02  | HU10  | Recibir una partida      | Clave y proveedor se         |
| --------- | ----- | ------------------------ | ---------------------------- |
|           |       | escaneando una caja con  | llenan solos; al confirmar,  |
|           |       | la pistola               | la existencia y la partida   |
del pedido se actualizan.
| PA-I2-03  | HU10  | Escanear un código  | La segunda lectura del  |
| --------- | ----- | ------------------- | ----------------------- |
|           |       | nuevo y asociarlo   | mismo código ya se      |
reconoce.

| PA-I2-04  | HU11  | Cancelar un pedido e  | El pedido no aparece       |
| --------- | ----- | --------------------- | -------------------------- |
|           |       | intentar recibirlo    | para recibir y el intento  |
directo muestra el aviso.
PA-I2-05  HU21  Importar un archivo de 20  Se muestran los 2 errores
|     |     | renglones con 2 errores  | y no se importa nada  |
| --- | --- | ------------------------ | --------------------- |
hasta corregirlos.
| PA-I2-06  | HU12  | Consultar un lote          | Se ven dos renglones con    |
| --------- | ----- | -------------------------- | --------------------------- |
|           |       | repartido en dos sectores  | su cantidad; el filtro por  |
ubicación funciona.
| PA-I2-07  | HU13  | Farmacia genera una    | Aparece PENDIENTE en       |
| --------- | ----- | ---------------------- | -------------------------- |
|           |       | solicitud de 20 cajas  | la bandeja de Almacén; el  |
inventario no cambia.
PA-I2-08  HU14  Despachar una solicitud  Se descuentan ambas
|           |       | completando con dos    | ubicaciones y la solicitud  |
| --------- | ----- | ---------------------- | --------------------------- |
|           |       | ubicaciones            | queda ATENDIDA.             |
| PA-I2-09  | HU14  | Confirmar una entrega  | La solicitud queda          |
|           |       | parcial                | PARCIAL y Farmacia ve       |
la cantidad pendiente.
PA-I2-10  HU15  Intentar despachar un lote  El sistema lo bloquea y
|           |       | de caducidad más lejana  | señala el lote FEFO.  |
| --------- | ----- | ------------------------ | --------------------- |
| PA-I2-11  | HU16  | Consultar el CPM de una  | El CPM ignora el      |
|           |       | clave con salidas y un   | préstamo.             |
préstamo
PA-I2-12  HU17  Llevar una clave por  La alerta aparece para
|     |     | debajo de su mínimo  | Almacén y para  |
| --- | --- | -------------------- | --------------- |
Supervisión con su fecha
de inicio.
| PA-I2-13  | HU18  | Canjear un lote con 6  | El lote origen queda  |
| --------- | ----- | ---------------------- | --------------------- |
|           |       | meses de vida          | CANJEADO y el nuevo   |
muestra su lote de origen.
| PA-I2-14  | HU19  | Mover un lote vencido a  | Deja de sumar en           |
| --------- | ----- | ------------------------ | -------------------------- |
|           |       | caducados                | existencias disponibles y  |
aparece en el apartado
de caducados.

PA-I2-15 HU20 Registrar un préstamo de La existencia baja y el
salida a otra institución movimiento muestra la
institución.
Tabla X. Pruebas de aceptación planeadas de la Iteración 2.
Ejemplo en el formato del reporte — PA-I2-10
PRUEBA DE ACEPTACIÓN PA-I2-10 · Historia de usuario: HU15
Nombre del caso de prueba Bloqueo de despacho fuera de orden FEFO
Descripción Se verifica que el sistema no permite despachar un
lote de caducidad más lejana mientras exista
existencia de un lote con caducidad más próxima de
la misma clave.
Condiciones de ejecución Usuario con rol de despachador autenticado. Clave
0134 con L-7940 (02/2027, 10 cajas, Sector 3) y
L-8821 (07/2028, 40 cajas, Sector 5). Solicitud
PENDIENTE de 20 cajas.
Entradas En la bandeja, el despachador cambia el lote
sugerido L-7940 por L-8821 y confirma 20 cajas.
Resultado esperado El sistema muestra "Existe un lote con caducidad
más próxima (L-7940)", no descuenta nada y la
solicitud sigue PENDIENTE.
Evaluación Por ejecutar
3.3.7 Pruebas de integración — Iteración 1 + Iteración 2
Al cierre de la iteración se verifica que el módulo de Almacén funciona sobre la autenticación y
el control de roles de la Iteración 1.
3.3.7.1 Escenarios de integración a verificar
Escenario Resultado esperado
Usuario de Farmacia intenta entrar a GuardaRutas lo redirige y el backend
/almacen/entradas responde 403.
Usuario de Supervisión abre el panel de Ve las alertas sin botones de acción;
alertas cualquier POST responde 403.
Cerrar sesión a mitad de un despacho El despacho no se guarda y la solicitud
sigue como estaba.
Cada movimiento generado en la iteración Queda con el usuario de la sesión que lo
registró.

Ciclo completo: pedido → recepción → Las cantidades cuadran en cada paso con
solicitud → despacho → CPM → alerta usuarios de roles distintos.
3.3.7.2 Entregable al cierre de la Iteración 2
Módulo de Almacén de Medicamentos funcional, integrado con la autenticación de la Iteración 1
y demostrable ante la encargada de Almacén y el asesor externo: registro y cancelación de
pedidos, recepción con pistola lectora, carga inicial del inventario, existencias por ubicación,
atención de solicitudes con bloqueo FEFO, CPM y alertas automáticas, canjes, caducados,
préstamos y transferencias.
Puntos por confirmar con el asesor externo
1. Qué trae el código de barras. Confirmar si los códigos de las cajas solo identifican el
producto (clave y proveedor) o si algunos traen también lote y caducidad (GS1). Si los traen, el
sistema puede llenarlos solos al escanear.
2. De dónde se precargan lote y caducidad. Confirmar si vienen en la remisión o factura del
pedido, o si el despachador los captura una vez por lote.
3. Parámetros de stock. Se proponen 3 y 6 meses de abasto sobre el CPM; confirmar si
aplican igual a todas las claves.
4. Ventana del CPM. Se proponen los últimos 6 meses cerrados; confirmar, y si la carga
inicial debe traer el CPM histórico del Excel.
5. Detalle de las ubicaciones (sector, anaquel, nivel), para no sobrecargar la captura.
6. Integración con Contratos/FONSABI. El registro automático de pedidos y el bloqueo
automático de cancelados quedan fuera de esta iteración; confirmar en cuál se abordan.
7. Plazo para canje. Se usa 9 meses antes de la caducidad; confirmar si es fijo o varía por
proveedor o contrato.
8. Duración y velocidad de la iteración. Confirmar cuántas semanas tiene la iteración y si
HU18 a HU20 pueden pasar a un incremento posterior si la carga no alcanza.
Historias de usuario de la Iteración 2
1. Resumen de la iteración
Esta iteración se enfoca únicamente en el módulo de Almacén de Medicamentos, que Fabián
lleva como responsable principal; el módulo de Farmacia/Dispensación que lleva Rubi se
aborda en la Iteración 3. Las historias se levantaron directamente con la encargada de Almacén
en la reunión de requerimientos.

| HU  | Nombre de la  | Usuario  | Prioridad  | Pts  | Responsable  |
| --- | ------------- | -------- | ---------- | ---- | ------------ |
historia
| HU10  | Recepción de    | DESPACHADOR  | Alta  | 5   | Fabián Jiménez  |
| ----- | --------------- | ------------ | ----- | --- | --------------- |
|       | medicamento en  | DE ALMACÉN   |       |     |                 |
Almacén
| HU11  | Marcar un pedido  | ENCARGADA  | Media  | 2   | Fabián Jiménez  |
| ----- | ----------------- | ---------- | ------ | --- | --------------- |
como cancelado
HU12  Consulta y control  DESPACHADOR  Alta  3  Fabián Jiménez
|     | de existencias por  | DE ALMACÉN  |     |     |     |
| --- | ------------------- | ----------- | --- | --- | --- |
ubicación
| HU13  | Solicitud de    | PERSONAL DE  | Alta  | 3   | Fabián Jiménez  |
| ----- | --------------- | ------------ | ----- | --- | --------------- |
|       | medicamento de  | FARMACIA     |       |     |                 |
Farmacia a
Almacén
| HU14  | Validación y  | DESPACHADOR  | Alta  | 5   | Fabián Jiménez  |
| ----- | ------------- | ------------ | ----- | --- | --------------- |
|       | despacho de   | DE ALMACÉN   |       |     |                 |
solicitud por
Almacén
| HU15  | Bloqueo de      | DESPACHADOR  | Alta  | 5   | Fabián Jiménez  |
| ----- | --------------- | ------------ | ----- | --- | --------------- |
|       | despacho fuera  | DE ALMACÉN   |       |     |                 |
de orden de
caducidad
(FEFO)
| HU16  | Cálculo de  | ENCARGADA DE  | Alta  | 8   | Fabián Jiménez  |
| ----- | ----------- | ------------- | ----- | --- | --------------- |
|       | consumo     | ALMACÉN       |       |     |                 |
promedio
mensual (CPM)
por clave
HU17  Alertas de stock  ENCARGADA DE  Alta  5  Fabián Jiménez
|     | mínimo y máximo  | ALMACÉN  |     |     |     |
| --- | ---------------- | -------- | --- | --- | --- |
HU18  Registro de canje  ENCARGADA DE  Media  3  Fabián Jiménez
|       | de lote      | ALMACÉN      |        |     |                 |
| ----- | ------------ | ------------ | ------ | --- | --------------- |
| HU19  | Apartado de  | DESPACHADOR  | Media  | 3   | Fabián Jiménez  |
medicamentos
caducados

| HU20  | Registro de  | ENCARGADA DE  | Media  | 3   | Fabián Jiménez  |
| ----- | ------------ | ------------- | ------ | --- | --------------- |
|       | préstamos y  | ALMACÉN       |        |     |                 |
transferencias
con otras
instituciones
| Total de la  |     |     |     | 45  |     |
| ------------ | --- | --- | --- | --- | --- |
iteración
Carga por residente en esta iteración: Fabián Jiménez, 45 puntos; Rubi Morales, 0 puntos (Rubi
concentra su carga en la Iteración 3, de Farmacia).
2. Actores de esta iteración
| Actor  |     | Rol en esta iteración  |     | Historias relacionadas  |     |
| ------ | --- | ---------------------- | --- | ----------------------- | --- |
Despachador de Almacén  Personal que recibe, ubica y  HU10, HU11, HU12, HU14,
|     |     | despacha el medicamento en el  |     | HU15, HU18, HU19  |     |
| --- | --- | ------------------------------ | --- | ----------------- | --- |
almacén (incluye a Lauro para las
entradas).
Encargada de Almacén  Responsable del área; da  HU11, HU16, HU17, HU18,
|     |     | seguimiento a rotación, alertas de  |     | HU19, HU20  |     |
| --- | --- | ----------------------------------- | --- | ----------- | --- |
stock, canjes, caducados y
préstamos/transferencias.
| Personal de Farmacia  |     | Genera las solicitudes de  |     | HU13  |     |
| --------------------- | --- | -------------------------- | --- | ----- | --- |
medicamento hacia Almacén; no
participa en la validación ni en el
despacho.
| Supervisión  |     | Consulta en solo lectura las alertas  |     | HU17  |     |
| ------------ | --- | ------------------------------------- | --- | ----- | --- |
de stock mínimo y máximo, sin
registrar movimientos.