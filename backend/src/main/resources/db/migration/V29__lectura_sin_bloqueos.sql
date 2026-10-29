-- B8: las pantallas leen la copia de Intelisis mientras la sincronización la reemplaza (unos
-- segundos cada 15 minutos). Con READ_COMMITTED_SNAPSHOT las lecturas ven la versión anterior
-- en lugar de esperar. Si el usuario de la base no tiene permiso, no se hace nada y TICs lo
-- activa a mano (DESPLIEGUE.md, paso 2). Corre fuera de transacción: ver el .conf.
IF HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'ALTER') = 1
   AND (SELECT is_read_committed_snapshot_on FROM sys.databases WHERE name = DB_NAME()) = 0
    EXEC('ALTER DATABASE CURRENT SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE');
