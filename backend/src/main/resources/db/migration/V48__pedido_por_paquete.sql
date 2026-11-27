-- Farmacia puede pedir a Almacén por paquete; Almacén anota cuántas cajas trae cada paquete (quién y cuándo).
ALTER TABLE solicitud ADD paquetes INT NULL;
ALTER TABLE solicitud ADD cajas_por_paquete INT NULL;
ALTER TABLE solicitud ADD cajas_por_paquete_por BIGINT NULL CONSTRAINT FK_solicitud_paquete_usuario REFERENCES usuario(id);
ALTER TABLE solicitud ADD cajas_por_paquete_fecha DATETIME2 NULL;
