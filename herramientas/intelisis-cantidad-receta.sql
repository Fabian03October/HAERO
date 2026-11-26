-- ¿La cantidad de vsa_RecetaD son PIEZAS o CAJAS? (solo lectura, Intelisis)
-- Compara cada renglón de receta firmada (últimos 60 días) con lo que Farmacia dio de baja a ese
-- paciente de esa clave en los 7 días siguientes ("Consumo Farmacia", en piezas: clave "-1").
--   baja ≈ Cantidad                    → la receta está en PIEZAS
--   baja ≈ Cantidad × UnidadesEnvase   → la receta está en CAJAS
SET NOCOUNT ON;

WITH receta AS (
    SELECT r.ID_Receta, r.Fecha, c.Nombre AS paciente, d.Articulo AS clave, d.Cantidad, am.UnidadesEnvase
    FROM vsa_Receta r WITH (NOLOCK)
    JOIN vsa_RecetaD d WITH (NOLOCK) ON d.ID_Receta = r.ID_Receta
    JOIN Cte c WITH (NOLOCK) ON c.Cliente = r.ExpKey
    JOIN ArtMedicamento am WITH (NOLOCK) ON am.Articulo = d.Articulo
    WHERE r.Estatus = 'FIRMADO' AND r.Fecha >= DATEADD(day, -60, GETDATE())
      AND d.Cantidad > 0 AND am.UnidadesEnvase > 1
),
baja AS (
    SELECT rc.ID_Receta, rc.clave, rc.Cantidad, rc.UnidadesEnvase, SUM(ABS(s.Cantidad)) AS piezasBaja
    FROM receta rc
    JOIN Inv i WITH (NOLOCK) ON i.Mov = 'Consumo Farmacia' AND i.Estatus = 'CONCLUIDO'
         AND LTRIM(RTRIM(i.Referencia)) = LTRIM(RTRIM(rc.paciente))
         AND i.FechaEmision >= rc.Fecha AND i.FechaEmision < DATEADD(day, 7, rc.Fecha)
    JOIN SerieLoteMov s WITH (NOLOCK) ON s.Modulo = 'INV' AND s.ID = i.ID AND s.Articulo = rc.clave + '-1'
    GROUP BY rc.ID_Receta, rc.clave, rc.Cantidad, rc.UnidadesEnvase
)
SELECT
    COUNT(*) AS renglones_con_baja,
    SUM(CASE WHEN piezasBaja = Cantidad THEN 1 ELSE 0 END) AS igual_a_piezas,
    SUM(CASE WHEN piezasBaja = Cantidad * UnidadesEnvase THEN 1 ELSE 0 END) AS igual_a_cajas,
    SUM(CASE WHEN piezasBaja <> Cantidad AND piezasBaja <> Cantidad * UnidadesEnvase THEN 1 ELSE 0 END) AS otro
FROM baja;

-- Ejemplos para revisar a mano (sin nombres de pacientes). La CTE se repite: solo vale para una consulta.
WITH receta AS (
    SELECT r.ID_Receta, r.Fecha, c.Nombre AS paciente, d.Articulo AS clave, d.Cantidad, am.UnidadesEnvase
    FROM vsa_Receta r WITH (NOLOCK)
    JOIN vsa_RecetaD d WITH (NOLOCK) ON d.ID_Receta = r.ID_Receta
    JOIN Cte c WITH (NOLOCK) ON c.Cliente = r.ExpKey
    JOIN ArtMedicamento am WITH (NOLOCK) ON am.Articulo = d.Articulo
    WHERE r.Estatus = 'FIRMADO' AND r.Fecha >= DATEADD(day, -60, GETDATE())
      AND d.Cantidad > 0 AND am.UnidadesEnvase > 1
),
baja AS (
    SELECT rc.ID_Receta, rc.clave, rc.Cantidad, rc.UnidadesEnvase, SUM(ABS(s.Cantidad)) AS piezasBaja
    FROM receta rc
    JOIN Inv i WITH (NOLOCK) ON i.Mov = 'Consumo Farmacia' AND i.Estatus = 'CONCLUIDO'
         AND LTRIM(RTRIM(i.Referencia)) = LTRIM(RTRIM(rc.paciente))
         AND i.FechaEmision >= rc.Fecha AND i.FechaEmision < DATEADD(day, 7, rc.Fecha)
    JOIN SerieLoteMov s WITH (NOLOCK) ON s.Modulo = 'INV' AND s.ID = i.ID AND s.Articulo = rc.clave + '-1'
    GROUP BY rc.ID_Receta, rc.clave, rc.Cantidad, rc.UnidadesEnvase
)
SELECT TOP 20 b.clave, b.Cantidad AS cantidad_receta, b.UnidadesEnvase AS piezas_por_caja, b.piezasBaja AS piezas_dadas_de_baja
FROM baja b ORDER BY CASE WHEN b.piezasBaja = b.Cantidad * b.UnidadesEnvase THEN 1 ELSE 0 END, b.ID_Receta DESC;
