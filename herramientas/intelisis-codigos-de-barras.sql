/* =====================================================================
   Intelisis -> HAERO: dónde guarda Intelisis los códigos de barras y
   si están llenos para los medicamentos.

   SOLO LECTURA (SELECT con NOLOCK). No toca tablas de pacientes.
   Correr en SQL Server Management Studio contra la base HRAEO_2015.
   Medicamentos de Farmacia = claves que empiezan con 010.000. o 040.000.
   ===================================================================== */

USE HRAEO_2015;
GO

-- 1) Columnas cuyo nombre suena a código de barras, GTIN, EAN o QR (sin tablas de pacientes).
SELECT t.name AS tabla, c.name AS columna, ty.name AS tipo, c.max_length AS largo
FROM sys.columns c
JOIN sys.tables t ON t.object_id = c.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE (c.name LIKE '%barra%' OR c.name LIKE '%GTIN%' OR c.name LIKE '%EAN%' OR c.name LIKE '%QR%'
       OR c.name LIKE 'CB%' OR c.name IN ('Codigo', 'CodigoAlterno'))
  AND t.name NOT LIKE 'vsa%' AND t.name NOT LIKE 'Cte%' AND t.name NOT LIKE 'Venta%' AND t.name NOT LIKE 'FarmaciaTablero%'
ORDER BY t.name, c.name;

-- 2) Tablas de códigos de barras y cuántos renglones tienen.
SELECT t.name AS tabla, SUM(p.rows) AS renglones
FROM sys.tables t
JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
WHERE t.name IN ('CB', 'ProvCB', 'CompraCB', 'LecturaCBFarmacia', 'LecturaCBFarmaciaNoExistencia', 'CBNoEncontrados')
GROUP BY t.name
ORDER BY t.name;

-- 3) Tabla CB (catálogo de códigos de barras): cuántos son de medicamentos.
SELECT CASE WHEN Cuenta LIKE '0[14]0.000.%' THEN 'medicamento'
            WHEN Cuenta LIKE 'G-0%' THEN 'farmacia gratuita'
            ELSE 'otro' END AS tipo,
       COUNT(*) AS codigos
FROM CB WITH (NOLOCK)
GROUP BY CASE WHEN Cuenta LIKE '0[14]0.000.%' THEN 'medicamento'
              WHEN Cuenta LIKE 'G-0%' THEN 'farmacia gratuita'
              ELSE 'otro' END;

-- 4) Los códigos de CB que sí son de medicamentos (hoy solo hay 2, de 2014).
SELECT Codigo, Cuenta AS articulo, Unidad, Cantidad, SerieLote, Fecha
FROM CB WITH (NOLOCK)
WHERE Cuenta LIKE '0[14]0.000.%' OR Cuenta LIKE 'G-0%';

-- 5) Campos Codigo y CodigoAlterno del catálogo (Art) en las claves de Farmacia: cuántas los tienen llenos.
SELECT COUNT(*) AS claves,
       SUM(CASE WHEN NULLIF(LTRIM(a.Codigo), '') IS NOT NULL THEN 1 ELSE 0 END) AS con_codigo,
       SUM(CASE WHEN NULLIF(LTRIM(a.CodigoAlterno), '') IS NOT NULL THEN 1 ELSE 0 END) AS con_codigo_alterno
FROM Art a WITH (NOLOCK)
WHERE a.Articulo LIKE '0[14]0.000.%';

-- 6) Campo CBFarmacia de los movimientos de inventario: cuántos lo traen y en qué tipo de movimiento.
SELECT i.Mov, COUNT(*) AS movimientos, MAX(i.FechaEmision) AS ultimo
FROM Inv i WITH (NOLOCK)
WHERE NULLIF(LTRIM(i.CBFarmacia), '') IS NOT NULL
GROUP BY i.Mov
ORDER BY COUNT(*) DESC;

-- 7) Configuración de la empresa para trabajar con códigos de barras (existe, aunque no se use).
SELECT c.name AS opcion
FROM sys.columns c
WHERE c.object_id = OBJECT_ID('EmpresaCfg') AND c.name LIKE 'CB%';
