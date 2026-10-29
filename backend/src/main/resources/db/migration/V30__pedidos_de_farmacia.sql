-- HU39: un pedido de Farmacia con varias claves. Cada clave sigue siendo una solicitud (la
-- recepción en Farmacia, la trazabilidad y los cortes no cambian); el pedido es su encabezado.
-- Las solicitudes anteriores quedan sin pedido y se ven como pedidos de una sola clave.
CREATE TABLE solicitud_pedido (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_solicitud_pedido PRIMARY KEY,
    fecha DATETIME2 NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    observaciones NVARCHAR(300) NULL
);
ALTER TABLE solicitud ADD pedido_id BIGINT NULL REFERENCES solicitud_pedido(id);
CREATE INDEX IX_solicitud_pedido ON solicitud (pedido_id);
