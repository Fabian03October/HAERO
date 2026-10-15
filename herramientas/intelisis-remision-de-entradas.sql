/* =====================================================================
   Intelisis -> HAERO: el documento del proveedor (factura o remisión) de
   cada entrada de compra de Farmacia.

   SOLO LECTURA (SELECT con NOLOCK). No toca tablas de pacientes.
   Correr en SQL Server Management Studio contra la base HRAEO_2015.

   Intelisis no tiene un campo llamado "remisión". El número del documento
   que trae el proveedor se captura en NoFactura, y en Observaciones se
   repite con su fecha (por ejemplo "BIOF-24742 13/10/2026").
   ===================================================================== */

USE HRAEO_2015;
GO

-- Número a buscar (factura o remisión del proveedor). Déjalo vacío ('') para ver las más recientes.
DECLARE @buscar VARCHAR(50) = '';

-- 1) Entradas de compra de Farmacia con su documento del proveedor, el pedido y el contrato.
SELECT TOP 200
       e.MovID                         AS entrada,
       e.FechaEmision                  AS fecha_entrada,
       e.NoFactura                     AS factura_o_remision,
       CAST(e.Observaciones AS VARCHAR(100)) AS observaciones,
       pr.Nombre                       AS proveedor,
       e.OrigenID                      AS pedido,
       e.NoContratoInterno             AS contrato,
       e.Referencia                    AS referencia
FROM Compra e WITH (NOLOCK)
LEFT JOIN Prov pr WITH (NOLOCK) ON pr.Proveedor = e.Proveedor
WHERE e.Mov = 'Ent. Compra Farmacia'
  AND e.Estatus = 'CONCLUIDO'
  AND (@buscar = '' OR e.NoFactura LIKE '%' + @buscar + '%' OR CAST(e.Observaciones AS VARCHAR(300)) LIKE '%' + @buscar + '%')
ORDER BY e.FechaEmision DESC, e.ID DESC;

-- 2) Cuántas entradas del último año traen el documento del proveedor.
SELECT COUNT(*) AS entradas,
       SUM(CASE WHEN NULLIF(LTRIM(e.NoFactura), '') IS NOT NULL THEN 1 ELSE 0 END) AS con_factura_o_remision
FROM Compra e WITH (NOLOCK)
WHERE e.Mov = 'Ent. Compra Farmacia' AND e.FechaEmision >= DATEADD(year, -1, GETDATE());

-- 3) Comprobación: Intelisis no tiene columnas llamadas "remisión" en compras, cuentas por pagar ni inventario.
SELECT t.name AS tabla, c.name AS columna
FROM sys.columns c
JOIN sys.tables t ON t.object_id = c.object_id
WHERE c.name LIKE '%Remisi%' AND (t.name LIKE 'Compra%' OR t.name LIKE 'Cxp%' OR t.name LIKE 'Inv%');
