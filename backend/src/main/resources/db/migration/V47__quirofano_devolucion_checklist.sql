-- Checklist al regresar la caja de quirófano: por medicamento, cuánto regresó y cuánto faltó (y por qué).
-- Quién y cuándo es el que recibió el vale (quirofano_vale.recibido_por / recibido_en).
ALTER TABLE quirofano_vale_partida ADD devuelto INT NULL;
ALTER TABLE quirofano_vale_partida ADD faltante INT NULL;
ALTER TABLE quirofano_vale_partida ADD motivo_faltante NVARCHAR(300) NULL;
