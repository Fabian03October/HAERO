-- HU15/HU19 (ajuste): alerta proactiva de lotes por vencer, ademas de las ya
-- existentes de stock (HU17). Antes solo se avisaba del bloqueo de lotes YA
-- vencidos al despachar, o del filtro de canje en pantalla; no habia una
-- alerta visible en el panel.

ALTER TABLE alerta ADD lote_id BIGINT NULL REFERENCES lote(id);

DECLARE @nombre_check NVARCHAR(200);
SELECT @nombre_check = cc.name
FROM sys.check_constraints cc
JOIN sys.columns col ON cc.parent_object_id = col.object_id AND cc.parent_column_id = col.column_id
WHERE cc.parent_object_id = OBJECT_ID('alerta') AND col.name = 'tipo';

IF @nombre_check IS NOT NULL
    EXEC('ALTER TABLE alerta DROP CONSTRAINT ' + @nombre_check);

-- 'CADUCIDAD_PROXIMA' (18 caracteres) ya no cabe en VARCHAR(12).
ALTER TABLE alerta ALTER COLUMN tipo VARCHAR(20) NOT NULL;

ALTER TABLE alerta ADD CONSTRAINT CK_alerta_tipo
    CHECK (tipo IN ('DESABASTO', 'SOBREABASTO', 'CADUCIDAD_PROXIMA'));
