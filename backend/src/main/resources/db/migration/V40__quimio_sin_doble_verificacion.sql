-- Quimioterapia: se quitó la doble verificación por otra persona (a petición de Farmacia). Queda
-- registrado quién surtió; "verifico_id" se conserva para las quimios ya registradas y las nuevas van vacías.
ALTER TABLE quimio_preparacion ALTER COLUMN verifico_id BIGINT NULL;
