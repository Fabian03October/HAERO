/* =====================================================================
   Intelisis -> HAERO: lotes del almacén 25301 marcados como FONSABI o
   No FONSABI, según el movimiento con que entró cada lote.

   SOLO LECTURA. No toca tablas de pacientes.

   Intelisis no guarda la fuente de financiamiento en la compra
   (ClavePresupuestal.FuenteFinanciamiento viene vacía). Por eso se toma
   del movimiento de entrada del lote:
     - FONSABI:     Inv "Donacion FONSABI", o movimientos UNOPS/INSABI, o
                    Concepto FONSABI/INSABI.
     - Compra del hospital: Compra "Ent. Compra Farmacia", "Compra Farm
                    Pasivo" o "Entrada Compra" (Concepto = adjudicación o
                    licitación).
     - Traspaso IMSS u otra donación: el resto de las entradas.
   El lote se busca con la clave -1 y con la clave base (las compras entran
   sin -1 y una Conversion las pasa a -1 el mismo día).
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
SELECT l.Articulo, l.SerieLote,
       COALESCE(i.Mov, c.Mov)                   AS Mov,
       COALESCE(i.MovID, c.MovID)               AS MovID,
       COALESCE(i.Concepto, c.Concepto)         AS Concepto,
       COALESCE(i.FechaEmision, c.FechaEmision) AS Fecha,
       CASE
         WHEN COALESCE(i.Mov, c.Mov) LIKE '%FONSABI%' OR COALESCE(i.Mov, c.Mov) LIKE '%INSABI%'
           OR COALESCE(i.Mov, c.Mov) LIKE '%UNOPS%' OR COALESCE(i.Concepto, c.Concepto) IN ('FONSABI', 'INSABI')
           THEN 'FONSABI'
         WHEN COALESCE(i.Mov, c.Mov) IN ('Ent. Compra Farmacia', 'Compra Farm Pasivo', 'Entrada Compra')
           THEN 'NO FONSABI - Compra del hospital'
         ELSE 'NO FONSABI - Traspaso IMSS / otra donacion'
       END AS Financiamiento
INTO #entradas
FROM #lotes l
JOIN SerieLoteMov m ON m.SerieLote = l.SerieLote AND m.Articulo IN (l.Articulo, l.Limpia, l.Base)
LEFT JOIN Inv i    ON m.Modulo = 'INV'  AND i.ID = m.ID AND i.Estatus = 'CONCLUIDO'
LEFT JOIN Compra c ON m.Modulo = 'COMS' AND c.ID = m.ID AND c.Estatus = 'CONCLUIDO'
WHERE COALESCE(i.Mov, c.Mov) IN ('Donacion FONSABI', 'Ent. Compra Farmacia', 'Compra Farm Pasivo', 'IMSS EntradaTraspaso',
      'Donacion Farmacia', 'Entrada Compra', 'Suminis Medicamentos', 'Recibo Traspaso', 'Entrada UNOPS', 'Donacion INSABI',
      'Donacion Almacen', 'Entrada por Prestamo');

-- Una fila por lote: si alguna entrada es FONSABI manda esa; si no, la primera entrada.
WITH ordenadas AS (
  SELECT e.*, ROW_NUMBER() OVER (PARTITION BY e.Articulo, e.SerieLote
           ORDER BY CASE WHEN e.Financiamiento = 'FONSABI' THEN 0 ELSE 1 END, e.Fecha) AS n
  FROM #entradas e
)
SELECT l.Limpia AS Clave,
       LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(a.Descripcion1, CHAR(9), ' '), CHAR(10), ' '), CHAR(13), ' '))) AS Medicamento,
       LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(l.SerieLote, CHAR(9), ' '), CHAR(10), ' '), CHAR(13), ' '))) AS Lote,
       CONVERT(varchar(10), l.FechaCaducidad, 23) AS Caducidad,
       l.Existencia AS Piezas,
       COALESCE(o.Financiamiento, 'Sin entrada en Intelisis') AS Financiamiento,
       o.Mov AS EntroPor, o.Concepto, o.MovID AS Folio,
       CONVERT(varchar(10), o.Fecha, 23) AS FechaEntrada
FROM #lotes l
JOIN Art a ON a.Articulo = l.Articulo
LEFT JOIN ordenadas o ON o.Articulo = l.Articulo AND o.SerieLote = l.SerieLote AND o.n = 1
ORDER BY Financiamiento, l.Articulo, l.FechaCaducidad;
