-- Baja por volumen: en Intelisis se captura UNA baja por área y día (totales por clave y lote); el
-- detalle por paciente se queda solo en HAERO. Cada "Terminar carrito" (el del día o uno de
-- urgencia) suma a la baja del día las entregas del área que todavía no estaban en ella.
CREATE TABLE farmacia_baja_volumen (
    folio INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_farmacia_baja_volumen PRIMARY KEY,
    dia DATE NOT NULL,
    central VARCHAR(10) NOT NULL,
    fecha DATETIME2 NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    CONSTRAINT UX_farmacia_baja_volumen_dia UNIQUE (dia, central)
);

ALTER TABLE farmacia_entrega ADD baja_volumen_folio INT NULL REFERENCES farmacia_baja_volumen(folio);
