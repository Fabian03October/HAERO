-- Seguridad del paciente: medicamentos de alto riesgo (lista ISMP: electrolitos concentrados,
-- quimioterapia, citotóxicos...) y de red fría. Va en una tabla aparte porque la sincronización
-- con Intelisis reescribe la tabla medicamento.
-- alto_riesgo NULL = se usa lo que dice Intelisis (riesgo = 'ALTO'); 1 o 0 = lo decidió Farmacia.
CREATE TABLE medicamento_seguridad (
    clave VARCHAR(30) NOT NULL CONSTRAINT PK_medicamento_seguridad PRIMARY KEY
        REFERENCES medicamento(clave),
    alto_riesgo BIT NULL,
    red_fria BIT NOT NULL CONSTRAINT DF_medicamento_seguridad_red_fria DEFAULT 0,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    actualizado_en DATETIME2 NOT NULL
);
