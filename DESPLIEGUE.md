# Instalación de HAERO en el servidor del hospital

Guía para el Área de TICs (HU43). HAERO tiene tres piezas:

| Pieza | Qué es | Dónde corre |
|---|---|---|
| Backend | Spring Boot 4 (Java 21), puerto 8080 | Servicio en el servidor |
| Frontend | Angular compilado (archivos estáticos) | Servido por nginx |
| Base de datos | SQL Server (base `farmacia`) | Servidor de base de datos |

Intelisis se lee en modo de **solo lectura**; HAERO nunca escribe en él.

## 1. Requisitos

- Java 21 y acceso a la red de Intelisis.
- SQL Server con una base vacía `farmacia` y un usuario con permiso para crear tablas (Flyway las crea).
- nginx.
- Node 20 o superior, solo para compilar el frontend (puede compilarse en otra computadora).

## 2. Base de datos

1. Crear la base y el usuario de la aplicación.
2. Activar la lectura sin bloqueos, para que las pantallas no esperen mientras se sincroniza Intelisis:

   ```sql
   ALTER DATABASE farmacia SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
   ```

3. **Antes de cada actualización, sacar un respaldo.** Al arrancar, el backend aplica solo las migraciones nuevas (`backend/src/main/resources/db/migration`), y una migración no se puede deshacer:

   ```sql
   BACKUP DATABASE farmacia TO DISK = 'D:\respaldos\farmacia-AAAAMMDD.bak' WITH INIT, COMPRESSION;
   ```

   Para regresar: `RESTORE DATABASE farmacia FROM DISK = '...' WITH REPLACE;` con el backend detenido.

## 3. Backend

1. Compilar: `cd backend && ./mvnw -DskipTests package`. Esto genera `target/backend-*.jar`.
2. Copiar `backend/.env.example` como `.env` junto al jar y llenar los valores. **El `.env` no se sube a git ni se comparte.**
   - `SPRING_PROFILES_ACTIVE=prod` es obligatorio en el servidor: así Hibernate no modifica tablas (solo Flyway) y no se registran las consultas.
   - `JWT_SECRET`: al menos 32 caracteres al azar. Si cambia, todas las sesiones se cierran.
   - `CORS_ORIGENES`: la dirección desde la que se abre HAERO, por ejemplo `http://haero.hospital.local`.
3. Arrancar como servicio. Ejemplo con systemd:

   ```ini
   [Unit]
   Description=HAERO backend
   After=network.target

   [Service]
   WorkingDirectory=/opt/haero/backend
   EnvironmentFile=/opt/haero/backend/.env
   ExecStart=/usr/bin/java -jar /opt/haero/backend/backend.jar
   Restart=on-failure
   User=haero

   [Install]
   WantedBy=multi-user.target
   ```

4. Revisar el registro del primer arranque: las migraciones aplicadas y la primera sincronización con Intelisis (alrededor de un minuto después).

## 4. Frontend

1. `cd hraeo-farmacia-frontend && npm ci && npm run build`.
2. Copiar el contenido de `dist/hraeo-farmacia-frontend/browser/` a `/opt/haero/frontend`.
3. Instalar `despliegue/nginx-haero.conf`, ajustar `server_name` y recargar nginx.

Desde cualquier equipo que no sea el propio servidor, el frontend llama a `/api` y nginx lo reenvía al backend, así que no hay que configurar nada más.

## 4.1 HTTPS (necesario para el celular)

La cámara del celular o la tableta como lector de códigos solo funciona en una página segura (`https://`). Computadoras y celulares abren **la misma dirección**, por ejemplo `https://haero.hospital.local`.

1. **Conseguir el certificado.** Debe incluir el nombre y la IP fija del servidor.
   - **Recomendado:** que TICs lo emita con la autoridad de certificados del hospital (si tienen una; las computadoras del dominio ya confían en ella).
   - **Si no hay:** crear una autoridad propia de HAERO **en el servidor o en un equipo de TICs** (no en la laptop de un desarrollador), por ejemplo con mkcert:

     ```bash
     export CAROOT=/root/haero-ca            # carpeta protegida; aquí queda la llave de la autoridad
     mkcert -cert-file haero.pem -key-file haero-key.pem haero.hospital.local 172.16.x.x
     ```

     El archivo `/root/haero-ca/rootCA.pem` es el que se instala en los equipos (paso 3). La llave `rootCA-key.pem` **no sale de esa carpeta**.
2. **nginx:** copiar `haero.pem` y `haero-key.pem` a `/etc/nginx/ssl/`, ajustar `server_name` en `despliegue/nginx-haero.conf` y recargar nginx. Quien entre por `http://` se manda solo a `https://`.
3. **Confiar en el certificado** (solo si no es de la autoridad del hospital), una vez por equipo:
   - **Windows:** doble clic en `rootCA.pem` (renombrado a `.crt`) → Instalar certificado → Equipo local → "Entidades de certificación raíz de confianza". TICs puede hacerlo para todas por directiva de grupo.
   - **iPhone:** abrir el archivo (correo o AirDrop) → Ajustes → "Perfil descargado" → Instalar; después Ajustes → General → Información → Ajustes de confianza de certificados → activar la autoridad de HAERO.
   - **Android:** Ajustes → Seguridad → Cifrado y credenciales → Instalar un certificado → Certificado de CA.
4. **Backend:** en el `.env`, `CORS_ORIGENES=https://haero.hospital.local` (y la dirección con IP si también se usa). Reiniciar el backend.
5. **Celulares:** deben estar en la red Wi-Fi del hospital y resolver el nombre `haero.hospital.local`; si el Wi-Fi no usa el DNS del hospital, abrir HAERO con la IP (`https://172.16.x.x`), que también va en el certificado.
6. **Renovación:** anotar la fecha de vencimiento del certificado y renovarlo antes; al vencer, el navegador bloquea la página.

## 5. Usuarios

- La migración V25 **desactiva** los usuarios de prueba `almacen`, `farmacia` y `supervision` si siguen con su contraseña de prueba.
- El administrador inicial es `admin`. En su primer inicio de sesión el sistema le obliga a cambiar la contraseña: **hacerlo antes de que el servidor quede abierto a la red.**
- Crear una cuenta **por persona** desde Administración → Usuarios. Compartir una cuenta impide saber quién hizo cada movimiento, y con la sesión única activada (`SEGURIDAD_SESION_UNICA=true`) cada persona que entra saca a la otra.
- Todo cambio a las cuentas queda en Administración → Bitácora.

### Si el administrador olvida su contraseña

1. Poner en el `.env` la línea `ADMIN_RESTABLECER_CONTRASENA=` con una contraseña temporal (mínimo 8 caracteres, con letra y número).
2. Reiniciar el backend. El registro avisa que se restableció.
3. El administrador entra con la temporal y el sistema le pide cambiarla.
4. **Borrar la línea del `.env` y reiniciar otra vez.** Mientras siga puesta, cada arranque la vuelve a aplicar.

## 6. Limitaciones conocidas

- El cierre por 15 minutos de inactividad se lleva en la memoria del backend: al reiniciarlo, el contador empieza de nuevo. HAERO está pensado para **un solo servidor** de backend.
- Si Intelisis no responde, HAERO sigue funcionando con la última copia sincronizada y reintenta cada 15 minutos.
