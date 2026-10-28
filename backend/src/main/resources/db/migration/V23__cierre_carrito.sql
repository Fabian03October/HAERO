-- Cierre del carrito del día de Hospitalización (por área). Desde el cierre, lo que se indique
-- se muestra como "después del carrito": se surte si urge o queda pendiente para el día siguiente.
CREATE TABLE farmacia_carrito_cierre (
    central VARCHAR(10) NOT NULL,
    dia DATE NOT NULL,
    cerrado_en DATETIME2 NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    CONSTRAINT PK_farmacia_carrito_cierre PRIMARY KEY (central, dia)
);
