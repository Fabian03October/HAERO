/* =====================================================================
   Intelisis -> HAERO: de dónde salió cada lote que hay en el almacén
   de medicamentos (25301): de un FONDO (FONSABI) o de un CONTRATO
   (compra del hospital), con su orden de suministro o su contrato.

   SOLO LECTURA. No toca tablas de pacientes. Corre en SQL Server 2012.

   Cómo se decide la fuente (por el movimiento con que ENTRÓ el lote, no
   por el texto de la orden, que se captura de muchas formas):
     - FONDO (FONSABI): Inv "Donacion FONSABI" (o INSABI / UNOPS).
         Orden de suministro = Inv.Referencia, p. ej.
         AEOO-20-02-2026-20262339-SADMI. El prefijo (AEOO, AEO, AEOB,
         AEO0, AEIO, IMBB...) varía según quién capturó: no sirve para
         decidir.
     - CONTRATO: Compra "Ent. Compra Farmacia", "Compra Farm Pasivo" o
         "Entrada Compra". Contrato = Compra.NoContratoInterno
         (IB-HRAEO-...), procedimiento = Compra.Concepto (licitación,
         adjudicación) y pedido = Compra.OrigenID.
     - TRASPASO: Inv "IMSS EntradaTraspaso" / "Recibo Traspaso" (oficio en
         Referencia u Observaciones).
     - DONACIÓN: otras donaciones. PRÉSTAMO: "Entrada por Prestamo".
     - MIXTO: el mismo lote entró por más de una fuente (ver Documentos).
   El lote se busca con la clave -1 y con la clave base (las compras
   entran sin -1 y una Conversion las pasa a -1 el mismo día).
   ===================================================================== */

SET NOCOUNT ON;
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;  -- no bloquear al ERP

IF OBJECT_ID('tempdb..#lotes') IS NOT NULL DROP TABLE #lotes;
-- Algunas claves traen tabuladores o saltos de línea pegados en Intelisis: Limpia es la clave sin ellos.
SELECT s.Articulo, s.SerieLote, s.Existencia, s.FechaCaducidad,
       LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(s.Articulo, CHAR(9), ''), CHAR(10), ''), CHAR(13), ''))) AS Limpia
INTO #lotes
FROM SerieLote s
WHERE s.Almacen = '25301' AND s.Existencia > 0;

ALTER TABLE #lotes ADD Base varchar(50);
UPDATE #lotes SET Base = CASE WHEN Limpia LIKE '%-1' THEN LEFT(Limpia, LEN(Limpia) - 2) ELSE Limpia END;

IF OBJECT_ID('tempdb..#entradas') IS NOT NULL DROP TABLE #entradas;
SELECT DISTINCT l.Articulo, l.SerieLote,
       COALESCE(i.Mov, c.Mov) AS Mov,
       COALESCE(i.FechaEmision, c.FechaEmision) AS Fecha,
       CASE
         WHEN COALESCE(i.Mov, c.Mov) LIKE '%FONSABI%' OR COALESCE(i.Mov, c.Mov) LIKE '%INSABI%'
           OR COALESCE(i.Mov, c.Mov) LIKE '%UNOPS%' OR COALESCE(i.Concepto, c.Concepto) IN ('FONSABI', 'INSABI')
           THEN 'FONDO (FONSABI)'
         WHEN c.Mov IN ('Ent. Compra Farmacia', 'Compra Farm Pasivo', 'Entrada Compra') THEN 'CONTRATO'
         WHEN i.Mov IN ('IMSS EntradaTraspaso', 'Recibo Traspaso') THEN 'TRASPASO'
         WHEN i.Mov = 'Entrada por Prestamo' THEN 'PRESTAMO'
         ELSE 'DONACION'
       END AS Fuente,
       LTRIM(RTRIM(CASE
         WHEN c.ID IS NOT NULL THEN 'Contrato ' + COALESCE(NULLIF(LTRIM(c.NoContratoInterno), ''), 'sin número')
                                    + COALESCE(' · ' + NULLIF(LTRIM(c.Concepto), ''), '')
                                    + COALESCE(' · Pedido ' + NULLIF(LTRIM(c.OrigenID), ''), '')
         WHEN i.Mov LIKE '%FONSABI%' OR i.Mov LIKE '%INSABI%' OR i.Mov LIKE '%UNOPS%'
           THEN 'Orden ' + COALESCE(NULLIF(LTRIM(i.Referencia), ''), 'sin número')
         ELSE i.Mov + COALESCE(' · ' + NULLIF(LTRIM(i.Referencia), ''), COALESCE(' · ' + NULLIF(LTRIM(i.Observaciones), ''), ''))
       END)) AS Documento,
       COALESCE(p.Nombre, LTRIM(RTRIM(i.Observaciones))) AS Proveedor
