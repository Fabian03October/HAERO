/* =====================================================================
   Intelisis -> HAERO: muestras para validar la cadena de trazabilidad
   del medicamento (compra -> llegada -> lote -> almacén -> paciente).

   SOLO LECTURA. Todas las consultas usan TOP / DISTINCT para traer poco.
   Contiene datos de pacientes: no compartir los resultados fuera del
   equipo y borrarlos al terminar el análisis.

   Ejecutar bloque por bloque y guardar cada resultado como CSV
   (01_*.csv, 02_*.csv, ...).
   ===================================================================== */

SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;  -- no bloquear al ERP


/* ---------------------------------------------------------------------
   01. ¿Dónde vive "FONSABI / Ramo / Contrato"? (origen del recurso)
   Buscamos el texto en las columnas que suelen guardarlo.
   --------------------------------------------------------------------- */
SELECT 'Art.Rama'      AS Campo, Rama      AS Valor, COUNT(*) AS N FROM Art GROUP BY Rama
UNION ALL SELECT 'Art.Grupo',     Grupo,     COUNT(*) FROM Art GROUP BY Grupo
UNION ALL SELECT 'Art.Categoria', Categoria, COUNT(*) FROM Art GROUP BY Categoria
UNION ALL SELECT 'Art.Familia',   Familia,   COUNT(*) FROM Art GROUP BY Familia
UNION ALL SELECT 'Art.Linea',     Linea,     COUNT(*) FROM Art GROUP BY Linea
UNION ALL SELECT 'Art.CuadroBasico', CuadroBasico, COUNT(*) FROM Art GROUP BY CuadroBasico
ORDER BY Campo, N DESC;

SELECT 'Compra.Mov'             AS Campo, Mov             AS Valor, COUNT(*) AS N FROM Compra GROUP BY Mov
UNION ALL SELECT 'Compra.Concepto',        Concepto,        COUNT(*) FROM Compra GROUP BY Concepto
UNION ALL SELECT 'Compra.Proyecto',        Proyecto,        COUNT(*) FROM Compra GROUP BY Proyecto
UNION ALL SELECT 'Compra.TipoAdquisicion', TipoAdquisicion, COUNT(*) FROM Compra GROUP BY TipoAdquisicion
UNION ALL SELECT 'Compra.Estatus',         Estatus,         COUNT(*) FROM Compra GROUP BY Estatus
ORDER BY Campo, N DESC;

SELECT TOP 50 FuenteFinanciamiento, COUNT(*) AS N
FROM ClavePresupuestal GROUP BY FuenteFinanciamiento ORDER BY N DESC;

SELECT TOP 50 SubCuenta, Propiedades, COUNT(*) AS N
FROM SerieLote GROUP BY SubCuenta, Propiedades ORDER BY N DESC;

-- Búsqueda directa del texto
SELECT TOP 20 'Compra' AS Origen, ID, Mov, MovID, Concepto, Proyecto, TipoAdquisicion, Observaciones
FROM Compra
WHERE Concepto LIKE '%FONSA%' OR Proyecto LIKE '%FONSA%' OR Observaciones LIKE '%FONSA%'
   OR TipoAdquisicion LIKE '%FONSA%' OR Concepto LIKE '%INSABI%' OR Proyecto LIKE '%INSABI%';


/* ---------------------------------------------------------------------
   02. Tipos de movimiento de inventario (entradas, traspasos, salidas)
   --------------------------------------------------------------------- */
SELECT Mov, Estatus, COUNT(*) AS N, MIN(FechaEmision) AS Desde, MAX(FechaEmision) AS Hasta
FROM Inv GROUP BY Mov, Estatus ORDER BY N DESC;

SELECT Mov, Estatus, COUNT(*) AS N FROM Venta GROUP BY Mov, Estatus ORDER BY N DESC;

SELECT Almacen, Nombre, Tipo, Grupo, Categoria, Sucursal, Estatus FROM Alm ORDER BY Almacen;


/* ---------------------------------------------------------------------
   03. Compra -> llegada (entrada a almacén) de medicamento
   --------------------------------------------------------------------- */
SELECT TOP 20 c.ID, c.Mov, c.MovID, c.FechaEmision, c.FechaEntrega, c.Estatus, c.Almacen,
       c.Origen, c.OrigenID, d.Articulo, a.Descripcion1, d.Cantidad, d.CantidadPendiente,
       d.FechaCaducidad, d.ClavePresupuestal, d.CveCucop
FROM Compra c
JOIN CompraD d ON d.ID = c.ID
JOIN Art a     ON a.Articulo = d.Articulo
JOIN ArtMedicamento m ON m.Articulo = a.Articulo
ORDER BY c.FechaEmision DESC;

