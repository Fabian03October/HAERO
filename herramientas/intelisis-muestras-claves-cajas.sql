/* =====================================================================
   Intelisis -> HAERO: clave de compra (010.000.xxxx.00) vs clave de
   Farmacia (010.000.xxxx.00-1). Tercera tanda.

   Objetivo: confirmar si la clave sin -1 es la caja que se compra, la
   clave -1 son las piezas que maneja Farmacia, y qué movimiento pasa
   la existencia de una a la otra (y con qué factor).

   SOLO LECTURA. No consulta tablas de pacientes y no muestra las
   columnas Cliente / IDTableroFarmacia de SerieLote ni SerieLoteMov.
   Solo mira movimientos de los módulos INV y COMS (nada de VTAS).

   Cómo correrlo en SSMS, todo a un solo archivo:
     Ctrl+Shift+F  ->  F5  ->  guardar en Desktop\Resultados-Intelisis
   ===================================================================== */

SET NOCOUNT ON;
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;  -- no bloquear al ERP


/* ---------------------------------------------------------------------
   B1. La misma clave con y sin -1, lado a lado (Art)
   --------------------------------------------------------------------- */
GO
PRINT '===== B1a. Cuántas claves -1 del 25301 tienen su clave sin -1 en Art =====';
SELECT COUNT(DISTINCT s.Articulo) AS claves_menos1_en_25301,
       COUNT(DISTINCT b.Articulo) AS con_clave_base_en_Art
FROM SerieLote s
LEFT JOIN Art b ON b.Articulo = LEFT(s.Articulo, LEN(s.Articulo) - 2)
WHERE s.Almacen = '25301' AND s.Existencia > 0 AND s.Articulo LIKE '%-1';

GO
PRINT '===== B1b. Clave base vs clave -1: unidad, factor y presentación (claves recibidas hace poco) =====';
SELECT TOP 40 b.Articulo AS clave_base, x.Articulo AS clave_menos1,
       b.Unidad AS unidad_base, x.Unidad AS unidad_menos1,
       b.UnidadCompra AS ucompra_base, x.UnidadCompra AS ucompra_menos1,
       b.UnidadCantidad AS ucant_base, x.UnidadCantidad AS ucant_menos1,
       b.Factor AS factor_base, x.Factor AS factor_menos1,
       b.FactorCompra AS fcompra_base, x.FactorCompra AS fcompra_menos1,
       b.FactorAlterno AS falterno_base, x.FactorAlterno AS falterno_menos1,
       b.Presentacion AS present_base, x.Presentacion AS present_menos1,
       b.TipoEmpaque AS empaque_base, b.Estatus AS estatus_base, x.Estatus AS estatus_menos1,
       b.Descripcion1 AS descripcion_base
FROM Art b
JOIN Art x ON x.Articulo = b.Articulo + '-1'
WHERE b.Articulo IN (SELECT TOP 40 d.Articulo
                     FROM Compra c JOIN CompraD d ON d.ID = c.ID
                     WHERE c.Mov = 'Ent. Compra Farmacia' AND c.Almacen = '25301'
                       AND c.Estatus = 'CONCLUIDO'
                     GROUP BY d.Articulo
                     ORDER BY MAX(c.FechaEmision) DESC);

GO
PRINT '===== B1c. Unidades de la clave base vs la clave -1 (conteo de combinaciones) =====';
SELECT TOP 30 b.Unidad AS unidad_base, x.Unidad AS unidad_menos1, COUNT(*) AS claves
FROM Art b
JOIN Art x ON x.Articulo = b.Articulo + '-1'
GROUP BY b.Unidad, x.Unidad
ORDER BY claves DESC;

GO
PRINT '===== B1d. Factores en ArtUnidad para claves base y -1 (si hay) =====';
SELECT TOP 40 u.Articulo, u.Unidad, u.Factor
FROM ArtUnidad u
WHERE u.Articulo LIKE '010.000.%' OR u.Articulo LIKE '040.000.%'
ORDER BY u.Articulo;


/* ---------------------------------------------------------------------
   B2. ¿Dónde vive la existencia de la clave base (sin -1)?
   --------------------------------------------------------------------- */
GO
PRINT '===== B2a. Existencia de claves de medicamento SIN -1, por almacén =====';
SELECT TOP 30 s.Almacen, COUNT(DISTINCT s.Articulo) AS claves, COUNT(*) AS lotes, SUM(s.Existencia) AS existencia
FROM SerieLote s
WHERE s.Existencia > 0
  AND (s.Articulo LIKE '010.000.%' OR s.Articulo LIKE '040.000.%')
  AND s.Articulo NOT LIKE '%-[0-9]'
