INSERT INTO usuario (nombre_completo, nombre_usuario, correo, contrasena_hash, rol_id, activo, debe_cambiar_contrasena)
VALUES (
    'Administrador de Prueba',
    'admin',
    'admin@hraeo.test',
    '$2a$10$72E8Rtxz1gWvE.TVLJUyde8qQla9i7E/k8.mlbVJgcizih03pEHnK',
    (SELECT id FROM rol WHERE nombre = 'ADMIN'),
    TRUE,
    FALSE
);
