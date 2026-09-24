INSERT INTO usuario (nombre_completo, nombre_usuario, correo, contrasena_hash, rol_id, activo, debe_cambiar_contrasena)
VALUES
    ('Prueba Almacen', 'almacen', 'almacen@hraeo.test', '$2a$10$tPuvqg4x.KeCgMlRFo/8bOKbPfUwOmf0l.DO6xm5kMrZ6QsWNWafi', (SELECT id FROM rol WHERE nombre = 'ALMACEN'), TRUE, FALSE),
    ('Prueba Farmacia', 'farmacia', 'farmacia@hraeo.test', '$2a$10$Fu8pA3GSwzF3XLY6ga6JPuNRf/VJiOiXdA6nYE2Og2C55DzXSlrAe', (SELECT id FROM rol WHERE nombre = 'FARMACIA'), TRUE, FALSE),
    ('Prueba Supervision', 'supervision', 'supervision@hraeo.test', '$2a$10$XvturiTceKDKbwZ/PGjMNu7DOCYK9ZIPPtmOiJSU/jG9rSeMXESAe', (SELECT id FROM rol WHERE nombre = 'SUPERVISION'), TRUE, FALSE);
