-- Cajas de anestesia: cada medicamento del listado puede llevar su propio nombre (como lo escribe
-- Farmacia en la caja y en el vale, ej. "Fentanilo sol. inyectable 0.5 mg/10 ml"), porque el nombre del
-- catálogo de Intelisis viene en formatos distintos ("FENTANILO, CITRATO DE"). Vacío = el del catálogo.
ALTER TABLE quirofano_caja_contenido ADD descripcion NVARCHAR(200) NULL;
