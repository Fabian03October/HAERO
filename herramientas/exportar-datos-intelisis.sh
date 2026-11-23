#!/bin/sh
# Genera respaldo-datos/datos-intelisis.sql desde la base de la Mac (contenedor "sqlserver").
# Uso: SA_PASSWORD=... sh herramientas/exportar-datos-intelisis.sh
set -e
cd "$(dirname "$0")/.."
mkdir -p respaldo-datos
docker cp herramientas/exportar-datos-intelisis.sql sqlserver:/tmp/exportar-datos-intelisis.sql
# -y 0 no deja quitar encabezados con -h: se quitan aquí (la columna se llama XHDR_HAERO y la sigue una línea de guiones)
docker exec sqlserver /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "${SA_PASSWORD:?pon SA_PASSWORD}" \
    -d farmacia -y 0 -f 65001 -b -i /tmp/exportar-datos-intelisis.sql \
  | awk 'saltar { saltar = 0; next } $0 == "XHDR_HAERO" { saltar = 1; next } { print }' \
  > respaldo-datos/datos-intelisis.sql
echo "Listo: respaldo-datos/datos-intelisis.sql ($(wc -c < respaldo-datos/datos-intelisis.sql) bytes)"
