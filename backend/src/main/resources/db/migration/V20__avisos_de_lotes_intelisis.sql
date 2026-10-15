-- Paso 4 del diseño aprobado: avisos de lotes y comparación en cajas.
--
-- Último estado que HAERO vio de cada lote con existencia en el almacén de
-- medicamentos de Intelisis. Con esto la sincronización sabe qué cambió desde la
-- vez anterior y deja un aviso, sin tocar nunca lo que Almacén registró en HAERO:
--   - LOTE_CAMBIO (por revisar): Intelisis cambió la caducidad de un lote que
--     Almacén ya tiene en HAERO.
--   - LOTE_POR_DAR_DE_ALTA (informativo): apareció en Intelisis un lote nuevo de
--     una clave dada de alta en HAERO y Almacén todavía no lo recibe.
CREATE TABLE intelisis_lote_visto (
    clave VARCHAR(30) NOT NULL,
    serie_lote VARCHAR(50) NOT NULL,
    caducidad DATE NULL,
    piezas DECIMAL(19, 4) NOT NULL,
    primera_vez DATETIME2 NOT NULL CONSTRAINT DF_intelisis_lote_visto_primera DEFAULT SYSDATETIME(),
    visto_en DATETIME2 NOT NULL CONSTRAINT DF_intelisis_lote_visto_visto DEFAULT SYSDATETIME(),
    CONSTRAINT PK_intelisis_lote_visto PRIMARY KEY (clave, serie_lote)
);

-- Para ligar rápido cada lote de HAERO con su lote de Intelisis.
CREATE INDEX IX_lote_clave_lote_intelisis ON lote (medicamento_clave, lote_intelisis);
