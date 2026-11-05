-- Ajustes de Quirófano y Quimioterapia (revisión de incongruencias).

-- Quirófano: los de red fría están en el vale pero NO se guardan en la caja; Farmacia los saca del
-- refrigerador al entregar la caja y lo que regresa sin usar vuelve al estante.
ALTER TABLE quirofano_caja_contenido ADD aparte BIT NOT NULL CONSTRAINT DF_quirofano_caja_contenido_aparte DEFAULT 0;

-- Quirófano: el médico anestesiólogo se elige de los médicos de Intelisis (hosp_medico) cuando existe.
ALTER TABLE quirofano_vale ADD medico_usuario VARCHAR(20) NULL;

-- Quirófano: una indicación médica (PDF) por paciente, no una por vale.
CREATE TABLE quirofano_vale_indicacion (
    vale_id BIGINT NOT NULL REFERENCES quirofano_vale(id),
    paciente_expediente VARCHAR(30) NOT NULL,
    documento_nombre VARCHAR(255) NOT NULL,
    documento VARBINARY(MAX) NOT NULL,
    CONSTRAINT PK_quirofano_vale_indicacion PRIMARY KEY (vale_id, paciente_expediente)
);

-- Quimioterapia: una solicitud puede cubrir varias sesiones o ciclos; cada surtido es una sesión.
ALTER TABLE quimio_preparacion DROP CONSTRAINT UX_quimio_preparacion_folio;
ALTER TABLE quimio_preparacion ADD sesion INT NOT NULL CONSTRAINT DF_quimio_preparacion_sesion DEFAULT 1;
ALTER TABLE quimio_preparacion ADD CONSTRAINT UX_quimio_preparacion_folio_sesion UNIQUE (folio_solicitud, sesion);
-- Si Intelisis no responde, el paciente se confirma con el expediente físico y queda justificado.
ALTER TABLE quimio_preparacion ADD sin_intelisis BIT NOT NULL CONSTRAINT DF_quimio_preparacion_sin_intelisis DEFAULT 0;

-- Quimioterapia: la dosis en mg (o la unidad que indique el médico) contra lo que trae cada frasco,
-- para saber qué sobra y qué se hace con el sobrante; y por qué se surtió la alternativa.
ALTER TABLE quimio_renglon ADD dosis_cantidad DECIMAL(12, 3) NULL;
ALTER TABLE quimio_renglon ADD dosis_unidad VARCHAR(10) NULL;
ALTER TABLE quimio_renglon ADD contenido_por_pieza DECIMAL(12, 3) NULL;
-- MERMA (se desecha) o RESGUARDO (se guarda la fracción, si su estabilidad lo permite).
ALTER TABLE quimio_renglon ADD destino_sobrante VARCHAR(10) NULL;
ALTER TABLE quimio_renglon ADD motivo_alternativa VARCHAR(300) NULL;
