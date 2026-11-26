-- ¿Por qué un lote que está en el anaquel no sale en HAERO? (solo lectura, Intelisis)
-- Cambia la clave (sin "-1") y el lote que encontraron en el anaquel (o parte de él).
-- HAERO solo copia lotes con existencia > 0 de los almacenes 25301 y 25301-C. Aquí salen TODOS los registros.
SET NOCOUNT ON;
DECLARE @clave VARCHAR(30) = '010.000.0000.00';
DECLARE @lote  VARCHAR(50) = '';   -- vacío = todos los lotes de la clave

-- 1) El lote en cualquier almacén, con o sin existencia.
SELECT s.Almacen, s.Articulo, s.SerieLote, s.Existencia, s.FechaCaducidad, s.UltimaEntrada, s.UltimaSalida,
       CASE WHEN s.Almacen NOT IN ('25301', '25301-C') THEN 'Otro almacén: HAERO no lo copia'
            WHEN s.Existencia <= 0 THEN 'Existencia 0 en Intelisis: HAERO no lo copia'
            ELSE 'Sí lo copia HAERO' END AS por_que
FROM SerieLote s WITH (NOLOCK)
WHERE (s.Articulo = @clave OR s.Articulo LIKE @clave + '-%')
  AND (@lote = '' OR s.SerieLote LIKE '%' + @lote + '%')
ORDER BY s.SerieLote, s.Almacen;

-- 2) Si no salió nada arriba: ¿el lote está con otra clave? (código o clave equivocada)
IF @lote <> ''
SELECT TOP 50 s.Almacen, s.Articulo, s.SerieLote, s.Existencia, s.FechaCaducidad
FROM SerieLote s WITH (NOLOCK)
WHERE s.SerieLote LIKE '%' + @lote + '%'
ORDER BY s.Existencia DESC;
