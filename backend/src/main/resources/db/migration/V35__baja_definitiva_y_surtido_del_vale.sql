-- Ajustes de la presentación con Supervisión.
--
-- 1) Baja definitiva de caducados (Almacén): los caducados no se borran, son bienes del hospital.
--    Salen del inventario solo cuando la empresa se los lleva y se levanta el acta ante las
--    autoridades. Aquí queda el acta (folio, empresa, fecha y PDF) y los lotes que incluye; el lote
--    pasa de CADUCADO a BAJA_DEFINITIVA.
CREATE TABLE almacen_baja_definitiva (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    folio_acta VARCHAR(60) NOT NULL,
    empresa VARCHAR(150) NOT NULL,
    fecha_recoleccion DATE NOT NULL,
    observaciones VARCHAR(500) NULL,
    documento_nombre VARCHAR(255) NOT NULL,
    documento VARBINARY(MAX) NOT NULL,
    fecha DATETIME2 NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id)
);

CREATE TABLE almacen_baja_definitiva_lote (
    baja_id BIGINT NOT NULL REFERENCES almacen_baja_definitiva(id),
    lote_id BIGINT NOT NULL REFERENCES lote(id),
    cajas INT NOT NULL,
    CONSTRAINT PK_almacen_baja_definitiva_lote PRIMARY KEY (baja_id, lote_id),
    CONSTRAINT UX_almacen_baja_definitiva_lote UNIQUE (lote_id)
);

-- 2) Vale de quirófano: lo que salió en la caja al entregarla (columna "Surtido" del vale), para
--    imprimir el vale con Surtido / Utilizado / Devolución aunque la caja ya se haya repuesto.
CREATE TABLE quirofano_vale_partida (
    vale_id BIGINT NOT NULL REFERENCES quirofano_vale(id),
    medicamento_clave VARCHAR(30) NOT NULL REFERENCES medicamento(clave),
    surtido INT NOT NULL,
    orden INT NOT NULL,
    CONSTRAINT PK_quirofano_vale_partida PRIMARY KEY (vale_id, medicamento_clave)
);
