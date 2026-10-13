/* =====================================================================
   Intelisis -> HAERO: muestras del Almacén de Medicamentos (25301)
   Segunda tanda, después de intelisis-muestras-trazabilidad.sql.

   SOLO LECTURA. No consulta tablas de pacientes (nada de vsa_*, Cte,
   FarmaciaTablero ni Venta). Todas las consultas traen pocas filas.

   Cada bloque imprime un título (PRINT) para reconocerlo en el archivo
   de resultados. Si una columna no existe, esa consulta marca error y
   las demás siguen (cada una va separada con GO): anota cuál falló.

   Guarda los resultados FUERA de la carpeta del proyecto.
   ===================================================================== */

SET NOCOUNT ON;
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;  -- no bloquear al ERP


/* ---------------------------------------------------------------------
   A0. Columnas de las tablas que vamos a usar (solo nombres y tipos).
       Sirve aunque alguna consulta de abajo falle.
   --------------------------------------------------------------------- */
GO
PRINT '===== A0. Columnas de Art, ArtMedicamento, ArtUnidad, Prov, CB, SerieLote, SerieLoteMov, Compra, CompraD =====';
SELECT TABLE_NAME AS tabla, ORDINAL_POSITION AS orden, COLUMN_NAME AS columna,
       DATA_TYPE AS tipo, CHARACTER_MAXIMUM_LENGTH AS longitud
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME IN ('Art', 'ArtMedicamento', 'ArtUnidad', 'Prov', 'CB',
                     'SerieLote', 'SerieLoteMov', 'Compra', 'CompraD')
ORDER BY TABLE_NAME, ORDINAL_POSITION;


/* ---------------------------------------------------------------------
   A1. Existencias por lote con caducidad en el almacén 25301
   --------------------------------------------------------------------- */
GO
PRINT '===== A1a. Resumen del almacén 25301: lotes, existencia y cuántos traen caducidad =====';
SELECT COUNT(*)                                                    AS lotes_con_existencia,
       COUNT(DISTINCT s.Articulo)                                  AS articulos_distintos,
       SUM(s.Existencia)                                           AS existencia_total,
       SUM(CASE WHEN s.FechaCaducidad IS NOT NULL THEN 1 ELSE 0 END) AS lotes_con_caducidad,
       SUM(CASE WHEN s.FechaCaducidad IS NULL THEN 1 ELSE 0 END)     AS lotes_sin_caducidad,
       MIN(s.FechaCaducidad)                                       AS caducidad_mas_proxima,
       MAX(s.FechaCaducidad)                                       AS caducidad_mas_lejana
FROM SerieLote s
WHERE s.Almacen = '25301' AND s.Existencia > 0;

GO
PRINT '===== A1b. Lotes del 25301 que caducan primero (orden FEFO) =====';
SELECT TOP 30 s.Almacen, s.Articulo, a.Descripcion1, s.SerieLote, s.Existencia,
       s.FechaCaducidad, s.UltimaEntrada, s.UltimaSalida
FROM SerieLote s
JOIN Art a ON a.Articulo = s.Articulo
WHERE s.Almacen = '25301' AND s.Existencia > 0
ORDER BY CASE WHEN s.FechaCaducidad IS NULL THEN 1 ELSE 0 END, s.FechaCaducidad;

GO
PRINT '===== A1c. Un mismo artículo en varios lotes (para ver cómo reparte la existencia) =====';
SELECT TOP 30 s.Articulo, COUNT(*) AS lotes, SUM(s.Existencia) AS existencia_total,
       MIN(s.FechaCaducidad) AS primera_caducidad, MAX(s.FechaCaducidad) AS ultima_caducidad
FROM SerieLote s
WHERE s.Almacen = '25301' AND s.Existencia > 0
GROUP BY s.Articulo
HAVING COUNT(*) > 1
ORDER BY lotes DESC;

GO
PRINT '===== A1d. Formatos de clave en el 25301 (con G-, con sufijo -1, normales) =====';
SELECT CASE WHEN s.Articulo LIKE 'G-%'                     THEN 'empieza con G-'
            WHEN s.Articulo LIKE '%-[0-9]'                 THEN 'termina en -N'
            WHEN s.Articulo LIKE '[0-9][0-9][0-9].[0-9][0-9][0-9].[0-9][0-9][0-9][0-9].[0-9][0-9]' THEN 'clave normal 000.000.0000.00'
            ELSE 'otro formato' END AS formato,
       COUNT(DISTINCT s.Articulo) AS articulos,
       MIN(s.Articulo) AS ejemplo
FROM SerieLote s
WHERE s.Almacen = '25301' AND s.Existencia > 0
GROUP BY CASE WHEN s.Articulo LIKE 'G-%'                     THEN 'empieza con G-'
              WHEN s.Articulo LIKE '%-[0-9]'                 THEN 'termina en -N'
              WHEN s.Articulo LIKE '[0-9][0-9][0-9].[0-9][0-9][0-9].[0-9][0-9][0-9][0-9].[0-9][0-9]' THEN 'clave normal 000.000.0000.00'
              ELSE 'otro formato' END;


/* ---------------------------------------------------------------------
   A2. Artículos de medicamento: unidad, presentación y factor a cajas
   --------------------------------------------------------------------- */
