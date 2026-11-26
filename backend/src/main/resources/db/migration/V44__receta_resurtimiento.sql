-- Resurtir una receta de Ventanilla ya surtida (pacientes crónicos que regresan con la misma receta):
-- se reabre con las mismas cantidades. Cada resurtido queda registrado con motivo, quién y cuándo.
CREATE TABLE farmacia_resurtimiento (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    receta_id BIGINT NOT NULL REFERENCES farmacia_receta(id),
    numero INT NOT NULL,
    motivo NVARCHAR(300) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha DATETIME2 NOT NULL
);
CREATE INDEX ix_farmacia_resurtimiento_receta ON farmacia_resurtimiento (receta_id);

-- Lo que se entrega en cada surtido (lo que indicó el médico); null = todavía no se resurte (vale piezas_indicadas).
ALTER TABLE farmacia_receta_clave ADD piezas_por_surtido INT NULL;
