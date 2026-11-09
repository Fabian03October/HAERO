-- Quimioterapia: un medicamento de la indicación puede quedar sin surtir (no hay existencia, o el
-- hospital no lo maneja y el paciente lo compra) y se entrega lo demás. Queda con su motivo.
ALTER TABLE quimio_renglon ADD no_surtido BIT NOT NULL CONSTRAINT DF_quimio_renglon_no_surtido DEFAULT 0;
ALTER TABLE quimio_renglon ADD motivo_no_surtido NVARCHAR(200) NULL;
