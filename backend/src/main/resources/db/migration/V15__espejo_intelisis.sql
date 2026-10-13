-- Integración de solo lectura con Intelisis (iteración de integración, pasos 1 a 3).
-- Estas tablas son una copia ("espejo") de lo que hay en Intelisis. Las llena el
-- proceso de sincronización en segundo plano; las pantallas de HAERO leen de aquí
-- y nunca consultan Intelisis directamente, así una consulta lenta del ERP o una
-- caída de la red no frena el sistema.
--
-- No tienen llaves foráneas hacia medicamento ni lote: todavía no reemplazan la
-- carga por Excel (HU21). Eso espera la decisión de unidades de Almacén/Farmacia.
--
-- En Intelisis la clave de Farmacia es la que termina en "-1"
-- (010.000.0101.00-1); la compra entra con la clave sin "-1" y un movimiento
-- "Conversion" la pasa a la "-1". clave_base guarda la clave sin el sufijo para
-- poder compararla con la clave de HAERO.

-- Catálogo de medicamentos (Art).
CREATE TABLE intelisis_articulo (
    articulo VARCHAR(20) NOT NULL CONSTRAINT PK_intelisis_articulo PRIMARY KEY,
    clave_base VARCHAR(20) NOT NULL,
    descripcion NVARCHAR(500) NULL,
    unidad VARCHAR(50) NULL,
    unidad_compra VARCHAR(50) NULL,
    -- Cantidad de la unidad por pieza (p. ej. 10 en un frasco de 10 mg). Parece ser
    -- el factor de la "Conversion"; pendiente de confirmar con Almacén.
    unidad_cantidad FLOAT NULL,
    grupo VARCHAR(50) NULL,
    estatus VARCHAR(15) NULL
);
CREATE INDEX IX_intelisis_articulo_clave_base ON intelisis_articulo (clave_base);

-- Proveedores (Prov). Solo los datos que HAERO usa; nada de contactos ni datos legales.
CREATE TABLE intelisis_proveedor (
    proveedor VARCHAR(20) NOT NULL CONSTRAINT PK_intelisis_proveedor PRIMARY KEY,
    nombre NVARCHAR(150) NULL,
    rfc VARCHAR(20) NULL,
    estatus VARCHAR(15) NULL
);

-- Existencia por lote (SerieLote) de los almacenes configurados (25301 y 25301-C).
-- La cantidad está en la unidad de Intelisis (piezas, mg, ml, UI...), sin convertir.
CREATE TABLE intelisis_existencia_lote (
    almacen VARCHAR(10) NOT NULL,
    articulo VARCHAR(20) NOT NULL,
    clave_base VARCHAR(20) NOT NULL,
    serie_lote VARCHAR(50) NOT NULL,
    existencia DECIMAL(19, 4) NOT NULL,
    fecha_caducidad DATE NULL,
    ultima_entrada DATE NULL,
    ultima_salida DATE NULL,
    CONSTRAINT PK_intelisis_existencia_lote PRIMARY KEY (almacen, articulo, serie_lote)
);
CREATE INDEX IX_intelisis_existencia_lote_clave_base ON intelisis_existencia_lote (clave_base, almacen);

-- Recepciones de proveedor ("Ent. Compra Farmacia" en Compra, solo CONCLUIDO).
CREATE TABLE intelisis_recepcion (
    compra_id INT NOT NULL CONSTRAINT PK_intelisis_recepcion PRIMARY KEY,
    mov_id VARCHAR(20) NULL,
    fecha_emision DATE NULL,
    almacen VARCHAR(10) NULL,
    proveedor VARCHAR(20) NULL,
    -- Normalmente "Pedido Proveedor" + su número: el enlace con los pedidos (más adelante).
    origen VARCHAR(30) NULL,
    origen_id VARCHAR(20) NULL,
    contrato_id INT NULL
);
CREATE INDEX IX_intelisis_recepcion_fecha ON intelisis_recepcion (fecha_emision);

-- Lotes que entraron con cada recepción (SerieLoteMov, módulo COMS).
CREATE TABLE intelisis_recepcion_lote (
    compra_id INT NOT NULL,
    articulo VARCHAR(20) NOT NULL,
    serie_lote VARCHAR(50) NOT NULL,
    cantidad DECIMAL(19, 4) NOT NULL,
    fecha_caducidad DATE NULL,
    CONSTRAINT PK_intelisis_recepcion_lote PRIMARY KEY (compra_id, articulo, serie_lote)
);
CREATE INDEX IX_intelisis_recepcion_lote_articulo ON intelisis_recepcion_lote (articulo);

-- Bitácora de cada sincronización (una fila por corrida).
CREATE TABLE intelisis_sincronizacion (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    inicio DATETIME2 NOT NULL,
    fin DATETIME2 NULL,
    -- EN_CURSO, OK o ERROR.
    estatus VARCHAR(10) NOT NULL,
    -- AUTOMATICA (programada) o MANUAL (botón).
    origen VARCHAR(10) NOT NULL,
    articulos INT NULL,
    proveedores INT NULL,
    lotes_existencia INT NULL,
    recepciones INT NULL,
    lotes_recepcion INT NULL,
    duracion_ms BIGINT NULL,
    error NVARCHAR(1000) NULL
);
