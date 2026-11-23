/* =====================================================================
   Genera un script de INSERTs con lo que HAERO trajo de Intelisis
   (catálogo, pedidos, existencias, Hospitalización, Quimioterapia, avisos)
   para cargarlo en el SQL Server 2012 del hospital. NO copia datos de
   prueba: usuarios, bitácora, lotes/existencias/movimientos capturados,
   códigos de barras, conteo inicial ni nada de Farmacia/Quirófano.

   Se corre contra la base de la Mac (SQL Server 2017+: usa STRING_AGG);
   lo que imprime es el script a cargar. Ver herramientas/exportar-datos-intelisis.sh
   ===================================================================== */
SET NOCOUNT ON;

DECLARE @tablas TABLE (orden int IDENTITY, nombre sysname, filtro nvarchar(400));
INSERT INTO @tablas (nombre, filtro) VALUES
 ('medicamento', NULL), ('pedido', NULL), ('pedido_detalle', NULL),
 ('alerta', 'lote_id IS NULL'),          -- las que apuntan a lotes de prueba se quedan
 ('aviso_intelisis', 'lote_id IS NULL'),
 ('intelisis_articulo', NULL), ('intelisis_consumo_mes', NULL), ('intelisis_entrada_inv', NULL),
 ('intelisis_entrada_inv_lote', NULL), ('intelisis_existencia_lote', NULL), ('intelisis_factor', NULL),
 ('intelisis_lote_visto', NULL), ('intelisis_medicamento', NULL), ('intelisis_mov_farmacia_lote', NULL),
 ('intelisis_pedido', NULL), ('intelisis_pedido_partida', NULL), ('intelisis_proveedor', NULL),
 ('intelisis_recepcion', NULL), ('intelisis_recepcion_lote', NULL), ('intelisis_sincronizacion', NULL),
 ('hosp_cama', NULL), ('hosp_estancia', NULL), ('hosp_evento', NULL), ('hosp_medicamento', NULL),
 ('hosp_medico', NULL), ('hosp_sincronizacion', NULL),
 ('quimio_solicitud', NULL), ('quimio_sincronizacion', NULL);

DECLARE @nl nchar(1) = NCHAR(10), @lote int = 500;

PRINT N'-- Datos de Intelisis para HAERO. Generado ' + CONVERT(nvarchar(30), SYSDATETIME(), 120) + N'.';
PRINT N'-- Cargar con: sqlcmd -S localhost\HRAEODB190 -U sqlFarmacia -P "..." -d farmacia -b -f 65001 -i datos-intelisis.sql';
PRINT N'SET NOCOUNT ON;';
PRINT N'IF OBJECT_ID(''dbo.intelisis_articulo'') IS NULL BEGIN RAISERROR(''Primero arranca HAERO una vez para que cree las tablas.'', 16, 1); RETURN; END';
PRINT N'GO';
PRINT N'EXEC sp_MSforeachtable ''ALTER TABLE ? NOCHECK CONSTRAINT ALL'';';
PRINT N'GO';

-- Vaciar primero (por si HAERO ya sincronizó algo en el servidor)
DECLARE @t sysname, @f nvarchar(400), @cols nvarchar(max), @vals nvarchar(max), @sql nvarchar(max);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT nombre FROM @tablas ORDER BY orden DESC;
OPEN c; FETCH c INTO @t;
WHILE @@FETCH_STATUS = 0 BEGIN
    PRINT N'DELETE FROM dbo.' + QUOTENAME(@t) + N';';
    FETCH c INTO @t;
END
CLOSE c; DEALLOCATE c;
PRINT N'GO';

DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT nombre, filtro FROM @tablas ORDER BY orden;
OPEN c; FETCH c INTO @t, @f;
WHILE @@FETCH_STATUS = 0 BEGIN
    SELECT @cols = STRING_AGG(CONVERT(nvarchar(max), QUOTENAME(name)), N',') WITHIN GROUP (ORDER BY column_id),
           @vals = STRING_AGG(CONVERT(nvarchar(max),
               N'ISNULL(' + CASE
                   WHEN TYPE_NAME(system_type_id) IN ('varbinary','binary')
                       THEN N'CONVERT(nvarchar(max),' + QUOTENAME(name) + N',1)'
                   WHEN TYPE_NAME(system_type_id) IN ('float','real')
                       THEN N'CONVERT(nvarchar(max),' + QUOTENAME(name) + N',3)'
                   WHEN TYPE_NAME(system_type_id) IN ('date','datetime','datetime2','smalldatetime','time')
                       THEN N'N''''''''+CONVERT(nvarchar(50),' + QUOTENAME(name) + N',126)+N'''''''''
                   WHEN TYPE_NAME(system_type_id) IN ('bit','tinyint','smallint','int','bigint','decimal','numeric','money')
                       THEN N'CONVERT(nvarchar(max),' + QUOTENAME(name) + N')'
                   ELSE N'N''N''''''+REPLACE(CONVERT(nvarchar(max),' + QUOTENAME(name) + N'),N'''''''',N'''''''''''')+N'''''''''
               END + N',N''NULL'')'), N'+N'',''+') WITHIN GROUP (ORDER BY column_id)
    FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.' + @t) AND is_computed = 0;

    IF OBJECTPROPERTY(OBJECT_ID(N'dbo.' + @t), 'TableHasIdentity') = 1
        PRINT N'SET IDENTITY_INSERT dbo.' + QUOTENAME(@t) + N' ON;';

    -- Un INSERT de varias filas cada @lote renglones, con GO entre lotes
    SET @sql = N'SELECT CASE WHEN (rn-1)%' + CAST(@lote AS nvarchar) + N'=0 THEN N''INSERT INTO dbo.' + QUOTENAME(@t)
        + N' (' + REPLACE(@cols, N'''', N'''''') + N') VALUES'' + @nl ELSE N'','' END + N''('' + v + N'')''
        + CASE WHEN rn%' + CAST(@lote AS nvarchar) + N'=0 OR rn=total THEN N'';'' + @nl + N''GO'' ELSE N'''' END AS XHDR_HAERO
        FROM (SELECT ' + @vals + N' AS v, ROW_NUMBER() OVER (ORDER BY (SELECT 1)) rn, COUNT(*) OVER () total
              FROM dbo.' + QUOTENAME(@t) + ISNULL(N' WHERE ' + @f, N'') + N') x ORDER BY rn;';
    EXEC sp_executesql @sql, N'@nl nchar(1)', @nl;

    IF OBJECTPROPERTY(OBJECT_ID(N'dbo.' + @t), 'TableHasIdentity') = 1
        PRINT N'SET IDENTITY_INSERT dbo.' + QUOTENAME(@t) + N' OFF;' + @nl + N'GO';
    FETCH c INTO @t, @f;
END
CLOSE c; DEALLOCATE c;

-- Reactiva las llaves foráneas y comprueba que todo cuadre (falla si algo apunta a un dato que no está)
PRINT N'EXEC sp_MSforeachtable ''ALTER TABLE ? WITH CHECK CHECK CONSTRAINT ALL'';';
PRINT N'GO';
SELECT N'SELECT ''' + nombre + N''' AS tabla, COUNT(*) AS filas FROM dbo.' + QUOTENAME(nombre)
       + CASE WHEN orden < (SELECT MAX(orden) FROM @tablas) THEN N' UNION ALL' ELSE N';' END AS XHDR_HAERO
FROM @tablas ORDER BY orden;
PRINT N'GO';
