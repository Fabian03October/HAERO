-- Quimio: un medicamento puede surtirse incompleto (menos piezas de las indicadas) con su motivo; lo que falta queda pendiente.
ALTER TABLE quimio_renglon ADD motivo_parcial NVARCHAR(200) NULL;