GO
PRINT '===== A2a. Columnas de Art que hablan de unidad, factor, empaque o presentación =====';
SELECT TABLE_NAME AS tabla, COLUMN_NAME AS columna, DATA_TYPE AS tipo
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME IN ('Art', 'ArtMedicamento', 'ArtUnidad')
  AND (COLUMN_NAME LIKE '%Unidad%' OR COLUMN_NAME LIKE '%Factor%' OR COLUMN_NAME LIKE '%Empaque%'
       OR COLUMN_NAME LIKE '%Presenta%' OR COLUMN_NAME LIKE '%Contenido%' OR COLUMN_NAME LIKE '%Caja%'
       OR COLUMN_NAME LIKE '%Piezas%' OR COLUMN_NAME LIKE '%Descripcion%')
ORDER BY TABLE_NAME, COLUMN_NAME;

GO
PRINT '===== A2b. Muestra de Art (medicamentos del 25301) =====';
SELECT TOP 10 a.*
FROM Art a
WHERE a.Articulo IN (SELECT DISTINCT TOP 10 s.Articulo FROM SerieLote s
                     WHERE s.Almacen = '25301' AND s.Existencia > 0);

GO
PRINT '===== A2c. Muestra de ArtMedicamento =====';
SELECT TOP 10 m.*
FROM ArtMedicamento m
WHERE m.Articulo IN (SELECT DISTINCT TOP 10 s.Articulo FROM SerieLote s
                     WHERE s.Almacen = '25301' AND s.Existencia > 0);

GO
PRINT '===== A2d. ArtUnidad (factores de conversión por artículo), si existe =====';
SELECT TOP 20 u.*
FROM ArtUnidad u
WHERE u.Articulo IN (SELECT DISTINCT TOP 10 s.Articulo FROM SerieLote s
                     WHERE s.Almacen = '25301' AND s.Existencia > 0);


/* ---------------------------------------------------------------------
   A3. Proveedores y códigos de barras
   --------------------------------------------------------------------- */
GO
PRINT '===== A3a. Muestra de Prov =====';
SELECT TOP 10 p.* FROM Prov p;

GO
PRINT '===== A3b. Cuántos códigos de barras hay y de qué tipo de cuenta =====';
SELECT TOP 20 TipoCuenta, COUNT(*) AS codigos
FROM CB GROUP BY TipoCuenta ORDER BY codigos DESC;

GO
PRINT '===== A3c. Códigos de barras de medicamentos del 25301 =====';
SELECT TOP 30 cb.*
FROM CB cb
WHERE cb.Cuenta IN (SELECT DISTINCT s.Articulo FROM SerieLote s
                    WHERE s.Almacen = '25301' AND s.Existencia > 0);

GO
PRINT '===== A3d. ¿Cuántos medicamentos del 25301 tienen al menos un código de barras? =====';
SELECT COUNT(DISTINCT s.Articulo) AS medicamentos_en_25301,
       COUNT(DISTINCT cb.Cuenta)  AS con_codigo_de_barras
FROM SerieLote s
LEFT JOIN CB cb ON cb.Cuenta = s.Articulo
WHERE s.Almacen = '25301' AND s.Existencia > 0;


/* ---------------------------------------------------------------------
   A4. Recepciones (Ent. Compra Farmacia) con sus lotes
   --------------------------------------------------------------------- */
GO
PRINT '===== A4a. Recepciones recientes al 25301: encabezado y renglones =====';
SELECT TOP 20 c.ID, c.Mov, c.MovID, c.FechaEmision, c.FechaEntrega, c.Estatus, c.Almacen,
       c.Origen, c.OrigenID, d.Articulo, a.Descripcion1, d.Cantidad, d.FechaCaducidad,
       d.ClavePresupuestal, d.CveCucop
FROM Compra c
JOIN CompraD d ON d.ID = c.ID
JOIN Art a     ON a.Articulo = d.Articulo
WHERE c.Mov = 'Ent. Compra Farmacia' AND c.Almacen = '25301'
ORDER BY c.FechaEmision DESC;

GO
PRINT '===== A4b. Encabezado completo de una recepción (para ver proveedor, factura, etc.) =====';
SELECT TOP 3 c.*
FROM Compra c
WHERE c.Mov = 'Ent. Compra Farmacia' AND c.Almacen = '25301' AND c.Estatus = 'CONCLUIDO'
ORDER BY c.FechaEmision DESC;

GO
PRINT '===== A4c. Lotes que entraron con esas recepciones (SerieLoteMov, módulo COMS) =====';
SELECT TOP 30 c.MovID AS recepcion, c.FechaEmision, slm.Articulo, slm.SerieLote,
       slm.Cantidad, slm.FechaCaducidad, slm.RenglonID
FROM Compra c
JOIN SerieLoteMov slm ON slm.Modulo = 'COMS' AND slm.ID = c.ID
WHERE c.Mov = 'Ent. Compra Farmacia' AND c.Almacen = '25301'
ORDER BY c.FechaEmision DESC;

GO
PRINT '===== A4d. De dónde viene cada recepción (Origen) =====';
SELECT TOP 20 c.Origen, c.Estatus, COUNT(*) AS recepciones
FROM Compra c
WHERE c.Mov = 'Ent. Compra Farmacia'
GROUP BY c.Origen, c.Estatus
ORDER BY recepciones DESC;

GO
PRINT '===== A4e. Recepciones por mes del último año (para ver el volumen) =====';
SELECT CONVERT(char(7), c.FechaEmision, 120) AS mes, COUNT(*) AS recepciones
FROM Compra c
WHERE c.Mov = 'Ent. Compra Farmacia' AND c.Estatus = 'CONCLUIDO'
  AND c.FechaEmision >= DATEADD(year, -1, GETDATE())
GROUP BY CONVERT(char(7), c.FechaEmision, 120)
ORDER BY mes;

GO
PRINT '===== Fin =====';
