-- Cajas de anestesia de quirófano y quimioterapia (Farmacia).
--
-- Cajas: cada caja (vale de quirófano: salas, imagen y endoscopias, hemodinamia…) tiene un código
-- de barras y un listado fijo de claves y piezas. Al armarla, las piezas salen del estante de
-- Farmacia (farmacia_movimiento tipo CAJA_QUIROFANO) y quedan en quirofano_caja_pieza por lote;
-- siguen siendo de Farmacia (en Intelisis siguen en el almacén hasta que se usan). Lo que se usó
-- con cada paciente se registra como una entrega normal de Farmacia (receta "QX-…"), así entra
-- al resumen del día, a la baja por volumen y a la trazabilidad.

CREATE TABLE quirofano_caja (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL CONSTRAINT UX_quirofano_caja_codigo UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    servicio VARCHAR(100) NOT NULL,
    activa BIT NOT NULL CONSTRAINT DF_quirofano_caja_activa DEFAULT 1,
    creada_en DATETIME2 NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);

-- Listado fijo de la caja (lo que debe traer).
CREATE TABLE quirofano_caja_contenido (
    caja_id BIGINT NOT NULL REFERENCES quirofano_caja(id),
    medicamento_clave VARCHAR(30) NOT NULL REFERENCES medicamento(clave),
    cantidad INT NOT NULL CONSTRAINT CK_quirofano_caja_contenido_cantidad CHECK (cantidad > 0),
    orden INT NOT NULL,
    CONSTRAINT PK_quirofano_caja_contenido PRIMARY KEY (caja_id, medicamento_clave)
);

-- Lo que la caja trae ahora, por lote de Farmacia.
CREATE TABLE quirofano_caja_pieza (
    caja_id BIGINT NOT NULL REFERENCES quirofano_caja(id),
    farmacia_lote_id BIGINT NOT NULL REFERENCES farmacia_lote(id),
    piezas INT NOT NULL CONSTRAINT CK_quirofano_caja_pieza_piezas CHECK (piezas >= 0),
    CONSTRAINT PK_quirofano_caja_pieza PRIMARY KEY (caja_id, farmacia_lote_id)
);

-- Vale: cada vez que la caja sale a quirófano (por turno) y su devolución.
CREATE TABLE quirofano_vale (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    caja_id BIGINT NOT NULL REFERENCES quirofano_caja(id),
    -- MATUTINO, VESPERTINO, NOCTURNO o FIN_DE_SEMANA (fines de semana y días festivos).
    turno VARCHAR(15) NOT NULL,
    sala VARCHAR(100) NOT NULL,
    medico VARCHAR(150) NOT NULL,
    entregado_en DATETIME2 NOT NULL,
    entregado_por BIGINT NOT NULL REFERENCES usuario(id),
    recibido_en DATETIME2 NULL,
    recibido_por BIGINT NULL REFERENCES usuario(id),
    justificacion VARCHAR(500) NULL,
    indicacion_nombre VARCHAR(255) NULL,
    indicacion_pdf VARBINARY(MAX) NULL
);
CREATE INDEX IX_quirofano_vale_caja ON quirofano_vale (caja_id, entregado_en);
-- Una caja solo puede estar afuera una vez.
CREATE UNIQUE INDEX UX_quirofano_vale_abierto ON quirofano_vale (caja_id) WHERE recibido_en IS NULL;

-- Lo que se le aplicó a cada paciente (columna "Utilizado" del vale).
CREATE TABLE quirofano_vale_uso (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    vale_id BIGINT NOT NULL REFERENCES quirofano_vale(id),
    paciente_expediente VARCHAR(30) NOT NULL,
    paciente_nombre VARCHAR(250) NOT NULL,
    medicamento_clave VARCHAR(30) NOT NULL REFERENCES medicamento(clave),
    piezas INT NOT NULL CONSTRAINT CK_quirofano_vale_uso_piezas CHECK (piezas > 0),
    entrega_id BIGINT NULL REFERENCES farmacia_entrega(id)
);

-- Historial de la caja: alta, cambios de listado (con justificación), armado, entrega, devolución.
CREATE TABLE quirofano_caja_evento (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    caja_id BIGINT NOT NULL REFERENCES quirofano_caja(id),
    accion VARCHAR(30) NOT NULL,
    detalle VARCHAR(1000) NULL,
    fecha DATETIME2 NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);
CREATE INDEX IX_quirofano_caja_evento_caja ON quirofano_caja_evento (caja_id, fecha);

-- Quimioterapia: la solicitud de procedimiento (folio y expediente que trae), el paciente que se
-- verificó en Intelisis, la nota del médico tal cual y la doble verificación. Lo surtido es una
-- entrega normal de Farmacia (receta "QUIM-<folio>").
CREATE TABLE quimio_preparacion (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    folio_solicitud VARCHAR(30) NOT NULL CONSTRAINT UX_quimio_preparacion_folio UNIQUE,
    expediente_solicitud VARCHAR(30) NOT NULL,
    nombre_solicitud VARCHAR(250) NULL,
    paciente_expediente VARCHAR(30) NOT NULL,
    paciente_nombre VARCHAR(250) NOT NULL,
    paciente_curp VARCHAR(18) NULL,
    expediente_corregido BIT NOT NULL,
    justificacion_paciente VARCHAR(300) NULL,
    nota_medico VARCHAR(2000) NULL,
    entrega_id BIGINT NOT NULL REFERENCES farmacia_entrega(id),
    surtio_id BIGINT NOT NULL REFERENCES usuario(id),
    verifico_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha DATETIME2 NOT NULL
);

CREATE TABLE quimio_renglon (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    preparacion_id BIGINT NOT NULL REFERENCES quimio_preparacion(id),
    texto_medico VARCHAR(300) NULL,
    clave_indicada VARCHAR(30) NOT NULL REFERENCES medicamento(clave),
    clave_alternativa VARCHAR(30) NULL REFERENCES medicamento(clave),
    clave_surtida VARCHAR(30) NOT NULL REFERENCES medicamento(clave),
    dosis VARCHAR(100) NOT NULL,
    piezas INT NOT NULL CONSTRAINT CK_quimio_renglon_piezas CHECK (piezas > 0)
);
