-- Diseño aprobado el 14/10/2026 (https://claude.ai/artifact/2fchfm3PxdQvLa7727E94c):
-- Intelisis es la fuente de los datos; HAERO agrega lo que Intelisis no tiene (códigos
-- de barras, ubicación física, cajas que llegaron) y lleva el control físico.
--
-- Paso 1: el catálogo se lee de Intelisis. No se captura nada en HAERO; la tabla
-- medicamento es la copia que usan las demás tablas (lotes, pedidos, solicitudes). Lo
-- único que HAERO le agrega a cada clave son sus códigos de barras (codigo_barras).

-- Catálogo de Farmacia tal como viene de Intelisis: claves "-1" de Art con su ficha de
-- ArtMedicamento cuando existe. clave = la de Intelisis sin el "-1".
CREATE TABLE intelisis_medicamento (
    clave VARCHAR(30) NOT NULL CONSTRAINT PK_intelisis_medicamento PRIMARY KEY,
    articulo VARCHAR(20) NOT NULL,
    nombre_generico VARCHAR(1000) NOT NULL,
    presentacion VARCHAR(1000) NOT NULL,
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
    unidades_envase VARCHAR(20) NULL
);

-- Piezas por caja de cada clave. En Intelisis la existencia de la clave "-1" está en
-- piezas; el movimiento "Conversion" pasa cajas (clave base) a piezas (clave -1) y su
-- proporción es constante por clave (Paracetamol 10, Abemaciclib 56).
--   origen: CONVERSION (de esos movimientos), FICHA (UnidadesEnvase de ArtMedicamento)
--           o SIN_DATO (no hay de dónde sacarlo; se usa 1 y queda por revisar).
CREATE TABLE intelisis_factor (
    clave VARCHAR(30) NOT NULL CONSTRAINT PK_intelisis_factor PRIMARY KEY,
    piezas_por_caja DECIMAL(19, 4) NOT NULL,
    origen VARCHAR(12) NOT NULL,
    conversiones INT NOT NULL CONSTRAINT DF_intelisis_factor_conversiones DEFAULT 0
);

-- Avisos de cambios en Intelisis (los usan los pasos siguientes: pedidos cancelados,
-- lotes que cambian, lotes por dar de alta, claves y pedidos nuevos).
-- nivel: INFO (para enterarse) o REVISAR (Intelisis cambió algo que HAERO ya movió).
CREATE TABLE aviso_intelisis (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    fecha DATETIME2 NOT NULL CONSTRAINT DF_aviso_intelisis_fecha DEFAULT SYSDATETIME(),
    tipo VARCHAR(25) NOT NULL,
    nivel VARCHAR(10) NOT NULL,
    titulo NVARCHAR(150) NOT NULL,
    detalle NVARCHAR(600) NULL,
    medicamento_clave VARCHAR(30) NULL,
    lote_id BIGINT NULL CONSTRAINT FK_aviso_intelisis_lote REFERENCES lote(id),
    pedido_id BIGINT NULL CONSTRAINT FK_aviso_intelisis_pedido REFERENCES pedido(id),
    revisado_en DATETIME2 NULL,
    revisado_por BIGINT NULL CONSTRAINT FK_aviso_intelisis_usuario REFERENCES usuario(id)
);
CREATE INDEX IX_aviso_intelisis_pendientes ON aviso_intelisis (revisado_en, nivel, fecha);

ALTER TABLE intelisis_sincronizacion ADD medicamentos INT NULL;
