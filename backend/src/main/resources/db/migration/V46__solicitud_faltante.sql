-- Lo que Almacén no surtió de una solicitud (parcial o sin existencia): cuántas cajas faltaron, por qué,
-- quién y cuándo. Un renglón por despacho (no se sobrescribe); sale en el vale del pedido.
CREATE TABLE solicitud_faltante (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    solicitud_id BIGINT NOT NULL REFERENCES solicitud(id),
    cajas INT NOT NULL,
    motivo NVARCHAR(300) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha DATETIME2 NOT NULL
);
CREATE INDEX ix_solicitud_faltante_solicitud ON solicitud_faltante (solicitud_id);
