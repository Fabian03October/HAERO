-- Conteo del inventario inicial de Almacén (HU21) y de Farmacia (HU25) en dos tiempos:
-- primero se registran los códigos y lotes de cada anaquel; después el personal captura
-- las cantidades. Al guardarse se da de alta todo de una vez y el área queda cerrada:
-- la fila de conteo_inicial impide un segundo conteo inicial.
CREATE TABLE conteo_inicial (
    area VARCHAR(10) NOT NULL CONSTRAINT PK_conteo_inicial PRIMARY KEY,
    cerrado_en DATETIME2 NOT NULL,
    cerrado_por BIGINT NOT NULL REFERENCES usuario(id),
    CONSTRAINT CK_conteo_inicial_area CHECK (area IN ('ALMACEN', 'FARMACIA'))
);

CREATE TABLE conteo_inicial_renglon (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_conteo_inicial_renglon PRIMARY KEY,
    area VARCHAR(10) NOT NULL,
    codigo VARCHAR(60) NOT NULL,
    medicamento_clave VARCHAR(30) NOT NULL REFERENCES medicamento(clave),
    lote VARCHAR(50) NOT NULL,
    caducidad DATE NOT NULL,
    -- Anaquel o estante (obligatorio en Almacén).
    ubicacion VARCHAR(40) NULL,
    -- Cajas en Almacén, piezas en Farmacia. Vacía hasta que la capturan; 0 = no había.
    cantidad INT NULL,
    registrado_por BIGINT NOT NULL REFERENCES usuario(id),
    registrado_en DATETIME2 NOT NULL,
    contado_por BIGINT NULL REFERENCES usuario(id),
    contado_en DATETIME2 NULL,
    CONSTRAINT CK_conteo_inicial_renglon_area CHECK (area IN ('ALMACEN', 'FARMACIA')),
    CONSTRAINT CK_conteo_inicial_renglon_cantidad CHECK (cantidad IS NULL OR cantidad >= 0)
);

-- Un mismo lote una sola vez por ubicación.
CREATE UNIQUE INDEX UX_conteo_inicial_renglon ON conteo_inicial_renglon (area, medicamento_clave, lote, ubicacion);
