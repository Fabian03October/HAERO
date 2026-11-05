-- Quimioterapia: solicitudes de procedimiento a Oncología Médica leídas de Intelisis cada 5 minutos
-- (vsa_AuxTxSol), para que Farmacia no vuelva a escribir lo que ya capturó el médico.
--
-- El "Folio" que trae la hoja impresa (FolioInterno en Intelisis) es vsa_AuxTxSol.ID_AuxTxSol; la
-- columna Folio de Intelisis casi siempre está vacía. El medicamento solo viene escrito en Notas, sin
-- clave: Farmacia lo pasa a clave al surtir (quimio_preparacion / quimio_renglon).
--
-- Son datos clínicos: solo Farmacia con las políticas aceptadas, cada consulta del detalle queda en
-- quimio_consulta y solo se guardan las solicitudes recientes (lo que sale de la ventana se borra).
CREATE TABLE quimio_solicitud (
    folio INT NOT NULL CONSTRAINT PK_quimio_solicitud PRIMARY KEY,
    fecha DATETIME2 NULL,
    fecha_requerida DATETIME2 NULL,
    estatus VARCHAR(20) NULL,
    expediente VARCHAR(30) NULL,
    paciente NVARCHAR(250) NULL,
    servicio_solicita NVARCHAR(100) NULL,
    diagnostico NVARCHAR(1000) NULL,
    notas NVARCHAR(MAX) NULL,
    medico_usuario VARCHAR(20) NULL,
    medico NVARCHAR(200) NULL,
    actualizado_en DATETIME2 NOT NULL
);
CREATE INDEX IX_quimio_solicitud_requerida ON quimio_solicitud (fecha_requerida);
CREATE INDEX IX_quimio_solicitud_expediente ON quimio_solicitud (expediente);

-- Quién abrió qué solicitud (privacidad).
CREATE TABLE quimio_consulta (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    folio INT NOT NULL,
    fecha DATETIME2 NOT NULL
);

-- Bitácora de cada lectura de Intelisis.
CREATE TABLE quimio_sincronizacion (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    inicio DATETIME2 NOT NULL,
    fin DATETIME2 NULL,
    estatus VARCHAR(10) NOT NULL,
    solicitudes INT NULL,
    error NVARCHAR(1000) NULL
);
