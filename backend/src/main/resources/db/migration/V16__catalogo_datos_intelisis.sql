-- El catálogo de medicamentos se llenará desde Intelisis (tablas Art y ArtMedicamento).
-- Se agregan los datos que se muestran en el catálogo y en su detalle. Todos aceptan
-- nulos porque las claves capturadas a mano en HAERO no los traen.
--
-- Nombre genérico y presentación se amplían al tamaño que usa Intelisis
-- (VARCHAR(1000)): algunos nombres del Cuadro Básico pasan de 150 caracteres.

ALTER TABLE medicamento ALTER COLUMN nombre_generico VARCHAR(1000) NOT NULL;
ALTER TABLE medicamento ALTER COLUMN presentacion VARCHAR(1000) NOT NULL;

ALTER TABLE medicamento ADD
    forma_farmaceutica VARCHAR(200) NULL,
    concentracion VARCHAR(2000) NULL,
    grupo_terapeutico VARCHAR(100) NULL,
    cuadro_basico BIT NULL,
    estatus VARCHAR(15) NULL,
    clave_cucop VARCHAR(20) NULL,
    unidad_compra VARCHAR(50) NULL,
    unidad_minima VARCHAR(50) NULL,
    riesgo VARCHAR(30) NULL,
    principal_indicacion VARCHAR(1000) NULL,
    contraindicaciones VARCHAR(1000) NULL,
    descripcion_completa VARCHAR(255) NULL,
    -- HAERO = capturada aquí; INTELISIS = la trae la sincronización.
    origen VARCHAR(10) NOT NULL CONSTRAINT DF_medicamento_origen DEFAULT 'HAERO',
    sincronizado_en DATETIME2 NULL;
