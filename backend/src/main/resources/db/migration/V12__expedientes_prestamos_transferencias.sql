-- Préstamos y transferencias con expediente (ajuste HU20, en pruebas).
-- Antes cada préstamo o transferencia era un movimiento suelto. Ahora el
-- expediente agrupa todo: la salida o entrada inicial, las devoluciones (solo
-- préstamos), los documentos PDF escaneados y el estatus.
--
-- Estatus guardados:
--   PRESTAMO:      ACTIVO -> DEVUELTO_PARCIAL -> CERRADO
--   TRANSFERENCIA: REGISTRADA -> CONFIRMADA
-- "Vencido" (préstamo sin cerrar con la fecha límite ya pasada) y "Por cerrar"
-- (todo devuelto, falta el documento de cierre) se calculan al consultar.

CREATE TABLE expediente_externo (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    -- SALIDA: el hospital presta o transfiere. ENTRADA: el hospital recibe.
    sentido VARCHAR(10) NOT NULL,
    institucion VARCHAR(120) NOT NULL,
    medicamento_clave VARCHAR(30) NOT NULL CONSTRAINT FK_expediente_medicamento REFERENCES medicamento(clave),
    cajas INT NOT NULL,
    cajas_devueltas INT NOT NULL CONSTRAINT DF_expediente_cajas_devueltas DEFAULT 0,
    fecha_registro DATETIME2 NOT NULL,
    -- Solo préstamos: fecha comprometida de devolución.
    fecha_limite DATE NULL,
    fecha_cierre DATETIME2 NULL,
    estatus VARCHAR(20) NOT NULL,
    observaciones VARCHAR(500) NULL,
    usuario_id BIGINT NOT NULL CONSTRAINT FK_expediente_usuario REFERENCES usuario(id)
);

-- PDF escaneados del expediente. Tipos: SOLICITUD y CIERRE (préstamo),
-- ENVIO y RECEPCION (transferencia).
CREATE TABLE documento_externo (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    expediente_id BIGINT NOT NULL CONSTRAINT FK_documento_expediente REFERENCES expediente_externo(id),
    tipo VARCHAR(20) NOT NULL,
    nombre_archivo NVARCHAR(255) NOT NULL,
    tamano INT NOT NULL,
    contenido VARBINARY(MAX) NOT NULL,
    fecha DATETIME2 NOT NULL,
    usuario_id BIGINT NOT NULL CONSTRAINT FK_documento_usuario REFERENCES usuario(id)
);

ALTER TABLE movimiento ADD expediente_id BIGINT NULL;
ALTER TABLE movimiento ADD CONSTRAINT FK_movimiento_expediente FOREIGN KEY (expediente_id) REFERENCES expediente_externo(id);
