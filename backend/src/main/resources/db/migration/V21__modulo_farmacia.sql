-- Módulo de Farmacia (Iteración 3, HU25 a HU35).
-- Farmacia lleva su propio inventario por lote y en PIEZAS (la unidad con que entrega a
-- los pacientes). Es independiente del de Almacén (cajas por ubicación): lo despachado
-- solo suma aquí cuando Farmacia lo recibe (HU26). En Intelisis ambos son el mismo
-- almacén 25301, por eso la comparación de la HU24 suma los dos.

-- Lote en Farmacia. lote_id lo liga al lote de Almacén del que salió (trazabilidad);
-- queda vacío si el lote solo se conoce por el alta inicial de los estantes (HU25).
CREATE TABLE farmacia_lote (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    medicamento_clave VARCHAR(30) NOT NULL REFERENCES medicamento(clave),
    numero_lote VARCHAR(50) NOT NULL,
    caducidad DATE NOT NULL,
    lote_id BIGINT NULL REFERENCES lote(id),
    piezas INT NOT NULL DEFAULT 0 CONSTRAINT CK_farmacia_lote_piezas CHECK (piezas >= 0),
    fecha_alta DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT UX_farmacia_lote UNIQUE (medicamento_clave, numero_lote)
);

-- Bitácora de todo lo que mueve piezas en Farmacia (positivo entra, negativo sale).
-- tipo: ALTA_INICIAL, RECEPCION, ENTREGA, DEVOLUCION o BAJA; referencia_id es el renglón
-- de la tabla de ese tipo que lo originó.
CREATE TABLE farmacia_movimiento (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    farmacia_lote_id BIGINT NOT NULL REFERENCES farmacia_lote(id),
    tipo VARCHAR(20) NOT NULL,
    piezas INT NOT NULL,
    referencia_id BIGINT NULL,
    fecha DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);
CREATE INDEX IX_farmacia_movimiento_lote ON farmacia_movimiento (farmacia_lote_id, fecha);

-- HU26: recepción de lo que Almacén despachó a una solicitud de Farmacia.
CREATE TABLE farmacia_recepcion (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    solicitud_id BIGINT NOT NULL REFERENCES solicitud(id),
    motivo_diferencia VARCHAR(300) NULL,
    fecha DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);

-- Una partida por cada salida de Almacén (movimiento). El índice único impide recibir
-- dos veces el mismo despacho (HU26 CA4).
CREATE TABLE farmacia_recepcion_partida (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    recepcion_id BIGINT NOT NULL REFERENCES farmacia_recepcion(id),
    movimiento_id BIGINT NOT NULL REFERENCES movimiento(id),
    farmacia_lote_id BIGINT NOT NULL REFERENCES farmacia_lote(id),
    cajas_despachadas INT NOT NULL,
    cajas_recibidas INT NOT NULL,
    piezas_recibidas INT NOT NULL,
    CONSTRAINT UX_farmacia_recepcion_movimiento UNIQUE (movimiento_id)
);

-- HU28/HU29: receta con el paciente que trae. Los datos del paciente se toman de Intelisis
-- al entregar y se guardan solo para la trazabilidad (HU33).
CREATE TABLE farmacia_receta (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    folio VARCHAR(40) NOT NULL CONSTRAINT UX_farmacia_receta_folio UNIQUE,
    paciente_nombre VARCHAR(250) NOT NULL,
    paciente_expediente VARCHAR(30) NULL,
    paciente_curp VARCHAR(18) NULL,
    -- SURTIDA, PARCIAL o NO_DISPENSADA.
    estado VARCHAR(15) NOT NULL,
    fecha DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);
CREATE INDEX IX_farmacia_receta_expediente ON farmacia_receta (paciente_expediente);
CREATE INDEX IX_farmacia_receta_estado ON farmacia_receta (estado);

CREATE TABLE farmacia_receta_clave (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    receta_id BIGINT NOT NULL REFERENCES farmacia_receta(id),
    medicamento_clave VARCHAR(30) NOT NULL REFERENCES medicamento(clave),
    piezas_indicadas INT NOT NULL CONSTRAINT CK_farmacia_receta_clave_indicadas CHECK (piezas_indicadas > 0),
    piezas_entregadas INT NOT NULL DEFAULT 0,
    no_dispensada BIT NOT NULL DEFAULT 0,
    motivo VARCHAR(300) NULL,
    CONSTRAINT UX_farmacia_receta_clave UNIQUE (receta_id, medicamento_clave)
);

