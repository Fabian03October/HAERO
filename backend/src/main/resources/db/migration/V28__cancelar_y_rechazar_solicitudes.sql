-- HU40: Farmacia cancela lo que ya no necesita y Almacén rechaza lo que no puede surtir.
-- Queda quién, cuándo y por qué; lo ya despachado no se toca.
ALTER TABLE solicitud ADD
    motivo_cierre NVARCHAR(300) NULL,
    cerrada_por BIGINT NULL REFERENCES usuario(id),
    fecha_cierre DATETIME2 NULL;
