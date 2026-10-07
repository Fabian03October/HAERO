-- 1) Control de concurrencia: si dos despachadores guardan la misma existencia o la
--    misma solicitud al mismo tiempo, el segundo recibe un aviso en lugar de pisar
--    el dato del primero (bloqueo optimista con @Version).
ALTER TABLE existencia ADD version BIGINT NOT NULL CONSTRAINT DF_existencia_version DEFAULT 0;
ALTER TABLE solicitud ADD version BIGINT NOT NULL CONSTRAINT DF_solicitud_version DEFAULT 0;

-- 2) Aceptación de las políticas de uso y privacidad guardada por usuario en el
--    servidor (antes solo en el navegador). Si la versión vigente cambia, se pide de nuevo.
ALTER TABLE usuario ADD version_politicas_aceptada VARCHAR(20) NULL;
ALTER TABLE usuario ADD fecha_aceptacion_politicas DATETIME2 NULL;

-- 3) Bloqueo temporal tras varios intentos fallidos de inicio de sesión.
ALTER TABLE usuario ADD intentos_fallidos INT NOT NULL CONSTRAINT DF_usuario_intentos_fallidos DEFAULT 0;
ALTER TABLE usuario ADD bloqueado_hasta DATETIME2 NULL;
