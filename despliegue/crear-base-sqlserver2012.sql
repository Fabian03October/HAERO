/* =====================================================================
   HAERO: crear la base "farmacia" y su usuario en SQL Server 2012.
   Correr con un usuario administrador de SQL Server (sa o equivalente).

   ANTES DE CORRER: cambiar <CONTRASEÑA> (aparece una sola vez, abajo).
   La contraseña debe cumplir la política del servidor (al menos un
   carácter especial). Usar solo letras, números y _ - . @
   (evitar % ! ^ & ; y comillas: rompen el iniciar.bat o la conexión).

   Después de crearla NO hay que crear tablas: HAERO las crea solo al
   arrancar la primera vez.
   ===================================================================== */

CREATE DATABASE farmacia COLLATE SQL_Latin1_General_CP1_CI_AS;
GO

CREATE LOGIN farmacia_app
    WITH PASSWORD = N'<CONTRASEÑA>',
         DEFAULT_DATABASE = farmacia,
         CHECK_POLICY = ON,
         CHECK_EXPIRATION = OFF;   -- que no caduque: si caduca, HAERO deja de conectarse
GO

USE farmacia;
GO

CREATE USER farmacia_app FOR LOGIN farmacia_app;
GO

EXEC sp_addrolemember 'db_owner', 'farmacia_app';
GO

-- Comprobación: debe mostrar la base, el usuario y "db_owner".
SELECT DB_NAME() AS base, dp.name AS usuario, r.name AS rol
FROM sys.database_role_members m
JOIN sys.database_principals r  ON r.principal_id  = m.role_principal_id
JOIN sys.database_principals dp ON dp.principal_id = m.member_principal_id
WHERE dp.name = 'farmacia_app';
GO
