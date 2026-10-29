-- Iteración 1, revisión antes de subir al servidor.

-- HU41: bitácora de administración de usuarios. Guarda quién dio de alta, cambió,
-- desactivó, reactivó o restableció la contraseña de cada cuenta. Nunca guarda contraseñas.
CREATE TABLE bitacora_usuario (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_bitacora_usuario PRIMARY KEY,
    fecha DATETIME2 NOT NULL,
    realizado_por VARCHAR(50) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    accion VARCHAR(30) NOT NULL,
    detalle NVARCHAR(400) NULL
);
CREATE INDEX IX_bitacora_usuario_fecha ON bitacora_usuario (fecha);
CREATE INDEX IX_bitacora_usuario_usuario ON bitacora_usuario (usuario_id, fecha);

-- Los usuarios de prueba de la V3 se crearon con contraseñas cuyo hash está en el
-- repositorio. Si alguno sigue con esa contraseña, se desactiva: en el servidor del
-- hospital nadie debe poder entrar con ella. El administrador puede reactivarlo y
-- restablecerle la contraseña (queda en la bitácora). No afecta a quien ya la cambió.
UPDATE usuario
SET activo = 0
WHERE (nombre_usuario = 'almacen' AND contrasena_hash = '$2a$10$tPuvqg4x.KeCgMlRFo/8bOKbPfUwOmf0l.DO6xm5kMrZ6QsWNWafi')
   OR (nombre_usuario = 'farmacia' AND contrasena_hash = '$2a$10$Fu8pA3GSwzF3XLY6ga6JPuNRf/VJiOiXdA6nYE2Og2C55DzXSlrAe')
   OR (nombre_usuario = 'supervision' AND contrasena_hash = '$2a$10$XvturiTceKDKbwZ/PGjMNu7DOCYK9ZIPPtmOiJSU/jG9rSeMXESAe');