GROUP BY s.Almacen
ORDER BY existencia DESC;

GO
PRINT '===== B2b. Existencia de claves CON -1, por almacén =====';
SELECT TOP 30 s.Almacen, COUNT(DISTINCT s.Articulo) AS claves, COUNT(*) AS lotes, SUM(s.Existencia) AS existencia
FROM SerieLote s
WHERE s.Existencia > 0 AND s.Articulo LIKE '%-1'
GROUP BY s.Almacen
ORDER BY existencia DESC;


/* ---------------------------------------------------------------------
   B3. Códigos de barras: ¿están en la clave base?
   --------------------------------------------------------------------- */
GO
PRINT '===== B3a. Códigos de barras por formato de clave =====';
SELECT CASE WHEN Cuenta LIKE '%-[0-9]' THEN 'con -N'
            WHEN Cuenta LIKE 'G-%'     THEN 'con G-'
            WHEN Cuenta LIKE '0[14]0.000.%' THEN 'clave base 010/040'
            ELSE 'otra' END AS formato,
       COUNT(*) AS codigos, COUNT(DISTINCT Cuenta) AS claves, MIN(Cuenta) AS ejemplo
FROM CB
WHERE TipoCuenta = 'Articulos'
GROUP BY CASE WHEN Cuenta LIKE '%-[0-9]' THEN 'con -N'
              WHEN Cuenta LIKE 'G-%'     THEN 'con G-'
              WHEN Cuenta LIKE '0[14]0.000.%' THEN 'clave base 010/040'
              ELSE 'otra' END;

GO
PRINT '===== B3b. Cuántas claves -1 del 25301 tienen código de barras en su clave base =====';
SELECT COUNT(DISTINCT s.Articulo) AS claves_menos1_en_25301,
       COUNT(DISTINCT cb.Cuenta)  AS base_con_codigo
FROM SerieLote s
LEFT JOIN CB cb ON cb.TipoCuenta = 'Articulos' AND cb.Cuenta = LEFT(s.Articulo, LEN(s.Articulo) - 2)
WHERE s.Almacen = '25301' AND s.Existencia > 0 AND s.Articulo LIKE '%-1';

GO
PRINT '===== B3c. Muestra de códigos de barras de claves de medicamento (Cantidad/Unidad = cuántas piezas trae ese código) =====';
SELECT TOP 30 cb.Codigo, cb.Cuenta, cb.Cantidad, cb.Unidad, cb.SerieLote, cb.Fecha, a.Unidad AS unidad_art,
       a.Descripcion1
FROM CB cb
JOIN Art a ON a.Articulo = cb.Cuenta
WHERE cb.TipoCuenta = 'Articulos' AND (cb.Cuenta LIKE '010.000.%' OR cb.Cuenta LIKE '040.000.%')
ORDER BY cb.Fecha DESC;

GO
PRINT '===== B3d. ¿Hay claves con más de un código de barras? (varios fabricantes/presentaciones) =====';
SELECT TOP 20 cb.Cuenta, COUNT(*) AS codigos
FROM CB cb
WHERE cb.TipoCuenta = 'Articulos' AND (cb.Cuenta LIKE '010.000.%' OR cb.Cuenta LIKE '040.000.%')
GROUP BY cb.Cuenta
HAVING COUNT(*) > 1
ORDER BY codigos DESC;


/* ---------------------------------------------------------------------
   B4. Seguir lotes recibidos: ¿qué movimiento los pasa a la clave -1?
       Toma los lotes de recepciones recientes (COMS) y busca esos mismos
       lotes en movimientos de inventario (INV), con cualquier clave.
   --------------------------------------------------------------------- */
GO
PRINT '===== B4a. Movimientos de inventario que tocan lotes recién recibidos (tipo, almacén, clave base o -1) =====';
WITH lotes_recibidos AS (
    SELECT DISTINCT TOP 40 slm.SerieLote, slm.Articulo AS clave_recibida
    FROM Compra c
    JOIN SerieLoteMov slm ON slm.Modulo = 'COMS' AND slm.ID = c.ID
    WHERE c.Mov = 'Ent. Compra Farmacia' AND c.Almacen = '25301' AND c.Estatus = 'CONCLUIDO'
      AND c.FechaEmision >= DATEADD(month, -3, GETDATE())
)
SELECT TOP 40 i.Mov, i.Estatus, i.Almacen, i.AlmacenDestino,
       CASE WHEN m.Articulo = l.clave_recibida          THEN 'clave base'
            WHEN m.Articulo = l.clave_recibida + '-1'   THEN 'clave -1'
            ELSE 'otra: ' + m.Articulo END AS que_clave,
       COUNT(*) AS renglones, SUM(m.Cantidad) AS cantidad
