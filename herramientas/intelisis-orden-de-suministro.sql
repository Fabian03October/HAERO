/* =====================================================================
   Intelisis -> HAERO: búsqueda de la orden de suministro ("HRAEO" + números).

   SOLO LECTURA (SELECT con NOLOCK). No toca tablas de pacientes.
   Correr en SQL Server Management Studio contra la base HRAEO_2015.

   Lo más útil: escribe en @buscar el número de una orden de suministro real
   (tal como viene en el papel o el PDF) y corre la parte 1.
   ===================================================================== */

USE HRAEO_2015;
GO

-- Número a buscar, tal como viene en la orden (puedes poner solo una parte, por ejemplo los números).
DECLARE @buscar VARCHAR(100) = 'HRAEO';

/* ---------------------------------------------------------------------
   1) ¿En qué campo de los pedidos, entradas o contratos aparece ese texto?
   --------------------------------------------------------------------- */
SELECT c.Mov, c.MovID, c.FechaEmision, v.campo, v.valor
FROM Compra c WITH (NOLOCK)
CROSS APPLY (VALUES
    ('Referencia', c.Referencia), ('Observaciones', CAST(c.Observaciones AS VARCHAR(300))),
    ('NoProcedimiento', c.NoProcedimiento), ('NoContratoInterno', c.NoContratoInterno),
    ('Documento', c.Documento), ('Concepto', c.Concepto), ('NoFactura', c.NoFactura),
    ('AccionCompra', CAST(c.AccionCompra AS VARCHAR(300))), ('ccaop', c.ccaop),
    ('ContratoMovID', c.ContratoMovID), ('Origen', c.Origen), ('OrigenID', c.OrigenID)
) v(campo, valor)
WHERE c.Mov IN ('Pedido Proveedor', 'Ent. Compra Farmacia')
  AND v.valor LIKE '%' + @buscar + '%'
ORDER BY c.FechaEmision DESC;

SELECT x.Mov, x.MovID, v.campo, v.valor
FROM Contrato x WITH (NOLOCK)
CROSS APPLY (VALUES
    ('Contrato', x.Contrato), ('Referencia', x.Referencia), ('Titulo', x.Titulo),
    ('Observaciones', CAST(x.Observaciones AS VARCHAR(300))), ('NoProcedimientoCompranet', x.NoProcedimientoCompranet)
) v(campo, valor)
WHERE v.valor LIKE '%' + @buscar + '%';

-- En el nombre o la ruta de los archivos adjuntos a pedidos y entradas.
SELECT c.Mov, c.MovID, a.Nombre, a.Direccion
FROM AnexoMov a WITH (NOLOCK)
JOIN Compra c WITH (NOLOCK) ON c.ID = a.ID
WHERE a.Rama = 'COMS' AND (a.Nombre LIKE '%' + @buscar + '%' OR a.Direccion LIKE '%' + @buscar + '%');

/* ---------------------------------------------------------------------
   2) Lo que se encontró al revisarlo (octubre de 2026), para verlo uno mismo
   --------------------------------------------------------------------- */

-- "HRAEO" seguido de números en cualquier campo de compras: no aparece en pedidos de medicamento.
SELECT x.Mov, v.campo, COUNT(*) AS renglones, MIN(v.valor) AS ejemplo
FROM Compra x WITH (NOLOCK)
CROSS APPLY (VALUES ('Referencia', x.Referencia), ('Observaciones', CAST(x.Observaciones AS VARCHAR(300))),
                    ('NoProcedimiento', x.NoProcedimiento), ('Documento', x.Documento), ('MovID', x.MovID)) v(campo, valor)
WHERE v.valor LIKE '%HRAEO[0-9]%' OR v.valor LIKE '%HRAEO [0-9]%' OR v.valor LIKE '%HRAEO-[0-9]%' OR v.valor LIKE '%HRAEO/[0-9]%'
GROUP BY x.Mov, v.campo
ORDER BY COUNT(*) DESC;

-- Lo que sí traen los pedidos del último año: procedimiento (CompraNet u oficio) y contrato.
SELECT TOP 30 c.MovID AS pedido, c.FechaEmision, c.NoProcedimiento, c.NoContratoInterno, c.Referencia
FROM Compra c WITH (NOLOCK)
WHERE c.Mov = 'Pedido Proveedor' AND c.Almacen = '25301' AND c.FechaEmision >= DATEADD(year, -1, GETDATE())
ORDER BY c.FechaEmision DESC;

-- Rutas de los archivos adjuntos a los pedidos más recientes (actas de fallo y oficios).
SELECT TOP 20 c.MovID AS pedido, a.Nombre, a.Direccion
FROM AnexoMov a WITH (NOLOCK)
JOIN Compra c WITH (NOLOCK) ON c.ID = a.ID
WHERE a.Rama = 'COMS' AND c.Mov = 'Pedido Proveedor'
ORDER BY c.ID DESC;