INTO #entradas
FROM #lotes l
JOIN SerieLoteMov m ON m.SerieLote = l.SerieLote AND m.Articulo IN (l.Articulo, l.Limpia, l.Base)
LEFT JOIN Inv i    ON m.Modulo = 'INV'  AND i.ID = m.ID AND i.Estatus = 'CONCLUIDO'
LEFT JOIN Compra c ON m.Modulo = 'COMS' AND c.ID = m.ID AND c.Estatus = 'CONCLUIDO'
LEFT JOIN Prov p   ON p.Proveedor = c.Proveedor
WHERE COALESCE(i.Mov, c.Mov) IN ('Donacion FONSABI', 'Donacion INSABI', 'Entrada UNOPS', 'Ent. Compra Farmacia', 'Compra Farm Pasivo',
      'Entrada Compra', 'IMSS EntradaTraspaso', 'Recibo Traspaso', 'Donacion Farmacia', 'Donacion Almacen', 'Suminis Medicamentos',
      'Entrada por Prestamo');

-- 1) Una fila por lote.
SELECT l.Limpia AS Clave,
       LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(a.Descripcion1, CHAR(9), ' '), CHAR(10), ' '), CHAR(13), ' '))) AS Medicamento,
       LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(l.SerieLote, CHAR(9), ' '), CHAR(10), ' '), CHAR(13), ' '))) AS Lote,
       CONVERT(varchar(10), l.FechaCaducidad, 23) AS Caducidad,
       l.Existencia,
       CASE WHEN l.Limpia LIKE '%-1' THEN 'Piezas' ELSE 'Cajas' END AS Unidad,
       CASE (SELECT COUNT(DISTINCT e.Fuente) FROM #entradas e WHERE e.Articulo = l.Articulo AND e.SerieLote = l.SerieLote)
         WHEN 0 THEN 'SIN ENTRADA EN INTELISIS'
         WHEN 1 THEN (SELECT TOP 1 e.Fuente FROM #entradas e WHERE e.Articulo = l.Articulo AND e.SerieLote = l.SerieLote)
         ELSE 'MIXTO'
       END AS Fuente,
       -- Todas las órdenes o contratos con que entró el lote, del más reciente al más viejo.
       STUFF((SELECT DISTINCT ' | ' + e.Documento FROM #entradas e WHERE e.Articulo = l.Articulo AND e.SerieLote = l.SerieLote
              FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 3, '') AS Documentos,
       (SELECT TOP 1 e.Proveedor FROM #entradas e WHERE e.Articulo = l.Articulo AND e.SerieLote = l.SerieLote ORDER BY e.Fecha DESC) AS Proveedor,
       (SELECT CONVERT(varchar(10), MIN(e.Fecha), 23) FROM #entradas e WHERE e.Articulo = l.Articulo AND e.SerieLote = l.SerieLote) AS PrimeraEntrada
FROM #lotes l
JOIN Art a ON a.Articulo = l.Articulo
ORDER BY Fuente, l.Limpia, l.FechaCaducidad;

-- 2) Resumen por fuente.
SELECT Fuente, COUNT(*) AS Lotes, COUNT(DISTINCT Base) AS Claves
FROM (
  SELECT l.Base,
         CASE (SELECT COUNT(DISTINCT e.Fuente) FROM #entradas e WHERE e.Articulo = l.Articulo AND e.SerieLote = l.SerieLote)
           WHEN 0 THEN 'SIN ENTRADA EN INTELISIS'
           WHEN 1 THEN (SELECT TOP 1 e.Fuente FROM #entradas e WHERE e.Articulo = l.Articulo AND e.SerieLote = l.SerieLote)
           ELSE 'MIXTO'
         END AS Fuente
  FROM #lotes l
) x
GROUP BY Fuente
ORDER BY Lotes DESC;
