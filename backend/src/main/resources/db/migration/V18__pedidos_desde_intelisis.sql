-- Diseño aprobado, paso 2: los pedidos se leen de Intelisis. No se captura nada en
-- HAERO (HU22 y HU11 pasan a consulta). HAERO solo guarda lo que Almacén recibió
-- físicamente contra cada pedido (pedido_detalle.cantidad_recibida, que la
-- sincronización nunca toca). Un pedido o partida cancelada en Intelisis no se deja
-- recibir y deja un aviso.

-- Pedidos a proveedor de Intelisis ("Pedido Proveedor" del almacén 25301, último año).
CREATE TABLE intelisis_pedido (
    compra_id INT NOT NULL CONSTRAINT PK_intelisis_pedido PRIMARY KEY,
    mov_id VARCHAR(20) NOT NULL,
    estatus VARCHAR(15) NOT NULL,
    fecha_emision DATE NULL,
    fecha_entrega DATE NULL,
    fecha_cancelacion DATETIME2 NULL,
    proveedor VARCHAR(20) NULL,
    contrato_id INT NULL,
    -- Número de contrato (por ejemplo IB-HRAEO-AD-B-C-310-2025).
    contrato VARCHAR(60) NULL,
    referencia VARCHAR(100) NULL
);

-- Partidas de medicamento de esos pedidos, una por clave. Cantidades en cajas.
CREATE TABLE intelisis_pedido_partida (
    compra_id INT NOT NULL,
    clave VARCHAR(30) NOT NULL,
    cucop VARCHAR(20) NULL,
    cantidad DECIMAL(19, 4) NOT NULL,
    pendiente DECIMAL(19, 4) NOT NULL,
    cancelada DECIMAL(19, 4) NOT NULL,
    CONSTRAINT PK_intelisis_pedido_partida PRIMARY KEY (compra_id, clave)
);

-- Pedido de HAERO = copia del de Intelisis (ya no se capturan pedidos).
ALTER TABLE pedido ADD origen VARCHAR(10) NOT NULL CONSTRAINT DF_pedido_origen DEFAULT 'HAERO';
ALTER TABLE pedido ADD compra_id_intelisis INT NULL;
ALTER TABLE pedido ADD contrato VARCHAR(60) NULL;
ALTER TABLE pedido ADD fecha_entrega DATE NULL;
-- PENDIENTE, CONCLUIDO o CANCELADO, tal como está en Intelisis.
ALTER TABLE pedido ADD estatus_intelisis VARCHAR(15) NULL;
CREATE INDEX IX_pedido_compra_intelisis ON pedido (compra_id_intelisis);

-- Por partida: lo que dice Intelisis. cantidad_recibida sigue siendo lo que recibió HAERO.
ALTER TABLE pedido_detalle ADD cucop VARCHAR(20) NULL;
ALTER TABLE pedido_detalle ADD recibida_intelisis INT NULL;
ALTER TABLE pedido_detalle ADD cancelada_intelisis INT NULL;
CREATE INDEX IX_pedido_detalle_pedido_clave ON pedido_detalle (pedido_id, medicamento_clave);

ALTER TABLE intelisis_sincronizacion ADD pedidos INT NULL;
