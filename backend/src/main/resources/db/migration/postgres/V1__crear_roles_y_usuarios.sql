CREATE TABLE rol (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE,
    descripcion VARCHAR(150)
);

CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    nombre_completo VARCHAR(120) NOT NULL,
    nombre_usuario VARCHAR(50) NOT NULL UNIQUE,
    correo VARCHAR(120) NOT NULL UNIQUE,
    contrasena_hash VARCHAR(100) NOT NULL,
    rol_id BIGINT NOT NULL REFERENCES rol(id),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    ultimo_acceso TIMESTAMP
);

INSERT INTO rol (nombre, descripcion) VALUES
    ('ALMACEN', 'Registra entradas, salidas, lotes y caducidades de medicamentos'),
    ('FARMACIA', 'Registra la dispensación de medicamentos al paciente'),
    ('SUPERVISION', 'Consulta información sin registrar movimientos'),
    ('ADMIN', 'Administra usuarios y roles del sistema');