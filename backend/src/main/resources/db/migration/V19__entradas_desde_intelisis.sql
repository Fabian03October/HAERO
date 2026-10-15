-- Paso 3 del diseño aprobado: la entrada (HU10) y el alta inicial (HU21) se eligen
-- de un registro de Intelisis; en HAERO ya no se teclean lote ni caducidad.

-- Otras entradas al almacén de medicamentos, además de las compras (Inv): donaciones
-- (FONSABI, Farmacia) y traspasos (IMSS). Copia de consulta, como el resto del espejo.
CREATE TABLE intelisis_entrada_inv (
    inv_id INT NOT NULL CONSTRAINT PK_intelisis_entrada_inv PRIMARY KEY,
    mov VARCHAR(30) NOT NULL,
    mov_id VARCHAR(20) NULL,
    fecha_emision DATE NULL,
    proveedor VARCHAR(20) NULL,
    referencia NVARCHAR(100) NULL,
    concepto NVARCHAR(100) NULL
);
CREATE INDEX IX_intelisis_entrada_inv_fecha ON intelisis_entrada_inv (fecha_emision);

-- Lotes de esas entradas (SerieLoteMov, módulo INV). La cantidad viene en piezas.
CREATE TABLE intelisis_entrada_inv_lote (
    inv_id INT NOT NULL,
    articulo VARCHAR(20) NOT NULL,
    clave_base VARCHAR(20) NOT NULL,
    serie_lote VARCHAR(50) NOT NULL,
    cantidad DECIMAL(19, 4) NOT NULL,
    fecha_caducidad DATE NULL,
    CONSTRAINT PK_intelisis_entrada_inv_lote PRIMARY KEY (inv_id, articulo, serie_lote)
);
CREATE INDEX IX_intelisis_entrada_inv_lote_clave ON intelisis_entrada_inv_lote (clave_base);

ALTER TABLE intelisis_sincronizacion ADD entradas_inv INT NULL;

-- "Foto" de Intelisis en cada lote de HAERO: de qué registro salió y qué decía Intelisis
-- en ese momento. Si después Intelisis cambia, la foto no se mueve (trazabilidad).
ALTER TABLE lote ADD
    -- COMPRA (Ent. Compra), INV (donación o traspaso) o EXISTENCIA (alta inicial).
    origen_intelisis VARCHAR(12) NULL,
    -- Compra.ID o Inv.ID; vacío en el alta inicial.
    id_intelisis INT NULL,
    -- Texto del movimiento, p. ej. "Ent. Compra Farmacia 5678" o "Donacion FONSABI 1234".
    movimiento_intelisis VARCHAR(60) NULL,
    referencia_intelisis VARCHAR(100) NULL,
    lote_intelisis VARCHAR(50) NULL,
    fecha_intelisis DATE NULL,
    piezas_intelisis DECIMAL(19, 4) NULL,
    cajas_intelisis DECIMAL(19, 4) NULL,
    -- Cajas que Almacén contó físicamente al recibir (la diferencia contra Intelisis queda registrada).
    cajas_confirmadas INT NULL,
    observaciones VARCHAR(300) NULL,
    contrato VARCHAR(60) NULL,
    fecha_alta DATETIME2 NOT NULL CONSTRAINT DF_lote_fecha_alta DEFAULT SYSDATETIME();
GO

-- Un registro de Intelisis se recibe una sola vez, y un lote de la existencia se da de
-- alta una sola vez.
CREATE UNIQUE INDEX UX_lote_registro_intelisis ON lote (origen_intelisis, id_intelisis, medicamento_clave, lote_intelisis)
    WHERE origen_intelisis IN ('COMPRA', 'INV');
CREATE UNIQUE INDEX UX_lote_alta_inicial ON lote (medicamento_clave, lote_intelisis)
    WHERE origen_intelisis = 'EXISTENCIA';
