-- HU38: consumo real de Farmacia en Intelisis por clave y mes, ya sumado ("Consumo Farmacia"
-- menos "Devolucion Farmacia"), para el CPM mientras HAERO no tenga 6 meses de salidas propias.
-- Lo llena la sincronización; la clave base (sin "-1") trae cajas y la "-1" piezas.
CREATE TABLE intelisis_consumo_mes (
    clave_base VARCHAR(20) NOT NULL,
    mes CHAR(7) NOT NULL,
    cajas DECIMAL(19, 4) NOT NULL,
    piezas DECIMAL(19, 4) NOT NULL,
    CONSTRAINT PK_intelisis_consumo_mes PRIMARY KEY (clave_base, mes)
);
