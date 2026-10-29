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
