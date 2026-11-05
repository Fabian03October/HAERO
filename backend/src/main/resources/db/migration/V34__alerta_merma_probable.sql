-- Ajuste de la presentación con Supervisión: además de la caducidad próxima (menos de 9 meses),
-- avisar cuando un lote va a caducar ANTES de que se alcance a consumir al ritmo del CPM, aunque
-- "en apariencia" haya para varios meses (ej. 10 cajas, CPM 3, pero el lote caduca en un mes).
-- limite_al_inicio guarda las cajas que sobrarían al caducar.

ALTER TABLE alerta DROP CONSTRAINT CK_alerta_tipo;

ALTER TABLE alerta ADD CONSTRAINT CK_alerta_tipo
    CHECK (tipo IN ('DESABASTO', 'SOBREABASTO', 'CADUCIDAD_PROXIMA', 'MERMA_PROBABLE'));
