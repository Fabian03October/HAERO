CREATE TABLE medicamento (
    clave VARCHAR(10) PRIMARY KEY,
    nombre_generico VARCHAR(150) NOT NULL,
    presentacion VARCHAR(80) NOT NULL,
    piezas_por_caja INT NULL,
    descripcion VARCHAR(200) NULL
);

CREATE TABLE codigo_barras (
    codigo VARCHAR(60) PRIMARY KEY,
    medicamento_clave VARCHAR(10) NOT NULL REFERENCES medicamento(clave),
    proveedor VARCHAR(120) NOT NULL
);

CREATE TABLE pedido (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    numero_pedido VARCHAR(30) NOT NULL UNIQUE,
    proveedor VARCHAR(120) NOT NULL,
    estatus VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    fecha DATE NOT NULL,
    motivo_cancelacion VARCHAR(200) NULL,
    fecha_cancelacion DATETIME2 NULL,
    usuario_cancelacion_id BIGINT NULL REFERENCES usuario(id)
);

CREATE TABLE pedido_detalle (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedido(id),
    medicamento_clave VARCHAR(10) NOT NULL REFERENCES medicamento(clave),
    cantidad_esperada INT NOT NULL,
    cantidad_recibida INT NOT NULL DEFAULT 0
);

CREATE TABLE lote (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    medicamento_clave VARCHAR(10) NOT NULL REFERENCES medicamento(clave),
    numero_lote VARCHAR(40) NOT NULL,
    caducidad DATE NOT NULL,
    proveedor VARCHAR(120) NOT NULL,
    estatus VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',
    pedido_id BIGINT NULL REFERENCES pedido(id)
);

CREATE TABLE existencia (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    lote_id BIGINT NOT NULL REFERENCES lote(id),
    ubicacion VARCHAR(40) NOT NULL,
    cantidad_cajas INT NOT NULL DEFAULT 0 CHECK (cantidad_cajas >= 0)
);

CREATE TABLE movimiento (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    existencia_id BIGINT NOT NULL REFERENCES existencia(id),
    tipo VARCHAR(20) NOT NULL,
    cantidad_cajas INT NOT NULL,
    fecha DATETIME2 NOT NULL DEFAULT GETDATE(),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);
