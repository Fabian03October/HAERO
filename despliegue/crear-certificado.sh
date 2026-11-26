#!/bin/bash
# Crea la autoridad de HAERO y el certificado HTTPS del servidor (para que los celulares puedan usar la cámara).
# Uso: ./crear-certificado.sh 172.16.5.190 [carpeta]
# Deja en la carpeta:
#   haero-ca.crt   → se instala UNA vez en cada celular/computadora (ver DESPLIEGUE.md, "HTTPS en Windows")
#   haero.crt + haero.key → van a E:\SICOMED\nginx\ssl\ en el servidor
#   haero-ca.key   → la llave de la autoridad: guardarla en un lugar seguro (sirve para renovar); NO se copia al servidor.
set -euo pipefail
IP="${1:?Indica la IP del servidor, por ejemplo 172.16.5.190}"
DIR="${2:-$HOME/HAERO-certificados}"
mkdir -p "$DIR" && cd "$DIR"
if [ ! -f haero-ca.key ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -keyout haero-ca.key -out haero-ca.crt -days 3650 \
    -subj "/O=HRAEO/CN=Autoridad HAERO" -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"
fi
openssl req -newkey rsa:2048 -nodes -keyout haero.key -out haero.csr -subj "/O=HRAEO/CN=$IP"
cat > haero.ext <<X
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=IP:$IP,DNS:haero.hospital.local
X
# 825 días: el máximo que aceptan iPhone y Android para certificados de servidor.
openssl x509 -req -in haero.csr -CA haero-ca.crt -CAkey haero-ca.key -CAcreateserial -out haero.crt -days 825 -extfile haero.ext
rm -f haero.csr haero.ext
chmod 600 haero-ca.key haero.key
echo "Listo en $DIR · vence: $(openssl x509 -enddate -noout -in haero.crt | cut -d= -f2)"
