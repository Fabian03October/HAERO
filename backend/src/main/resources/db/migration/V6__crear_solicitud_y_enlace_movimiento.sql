CREATE TABLE solicitud (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    medicamento_clave VARCHAR(10) NOT NULL REFERENCES medicamento(clave),
    cantidad_solicitada INT NOT NULL,
    cantidad_atendida INT NOT NULL DEFAULT 0,
    estatus VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha DATETIME2 NOT NULL DEFAULT GETDATE(),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);

ALTER TABLE movimiento ADD solicitud_id BIGINT NULL REFERENCES solicitud(id);
