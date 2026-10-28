-- Hospitalización: camas por área (central de enfermería), quién está en cada cama y los
-- medicamentos de sus indicaciones, leídos de Intelisis cada 5 minutos.
--
-- Intelisis no avisa de los cambios: HAERO guarda la última foto de los internados y la
-- compara con la nueva para registrar novedades (ingreso, egreso, cambio de cama,
-- medicamento agregado o suspendido). Son datos clínicos: solo los ven Farmacia y
-- Supervisión con las políticas aceptadas, cada consulta del detalle queda registrada y
-- lo de pacientes egresados se borra a los 30 días. No se copian diagnósticos ni
-- indicaciones generales; solo medicamentos.

-- Camas activas de cada central de enfermería (vsa_CenEnfEspacio + Espacio).
CREATE TABLE hosp_cama (
    espacio VARCHAR(10) NOT NULL CONSTRAINT PK_hosp_cama PRIMARY KEY,
    nombre NVARCHAR(100) NULL,
    tipo NVARCHAR(50) NULL,
    central VARCHAR(10) NOT NULL,
    actualizado_en DATETIME2 NOT NULL
);
CREATE INDEX IX_hosp_cama_central ON hosp_cama (central);

-- Estancia de un paciente en una cama (vsa_IngArea ligado a vsa_Ingreso y Cte).
CREATE TABLE hosp_estancia (
    id_ing_area INT NOT NULL CONSTRAINT PK_hosp_estancia PRIMARY KEY,
    id_ingreso INT NOT NULL,
    espacio VARCHAR(10) NULL,
    servicio NVARCHAR(100) NULL,
    expediente VARCHAR(20) NULL,
    paciente NVARCHAR(250) NULL,
    fecha_ingreso DATETIME2 NULL,
    activa BIT NOT NULL,
    -- Cuándo HAERO dejó de verla activa en Intelisis (egreso o cambio de cama).
    egreso_detectado DATETIME2 NULL,
    actualizado_en DATETIME2 NOT NULL
);
CREATE INDEX IX_hosp_estancia_espacio ON hosp_estancia (espacio, activa);
CREATE INDEX IX_hosp_estancia_ingreso ON hosp_estancia (id_ingreso);

-- Medicamentos de las indicaciones de los internados (vsa_IndMedicaMedicamento).
CREATE TABLE hosp_medicamento (
    id INT NOT NULL CONSTRAINT PK_hosp_medicamento PRIMARY KEY,
    id_ind_medica INT NOT NULL,
    id_ingreso INT NOT NULL,
    clave VARCHAR(40) NULL,
    medicamento NVARCHAR(255) NULL,
    via VARCHAR(20) NULL,
    dosis REAL NULL,
    unidad VARCHAR(10) NULL,
    cada INT NULL,
    cada_unidad VARCHAR(10) NULL,
    indicado_en DATETIME2 NULL,
    activo BIT NOT NULL,
    suspendido_en DATETIME2 NULL,
    actualizado_en DATETIME2 NOT NULL
);
CREATE INDEX IX_hosp_medicamento_ingreso ON hosp_medicamento (id_ingreso, activo);

-- Novedades detectadas al comparar con la foto anterior.
-- tipo: INGRESO, EGRESO, CAMBIO_CAMA, MEDICAMENTO_NUEVO o MEDICAMENTO_SUSPENDIDO.
CREATE TABLE hosp_evento (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    fecha DATETIME2 NOT NULL,
    tipo VARCHAR(25) NOT NULL,
    id_ingreso INT NOT NULL,
    espacio VARCHAR(10) NULL,
    central VARCHAR(10) NULL,
    detalle NVARCHAR(400) NOT NULL
);
CREATE INDEX IX_hosp_evento_espacio ON hosp_evento (espacio, fecha);
CREATE INDEX IX_hosp_evento_fecha ON hosp_evento (fecha);

-- Hasta cuándo vio cada usuario las novedades de cada cama (para el aviso).
CREATE TABLE hosp_visto (
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    espacio VARCHAR(10) NOT NULL,
    visto_hasta DATETIME2 NOT NULL,
    CONSTRAINT PK_hosp_visto PRIMARY KEY (usuario_id, espacio)
);

-- Bitácora: quién abrió el detalle de qué cama y paciente.
CREATE TABLE hosp_consulta (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    espacio VARCHAR(10) NOT NULL,
    fecha DATETIME2 NOT NULL
);

-- Bitácora de cada lectura de Intelisis.
CREATE TABLE hosp_sincronizacion (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    inicio DATETIME2 NOT NULL,
    fin DATETIME2 NULL,
    estatus VARCHAR(10) NOT NULL,
    camas INT NULL,
    estancias INT NULL,
    medicamentos INT NULL,
    eventos INT NULL,
    error NVARCHAR(1000) NULL
);
