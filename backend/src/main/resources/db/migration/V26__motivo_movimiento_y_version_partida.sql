-- Iteración 2, revisión antes de subir al servidor.

-- Motivo de un movimiento cuando la regla lo pide: despacho fuera de FEFO (HU15 CA2),
-- y más adelante ajustes de inventario (HU36) y reubicaciones (HU37).
ALTER TABLE movimiento ADD motivo NVARCHAR(300) NULL;

-- Dos personas recibiendo la misma partida al mismo tiempo ya no pierden una de las sumas.
ALTER TABLE pedido_detalle ADD version BIGINT NOT NULL CONSTRAINT DF_pedido_detalle_version DEFAULT 0;