-- Lotes que entraron con esa compra
SELECT TOP 20 slm.Modulo, slm.ID, slm.RenglonID, slm.Articulo, slm.SerieLote,
       slm.Cantidad, slm.FechaCaducidad, slm.IDTableroFarmacia
FROM SerieLoteMov slm
WHERE slm.Modulo = 'COMS'
ORDER BY slm.RID DESC;

SELECT Modulo, COUNT(*) AS N FROM SerieLoteMov GROUP BY Modulo ORDER BY N DESC;


/* ---------------------------------------------------------------------
   04. Existencia actual por lote y caducidad
   --------------------------------------------------------------------- */
SELECT TOP 20 s.Almacen, s.Articulo, a.Descripcion1, s.SerieLote, s.Existencia,
       s.FechaCaducidad, s.UltimaEntrada, s.UltimaSalida
FROM SerieLote s
JOIN Art a ON a.Articulo = s.Articulo
WHERE s.Existencia > 0
ORDER BY s.FechaCaducidad;


/* ---------------------------------------------------------------------
   05. Despacho INTERNO (hospitalizado / quirófano): indicación -> surtido
       -> aplicación, con paciente, servicio, piso y cama
   --------------------------------------------------------------------- */
SELECT TOP 20 ih.ID_IngHosp, ih.Cliente AS Paciente, cte.Nombre AS NombrePaciente,
       ia.Servicio, ia.Area, ia.Piso, ia.Espacio AS Cama,
       im.ID_IndMedicamento, im.Articulo, im.Farmaco, im.Dosis, im.Unidad, im.Via,
       ap.FechaAplicado, ap.Aplicado, ap.Usuario AS Aplico, ap.Lote, ap.FechaCaducidad
FROM vsa_IndMedicamentoApli ap
JOIN vsa_IndMedicamento im ON im.ID_IndMedicamento = ap.ID_IndMedicamento
JOIN vsa_IngHosp ih        ON ih.ID_IngHosp = im.ID_IngHosp
LEFT JOIN vsa_IngArea ia   ON ia.ID_IngArea = im.ID_IngArea
LEFT JOIN Cte cte          ON cte.Cliente = ih.Cliente
ORDER BY ap.FechaAplicado DESC;

SELECT TOP 20 ms.*, msd.SurtidoFecha, msd.SurtioUsuario, msd.RecibeFecha,
       msd.RecibeUsuario, msd.Area AS AreaRecibe, msd.Folio AS FolioSurtido
FROM vsa_MedicamentoSurtir ms
LEFT JOIN vsa_MedicamentoSurtirD msd ON msd.ID_MedicamentoSurtir = ms.ID_MedicamentoSurtir
ORDER BY ms.FechaRegistro DESC;

-- Quirófano
SELECT TOP 20 * FROM vsa_MedicamentoSurtirQx ORDER BY FechaRegistro DESC;


/* ---------------------------------------------------------------------
   06. Tablero de farmacia: une prescripción, paciente, cama y el
       movimiento de inventario/lote con que se surtió
   --------------------------------------------------------------------- */
SELECT Tipo, Estatus, COUNT(*) AS N FROM FarmaciaTablero GROUP BY Tipo, Estatus ORDER BY N DESC;

SELECT TOP 20 ft.ID, ft.Tipo, ft.NoPrescripcion, ft.FechaPrescripcion, ft.Servicio, ft.Cama,
       ft.Paciente, ft.NombrePaciente, ft.Expediente, ft.Articulo, ft.CantidadSurtir,
       ft.Estatus, ft.UsuarioSurte, ft.ID_IndMedicamento,
       d.ID AS InvID, i.Mov, i.MovID, i.Almacen, i.AlmacenDestino,
       slm.SerieLote, slm.FechaCaducidad, slm.Cantidad AS CantidadLote
FROM FarmaciaTablero ft
LEFT JOIN InvD d          ON d.IDFarmaciaTablero = ft.ID
LEFT JOIN Inv i           ON i.ID = d.ID
LEFT JOIN SerieLoteMov slm ON slm.IDTableroFarmacia = ft.ID
ORDER BY ft.FechaPrescripcion DESC;


/* ---------------------------------------------------------------------
   07. Despacho EXTERNO (consulta -> receta -> farmacia)
   --------------------------------------------------------------------- */
SELECT Origen, Estatus, Interna, COUNT(*) AS N
FROM vsa_Receta GROUP BY Origen, Estatus, Interna ORDER BY N DESC;

SELECT TOP 20 r.ID_Receta, r.Folio, r.Fecha, r.ExpKey, r.Interna, r.Origen, r.Estatus,
       r.ID_IngHosp, r.ID_Venta AS RecetaVenta,
       rd.Articulo, rd.Farmaco, rd.Cantidad, rd.Unidad, rd.ID_Venta AS RenglonVenta,
       v.Mov, v.MovID, v.FechaEmision AS FechaDespacho, v.Almacen, v.Cliente
