-- Dónde está cada lote en Farmacia (estante), igual que en Almacén. Se sugiere al recibir y se puede cambiar;
-- cada cambio queda registrado con quién, cuándo y de dónde a dónde.
ALTER TABLE farmacia_lote ADD ubicacion NVARCHAR(40) NULL;

CREATE TABLE farmacia_cambio_ubicacion (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    farmacia_lote_id BIGINT NOT NULL REFERENCES farmacia_lote(id),
    anterior NVARCHAR(40) NULL,
    nueva NVARCHAR(40) NOT NULL,
    -- ALTA_INICIAL, RECEPCION o CAMBIO (lo movieron en el inventario).
    origen NVARCHAR(20) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha DATETIME2 NOT NULL
);
CREATE INDEX ix_farmacia_cambio_ubicacion_lote ON farmacia_cambio_ubicacion (farmacia_lote_id);
