-- Movimientos especiales (HU18, HU19, HU20), según los ajustes al diseño de la Iteración 2:
-- - lote.lote_origen_id: enlace lote de origen -> lote nuevo de un canje (HU18 CA3).
-- - movimiento.sentido: ENTRADA o SALIDA para canjes, préstamos y transferencias (HU20 CA1).
-- - movimiento.institucion_externa: institución del préstamo o transferencia (HU20 CA4).
-- Los estatus de lote CANJEADO y CADUCADO y los tipos de movimiento CANJE,
-- BAJA_CADUCIDAD, PRESTAMO y TRANSFERENCIA ya caben en las columnas VARCHAR(20).

ALTER TABLE lote ADD lote_origen_id BIGINT NULL;
ALTER TABLE lote ADD CONSTRAINT FK_lote_lote_origen FOREIGN KEY (lote_origen_id) REFERENCES lote(id);

ALTER TABLE movimiento ADD sentido VARCHAR(10) NULL;
ALTER TABLE movimiento ADD institucion_externa VARCHAR(120) NULL;