FROM vsa_Receta r
JOIN vsa_RecetaD rd ON rd.ID_Receta = r.ID_Receta
LEFT JOIN Venta v   ON v.ID = COALESCE(rd.ID_Venta, r.ID_Venta)
ORDER BY r.Fecha DESC;

-- Lotes que salieron en esa venta/despacho
SELECT TOP 20 slm.* FROM SerieLoteMov slm WHERE slm.Modulo = 'VTAS' ORDER BY slm.RID DESC;


/* ---------------------------------------------------------------------
   08. Un lote de punta a punta (cambiar el valor de @Lote por uno real
       sacado del bloque 04)
   --------------------------------------------------------------------- */
DECLARE @Lote varchar(50) = 'CAMBIAR';
SELECT slm.Modulo, slm.ID, slm.RenglonID, slm.Articulo, slm.SerieLote, slm.Cantidad,
       slm.FechaCaducidad, slm.IDTableroFarmacia,
       COALESCE(i.Mov, v.Mov, c.Mov)                   AS Mov,
       COALESCE(i.MovID, v.MovID, c.MovID)             AS MovID,
       COALESCE(i.FechaEmision, v.FechaEmision, c.FechaEmision) AS Fecha,
       COALESCE(i.Almacen, v.Almacen, c.Almacen)       AS Almacen,
       i.AlmacenDestino, i.Paciente, i.NoPrescripcion, v.Cliente
FROM SerieLoteMov slm
LEFT JOIN Inv i    ON slm.Modulo = 'INV'  AND i.ID = slm.ID
LEFT JOIN Venta v  ON slm.Modulo = 'VTAS' AND v.ID = slm.ID
LEFT JOIN Compra c ON slm.Modulo = 'COMS' AND c.ID = slm.ID
WHERE slm.SerieLote = @Lote
ORDER BY Fecha;


/* ---------------------------------------------------------------------
   09. Movimientos especiales: cómo los nombra y guarda Intelisis
       (canje, caducados/bajas, préstamos, transferencias)
   --------------------------------------------------------------------- */
-- Catálogo de tipos de movimiento: el nombre que usa el hospital (Mov)
-- y la clave estándar de Intelisis que dice qué hace (Clave)
SELECT Modulo, Mov, Clave, SubClave, Grupo
FROM MovTipo WHERE Modulo IN ('INV', 'COMS', 'VTAS')
ORDER BY Modulo, Clave, Mov;

-- ¿Qué tipos se usan de verdad y cuántas veces?
SELECT i.Mov, mt.Clave, i.Estatus, COUNT(*) AS N,
       SUM(CASE WHEN i.AlmacenDestino IS NOT NULL THEN 1 ELSE 0 END) AS ConDestino,
       SUM(CASE WHEN i.MotivoBaja IS NOT NULL THEN 1 ELSE 0 END)     AS ConMotivoBaja,
       SUM(CASE WHEN i.Proveedor IS NOT NULL OR i.AcreedorCxc IS NOT NULL THEN 1 ELSE 0 END) AS ConTercero
FROM Inv i
LEFT JOIN MovTipo mt ON mt.Modulo = 'INV' AND mt.Mov = i.Mov
GROUP BY i.Mov, mt.Clave, i.Estatus ORDER BY N DESC;

-- Canje / cambio de lote (lote viejo -> lote nuevo)
SELECT TOP 20 * FROM CambioSerieLote ORDER BY FechaCambio DESC;
SELECT TOP 20 * FROM SerieLoteMovCanje;

-- Bajas (caducados, mermas): catálogo de causas y ejemplos
SELECT * FROM CatalogoCausaBaja;
SELECT TOP 20 ID, Mov, MovID, FechaEmision, Almacen, MotivoBaja, Observaciones, Referencia
FROM Inv WHERE MotivoBaja IS NOT NULL ORDER BY FechaEmision DESC;

-- Posibles préstamos / transferencias con otras instituciones
SELECT TOP 20 ID, Mov, MovID, FechaEmision, Almacen, AlmacenDestino, Proveedor, AcreedorCxc,
       Referencia, Observaciones, Concepto
FROM Inv
WHERE Mov LIKE '%PREST%' OR Mov LIKE '%TRANSF%' OR Concepto LIKE '%PREST%'
   OR Observaciones LIKE '%PREST%' OR Observaciones LIKE '%INSTITU%'
ORDER BY FechaEmision DESC;

-- Cadena entre movimientos (origen -> destino), p. ej. solicitud -> salida
SELECT TOP 20 * FROM MovFlujo WHERE OModulo = 'INV' OR DModulo = 'INV' ORDER BY DID DESC;
