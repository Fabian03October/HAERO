CREATE TABLE rol (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE,
    descripcion VARCHAR(150)
);

CREATE TABLE usuario (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    nombre_completo VARCHAR(120) NOT NULL,
    nombre_usuario VARCHAR(50) NOT NULL UNIQUE,
    correo VARCHAR(120) NOT NULL UNIQUE,
    contrasena_hash VARCHAR(100) NOT NULL,
    rol_id BIGINT NOT NULL REFERENCES rol(id),
    activo BIT NOT NULL DEFAULT 1,
    debe_cambiar_contrasena BIT NOT NULL DEFAULT 1,
    fecha_creacion DATETIME2 NOT NULL DEFAULT GETDATE(),
    ultimo_acceso DATETIME2 NULL
);

INSERT INTO rol (nombre, descripcion) VALUES
    ('ALMACEN', 'Registra entradas, salidas, lotes y caducidades de medicamentos'),
    ('FARMACIA', 'Registra la dispensacion de medicamentos al paciente'),
    ('SUPERVISION', 'Consulta informacion sin registrar movimientos'),
    ('ADMIN', 'Administra usuarios y roles del sistema');