-- Cada vez que se entrega algo de una receta (la primera vez o al completarla).
CREATE TABLE farmacia_entrega (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    receta_id BIGINT NOT NULL REFERENCES farmacia_receta(id),
    fecha DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);
CREATE INDEX IX_farmacia_entrega_fecha ON farmacia_entrega (fecha);

CREATE TABLE farmacia_entrega_partida (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    entrega_id BIGINT NOT NULL REFERENCES farmacia_entrega(id),
    receta_clave_id BIGINT NOT NULL REFERENCES farmacia_receta_clave(id),
    farmacia_lote_id BIGINT NOT NULL REFERENCES farmacia_lote(id),
    piezas INT NOT NULL CONSTRAINT CK_farmacia_entrega_partida_piezas CHECK (piezas > 0),
    piezas_devueltas INT NOT NULL DEFAULT 0
);
CREATE INDEX IX_farmacia_entrega_partida_lote ON farmacia_entrega_partida (farmacia_lote_id);

-- HU34: devolución ligada a una entrega. dia_resumen y en_resumen se fijan al registrarla:
-- si el día de la entrega aún no se daba de baja en Intelisis, se RESTA en ese día; si ya
-- se había dado de baja, aparece como "Devolucion Farmacia" (DEVOLUCION) en el día de hoy.
CREATE TABLE farmacia_devolucion (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    entrega_id BIGINT NOT NULL REFERENCES farmacia_entrega(id),
    motivo VARCHAR(300) NOT NULL,
    dia_resumen DATE NOT NULL,
    en_resumen VARCHAR(12) NOT NULL,
    fecha DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);

CREATE TABLE farmacia_devolucion_partida (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    devolucion_id BIGINT NOT NULL REFERENCES farmacia_devolucion(id),
    entrega_partida_id BIGINT NOT NULL REFERENCES farmacia_entrega_partida(id),
    piezas INT NOT NULL CONSTRAINT CK_farmacia_devolucion_partida_piezas CHECK (piezas > 0),
    danado BIT NOT NULL DEFAULT 0
);

-- HU35: baja por caducidad, daño o extravío (también lo dañado de una devolución).
CREATE TABLE farmacia_baja (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    farmacia_lote_id BIGINT NOT NULL REFERENCES farmacia_lote(id),
    piezas INT NOT NULL CONSTRAINT CK_farmacia_baja_piezas CHECK (piezas > 0),
    -- CADUCIDAD, DANO o EXTRAVIO.
    motivo VARCHAR(12) NOT NULL,
    detalle VARCHAR(300) NULL,
    devolucion_partida_id BIGINT NULL REFERENCES farmacia_devolucion_partida(id),
    fecha DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);
CREATE INDEX IX_farmacia_baja_fecha ON farmacia_baja (fecha);

-- HU31/HU32: el día dado de baja por volumen en Intelisis y su verificación.
CREATE TABLE farmacia_corte (
    dia DATE NOT NULL CONSTRAINT PK_farmacia_corte PRIMARY KEY,
    -- DADO_DE_BAJA o VERIFICADO (un día sin renglón está PENDIENTE).
    estado VARCHAR(15) NOT NULL,
    folio_intelisis VARCHAR(30) NULL,
    marcado_en DATETIME2 NOT NULL,
    marcado_por BIGINT NOT NULL REFERENCES usuario(id),
    verificado_en DATETIME2 NULL
);

-- HU33 CA4: cada consulta de trazabilidad (expone datos de pacientes) queda registrada.
CREATE TABLE farmacia_consulta_trazabilidad (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    modo VARCHAR(12) NOT NULL,
    valor VARCHAR(60) NOT NULL,
    fecha DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);

-- Espejo de Intelisis para la verificación (HU32): lo que se dio de baja con "Consumo
-- Farmacia" y "Devolucion Farmacia" en el almacén principal, por lote. Lo llena la
-- sincronización con los últimos días configurados.
CREATE TABLE intelisis_mov_farmacia_lote (
    inv_id INT NOT NULL,
    mov VARCHAR(30) NOT NULL,
    mov_id VARCHAR(20) NULL,
    fecha_emision DATE NOT NULL,
    articulo VARCHAR(20) NOT NULL,
    clave_base VARCHAR(20) NOT NULL,
    serie_lote VARCHAR(50) NOT NULL,
    cantidad DECIMAL(19, 4) NOT NULL,
    CONSTRAINT PK_intelisis_mov_farmacia_lote PRIMARY KEY (inv_id, articulo, serie_lote)
);
CREATE INDEX IX_intelisis_mov_farmacia_lote_fecha ON intelisis_mov_farmacia_lote (fecha_emision);
