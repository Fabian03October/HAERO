/* ============================================================
   DESCUBRIMIENTO DE ESQUEMA - Intelisis (HRAEOBI)
   Úsalo con el usuario de SOLO LECTURA (sqlFarmacia).
   Solo LEE catálogos del sistema; no modifica nada.

   Cómo usarlo en SQL Server Management Studio (SSMS):
   1. Conéctate al servidor.
   2. Arriba, en la lista de bases, elige la base que te interese
      (o corre primero la CONSULTA 0 para ver cuáles hay).
   3. Abre una "New Query", pega esto y ejecuta (F5).
   4. Guarda cada resultado: clic derecho en la cuadrícula ->
      "Save Results As..." -> CSV. Mándame esos archivos.
   ============================================================ */

-- CONSULTA 0: bases de datos visibles para este usuario
SELECT name AS base_de_datos
FROM sys.databases
WHERE HAS_DBACCESS(name) = 1
ORDER BY name;

-- CONSULTA 1: todas las tablas (con su esquema y conteo de columnas)
SELECT s.name  AS esquema,
       t.name  AS tabla,
       (SELECT COUNT(*) FROM sys.columns c WHERE c.object_id = t.object_id) AS num_columnas
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
ORDER BY s.name, t.name;

-- CONSULTA 2: todas las columnas de todas las tablas (tipo y si acepta nulos)
SELECT s.name  AS esquema,
       t.name  AS tabla,
       c.column_id AS orden,
       c.name  AS columna,
       ty.name AS tipo,
       CASE WHEN ty.name IN ('varchar','nvarchar','char','nchar')
            THEN CASE WHEN c.max_length = -1 THEN 'MAX'
                      WHEN ty.name IN ('nvarchar','nchar') THEN CAST(c.max_length/2 AS varchar)
                      ELSE CAST(c.max_length AS varchar) END
            ELSE NULL END AS longitud,
       CASE WHEN c.is_nullable = 1 THEN 'SI' ELSE 'NO' END AS acepta_nulos,
       CASE WHEN c.is_identity = 1 THEN 'SI' ELSE 'NO' END AS es_identity
FROM sys.columns c
JOIN sys.tables t  ON t.object_id = c.object_id
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.types ty  ON ty.user_type_id = c.user_type_id
ORDER BY s.name, t.name, c.column_id;

-- CONSULTA 3: llaves primarias (qué columna(s) identifican cada tabla)
SELECT s.name AS esquema,
       t.name AS tabla,
       kc.name AS nombre_pk,
       c.name  AS columna,
       ic.key_ordinal AS orden
FROM sys.key_constraints kc
JOIN sys.tables t  ON t.object_id = kc.parent_object_id
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE kc.type = 'PK'
ORDER BY s.name, t.name, ic.key_ordinal;

-- CONSULTA 4: llaves foráneas (cómo se conectan las tablas entre sí)
SELECT  s1.name AS esquema_origen,
        t1.name AS tabla_origen,
        c1.name AS columna_origen,
        s2.name AS esquema_destino,
        t2.name AS tabla_destino,
        c2.name AS columna_destino,
        fk.name AS nombre_fk
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
JOIN sys.tables  t1 ON t1.object_id = fk.parent_object_id
JOIN sys.schemas s1 ON s1.schema_id = t1.schema_id
JOIN sys.columns c1 ON c1.object_id = fkc.parent_object_id  AND c1.column_id = fkc.parent_column_id
JOIN sys.tables  t2 ON t2.object_id = fk.referenced_object_id
JOIN sys.schemas s2 ON s2.schema_id = t2.schema_id
JOIN sys.columns c2 ON c2.object_id = fkc.referenced_object_id AND c2.column_id = fkc.referenced_column_id
ORDER BY s1.name, t1.name, fk.name, fkc.constraint_column_id;

-- CONSULTA 5 (opcional): vistas (Intelisis suele exponer datos en vistas)
SELECT s.name AS esquema, v.name AS vista
FROM sys.views v
JOIN sys.schemas s ON s.schema_id = v.schema_id
ORDER BY s.name, v.name;
