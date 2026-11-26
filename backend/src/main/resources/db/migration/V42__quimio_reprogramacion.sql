-- Quimioterapia: el paciente no puede pasar el día programado. Farmacia llama a Oncología Médica,
-- anota la nueva fecha y el motivo. Intelisis no se toca (solo lectura): la fecha que manda en HAERO
-- es la última reprogramación de la solicitud.
CREATE TABLE quimio_reprogramacion (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    folio INT NOT NULL,
    fecha_anterior DATETIME2 NULL,
    fecha_nueva DATE NOT NULL,
    motivo NVARCHAR(300) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha DATETIME2 NOT NULL
);
CREATE INDEX ix_quimio_reprogramacion_folio ON quimio_reprogramacion (folio);
