-- Las claves del cuadro básico tienen la forma 010.000.6059.01-1 (17 caracteres)
-- y no caben en VARCHAR(10). Se amplía la clave a VARCHAR(30) en el catálogo y en
-- todas las tablas que la referencian.
--
-- SQL Server no permite cambiar el tamaño de una columna que es llave primaria o
-- que participa en una llave foránea, así que primero se quitan esas restricciones
-- (V5/V6 no les pusieron nombre, por eso se buscan en el catálogo del sistema),
-- se amplían las columnas y se vuelven a crear con nombre.

DECLARE @sql NVARCHAR(MAX) = N'';

SELECT @sql = @sql + N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(fk.parent_object_id)) + N'.'
    + QUOTENAME(OBJECT_NAME(fk.parent_object_id)) + N' DROP CONSTRAINT ' + QUOTENAME(fk.name) + N';'
FROM sys.foreign_keys fk
WHERE fk.referenced_object_id = OBJECT_ID(N'medicamento');

SELECT @sql = @sql + N'ALTER TABLE medicamento DROP CONSTRAINT ' + QUOTENAME(kc.name) + N';'
FROM sys.key_constraints kc
WHERE kc.parent_object_id = OBJECT_ID(N'medicamento') AND kc.type = 'PK';

EXEC sp_executesql @sql;

ALTER TABLE medicamento ALTER COLUMN clave VARCHAR(30) NOT NULL;
ALTER TABLE codigo_barras ALTER COLUMN medicamento_clave VARCHAR(30) NOT NULL;
ALTER TABLE pedido_detalle ALTER COLUMN medicamento_clave VARCHAR(30) NOT NULL;
ALTER TABLE lote ALTER COLUMN medicamento_clave VARCHAR(30) NOT NULL;
ALTER TABLE solicitud ALTER COLUMN medicamento_clave VARCHAR(30) NOT NULL;

ALTER TABLE medicamento ADD CONSTRAINT PK_medicamento PRIMARY KEY (clave);
ALTER TABLE codigo_barras ADD CONSTRAINT FK_codigo_barras_medicamento FOREIGN KEY (medicamento_clave) REFERENCES medicamento(clave);
ALTER TABLE pedido_detalle ADD CONSTRAINT FK_pedido_detalle_medicamento FOREIGN KEY (medicamento_clave) REFERENCES medicamento(clave);
ALTER TABLE lote ADD CONSTRAINT FK_lote_medicamento FOREIGN KEY (medicamento_clave) REFERENCES medicamento(clave);
ALTER TABLE solicitud ADD CONSTRAINT FK_solicitud_medicamento FOREIGN KEY (medicamento_clave) REFERENCES medicamento(clave);