FROM lotes_recibidos l
JOIN SerieLoteMov m ON m.Modulo = 'INV' AND m.SerieLote = l.SerieLote
                   AND m.Articulo IN (l.clave_recibida, l.clave_recibida + '-1')
JOIN Inv i ON i.ID = m.ID
GROUP BY i.Mov, i.Estatus, i.Almacen, i.AlmacenDestino,
         CASE WHEN m.Articulo = l.clave_recibida          THEN 'clave base'
              WHEN m.Articulo = l.clave_recibida + '-1'   THEN 'clave -1'
              ELSE 'otra: ' + m.Articulo END
ORDER BY renglones DESC;

GO
PRINT '===== B4b. Vida de 5 lotes recibidos: recepción y movimientos INV, en orden de fecha =====';
WITH lotes AS (
    SELECT DISTINCT TOP 5 slm.SerieLote, slm.Articulo AS clave_recibida
    FROM Compra c
    JOIN SerieLoteMov slm ON slm.Modulo = 'COMS' AND slm.ID = c.ID
    WHERE c.Mov = 'Ent. Compra Farmacia' AND c.Almacen = '25301' AND c.Estatus = 'CONCLUIDO'
      AND c.FechaEmision >= DATEADD(month, -3, GETDATE())
)
SELECT TOP 60 l.SerieLote, l.clave_recibida, x.modulo, x.Mov, x.MovID, x.Estatus, x.FechaEmision,
       x.Almacen, x.AlmacenDestino, x.Articulo, x.Cantidad
FROM lotes l
CROSS APPLY (
    SELECT 'COMS' AS modulo, c.Mov, c.MovID, c.Estatus, c.FechaEmision, c.Almacen,
           CAST(NULL AS varchar(10)) AS AlmacenDestino, m.Articulo, m.Cantidad
    FROM SerieLoteMov m JOIN Compra c ON c.ID = m.ID
    WHERE m.Modulo = 'COMS' AND m.SerieLote = l.SerieLote
      AND m.Articulo IN (l.clave_recibida, l.clave_recibida + '-1')
    UNION ALL
    SELECT 'INV', i.Mov, i.MovID, i.Estatus, i.FechaEmision, i.Almacen, i.AlmacenDestino, m.Articulo, m.Cantidad
    FROM SerieLoteMov m JOIN Inv i ON i.ID = m.ID
    WHERE m.Modulo = 'INV' AND m.SerieLote = l.SerieLote
      AND m.Articulo IN (l.clave_recibida, l.clave_recibida + '-1')
) x
ORDER BY l.SerieLote, x.FechaEmision;

GO
PRINT '===== B4c. Tipos de movimiento INV que tienen renglones con clave base Y con clave -1 en el mismo movimiento =====';
SELECT TOP 20 i.Mov, COUNT(DISTINCT i.ID) AS movimientos
FROM Inv i
WHERE i.Estatus = 'CONCLUIDO'
  AND i.FechaEmision >= DATEADD(month, -6, GETDATE())
  AND EXISTS (SELECT 1 FROM InvD d1 WHERE d1.ID = i.ID AND d1.Articulo LIKE '0[14]0.000.%' AND d1.Articulo NOT LIKE '%-[0-9]')
  AND EXISTS (SELECT 1 FROM InvD d2 WHERE d2.ID = i.ID AND d2.Articulo LIKE '%-1')
GROUP BY i.Mov
ORDER BY movimientos DESC;


/* ---------------------------------------------------------------------
   B5. Sufijo de los lotes (SCC, CCC, U013, SFAS, SADMI...)
   --------------------------------------------------------------------- */
GO
PRINT '===== B5. Última palabra del lote en el 25301 (posible origen/programa) =====';
SELECT TOP 30
       CASE WHEN CHARINDEX(' ', RTRIM(s.SerieLote)) = 0 THEN '(sin sufijo)'
            ELSE RIGHT(RTRIM(s.SerieLote), CHARINDEX(' ', REVERSE(RTRIM(s.SerieLote))) - 1) END AS sufijo,
       COUNT(*) AS lotes, COUNT(DISTINCT s.Articulo) AS claves, SUM(s.Existencia) AS existencia
FROM SerieLote s
WHERE s.Almacen = '25301' AND s.Existencia > 0
GROUP BY CASE WHEN CHARINDEX(' ', RTRIM(s.SerieLote)) = 0 THEN '(sin sufijo)'
              ELSE RIGHT(RTRIM(s.SerieLote), CHARINDEX(' ', REVERSE(RTRIM(s.SerieLote))) - 1) END
ORDER BY lotes DESC;

GO
PRINT '===== Fin =====';
