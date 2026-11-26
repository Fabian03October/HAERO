-- Ventanilla: si al paciente ya se le entregó la misma clave en los últimos días (otra receta, Quimio u
-- Hospitalización), se pide una justificación para volver a entregarla y queda guardada con la entrega.
ALTER TABLE farmacia_entrega ADD justificacion_repetida NVARCHAR(300) NULL;
