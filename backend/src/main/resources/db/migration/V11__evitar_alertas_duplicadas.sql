-- Bug encontrado al correr el sistema en vivo (2026-10-06): dos procesos
-- evaluando alertas casi al mismo tiempo (p. ej. "mvnw test" y
-- "mvnw spring-boot:run" contra la misma base) podian pasar los dos la
-- validacion "no existe una alerta abierta" antes de que cualquiera de los
-- dos guardara la suya, dejando dos filas abiertas para la misma clave/lote
-- y tipo. Eso rompia AlertaRepository.findBy...FechaFinIsNull (esperaba un
-- unico resultado) con NonUniqueResultException.
--
-- El codigo ya se corrigio para autocurar duplicados que existan, pero el
-- blindaje real es a nivel de base: un indice unico filtrado (solo sobre
-- las filas con fecha_fin NULL, o sea las alertas abiertas) impide que se
-- puedan insertar dos abiertas para la misma combinacion, sin importar
-- cuantos procesos esten corriendo a la vez.

CREATE UNIQUE INDEX UX_alerta_stock_abierta
    ON alerta (medicamento_clave, tipo)
    WHERE fecha_fin IS NULL AND lote_id IS NULL;

CREATE UNIQUE INDEX UX_alerta_caducidad_abierta
    ON alerta (lote_id, tipo)
    WHERE fecha_fin IS NULL AND lote_id IS NOT NULL;
