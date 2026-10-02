-- HU16 (CPM) y HU17 (alertas de stock minimo/maximo), calculados en el
-- servidor para que Almacen y Supervision vean siempre el mismo numero.
-- El CPM no se guarda: se calcula al vuelo con las salidas reales (tipo
-- SALIDA, despachos a Farmacia). Mientras una clave no tenga salidas
-- propias registradas, se usa como respaldo el consumo promedio de la hoja
-- de carga inicial (columna CONSUMO PROM. del Excel/Drive de Almacen).
-- Las alertas si se persisten, con fecha_inicio y fecha_fin, para que sean
-- consultables sin que nadie este conectado cuando se generan (HU17 CA4/CA5).

CREATE TABLE alerta (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    medicamento_clave VARCHAR(30) NOT NULL REFERENCES medicamento(clave),
    tipo VARCHAR(12) NOT NULL CHECK (tipo IN ('DESABASTO', 'SOBREABASTO')),
    fecha_inicio DATETIME2 NOT NULL DEFAULT GETDATE(),
    fecha_fin DATETIME2 NULL,
    -- Fotografia del momento en que se abrio la alerta, para el historial (HU17 CA5).
    existencia_al_inicio INT NOT NULL,
    limite_al_inicio FLOAT NOT NULL
);

ALTER TABLE medicamento ADD consumo_promedio_hoja FLOAT NULL;
