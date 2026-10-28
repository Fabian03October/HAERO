-- Hospitalización: lo que hace falta para imprimir "Indicaciones médicas activas" igual que
-- Intelisis, avisar quién indicó o suspendió cada medicamento y la hoja de baja propia de HAERO.
-- Las indicaciones generales, la dieta, el sexo y la fecha de nacimiento NO se guardan: se leen
-- de Intelisis al imprimir. Los medicamentos sí se guardan (se surten y se rastrean).

ALTER TABLE hosp_medicamento ADD
    nota NVARCHAR(1000) NULL,
    folio INT NULL,
    externo BIT NULL,
    dosis_24h INT NULL,
    -- Dosis única: en la hoja sale "Unidosis" en lugar de "Cada N horas".
    unidosis BIT NULL,
    inicia DATETIME2 NULL,
    -- Medicamento por infusión continua (soluciones: "1000 ml para 8 horas").
    infusion BIT NULL,
    cantidad_hora REAL NULL,
    numero_horas INT NULL,
    -- Usuario de Intelisis que lo indicó y el que lo suspendió.
    indico VARCHAR(20) NULL,
    suspendio VARCHAR(20) NULL;

-- Quién causó la novedad (médico) y de qué clave es, para avisar con nombre.
ALTER TABLE hosp_evento ADD
    medico VARCHAR(20) NULL,
    clave VARCHAR(40) NULL;

-- Médicos de Intelisis (vsa_RecursoHumano) que aparecen en las indicaciones: personal del
-- hospital, no pacientes.
CREATE TABLE hosp_medico (
    usuario VARCHAR(20) NOT NULL CONSTRAINT PK_hosp_medico PRIMARY KEY,
    nombre NVARCHAR(200) NULL,
    cedula VARCHAR(50) NULL,
    especialidad NVARCHAR(100) NULL,
    actualizado_en DATETIME2 NOT NULL
);

-- Hoja de baja ("Consumo Farmacia") propia de HAERO: una por paciente y día, con folio
-- consecutivo, que se asigna al terminar el carrito. Al capturar la baja en Intelisis se escribe
-- este folio en "Observaciones" para ligarla.
CREATE TABLE farmacia_hoja_baja (
    folio INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_farmacia_hoja_baja PRIMARY KEY,
    receta_id BIGINT NOT NULL REFERENCES farmacia_receta(id),
    dia DATE NOT NULL,
    central VARCHAR(10) NOT NULL,
    espacio VARCHAR(10) NULL,
    fecha DATETIME2 NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    CONSTRAINT UX_farmacia_hoja_baja_receta UNIQUE (receta_id)
);
CREATE INDEX IX_farmacia_hoja_baja_dia ON farmacia_hoja_baja (dia, central);
