# HAERO · Farmacia HRAEO

Sistema web para el control del Almacén de Medicamentos y la dispensación del Servicio de Farmacia Hospitalaria del Hospital Regional de Alta Especialidad de Oaxaca (HRAEO).

El sistema se llama **HAERO**; **HRAEO** es el hospital. Se desarrolla con la metodología Extreme Programming (XP), por iteraciones:

| Iteración | Contenido | Historias |
|---|---|---|
| 1 | Autenticación y control de usuarios | HU01–HU09 |
| 2 | Módulo de Almacén de Medicamentos e integración de solo lectura con Intelisis | HU10–HU24 |
| 3 | Módulo de Farmacia (dispensación) | En desarrollo |

## Estructura del proyecto

```
HAERO/
├── backend/                  API REST en Spring Boot 4.1 (Java 21)
│   └── src/
│       ├── main/java/com/example/backend/
│       │   ├── config/       Configuración general (dialecto de SQL Server, etc.)
│       │   ├── security/     Inicio de sesión con JWT y permisos por rol
│       │   ├── controller/   Servicios REST de autenticación y usuarios
│       │   ├── service/      Reglas de negocio de autenticación y usuarios
│       │   ├── entity/       Tablas de usuarios y roles
│       │   ├── repository/   Acceso a datos de usuarios y roles
│       │   ├── dto/          Datos que entran y salen de la API
│       │   ├── exception/    Manejo de errores de toda la API
│       │   ├── almacen/      Módulo de Almacén (controller, service, repository, entity, dto)
│       │   └── intelisis/    Conexión de solo lectura y sincronización con Intelisis
│       ├── main/resources/
│       │   ├── application.properties
│       │   └── db/migration/ Migraciones de Flyway (V1 a V20)
│       └── test/             Pruebas unitarias (JUnit 5 y Mockito)
├── hraeo-farmacia-frontend/  Aplicación web en Angular 21
│   └── src/app/
│       ├── auth/             Inicio de sesión, cambio de contraseña y políticas de uso
│       ├── admin/            Administración de usuarios
│       ├── almacen/          Recepción, existencias, despacho, rotación y movimientos especiales
│       ├── farmacia/         Solicitudes a Almacén (la dispensación es de la Iteración 3)
│       ├── supervision/      Consultas y reportes en solo lectura
│       └── shared/           Componentes reutilizables (menú, barra superior, escáner, avisos)
├── herramientas/             Consultas SQL de solo lectura para revisar datos en Intelisis
└── docs/                     Documentación de las iteraciones
```

El backend sigue el patrón **Controlador → Servicio → Repositorio**: los controladores reciben las peticiones, los servicios aplican las reglas de negocio y los repositorios acceden a la base de datos.

## Requisitos

- Java 21
- Node.js 20 o superior y npm
- SQL Server (la edición Express funciona) con una base `farmacia` y un usuario `farmacia_app` con permisos sobre ella

## Configuración del backend

El backend lee estas variables de entorno. Ninguna contraseña se guarda en el repositorio.

| Variable | Obligatoria | Para qué |
|---|---|---|
| `DB_PASSWORD` | Sí | Contraseña del usuario `farmacia_app` |
| `DB_URL` | No | Cadena JDBC; por defecto `localhost\SQLEXPRESS`, base `farmacia` |
| `DB_USERNAME` | No | Por defecto `farmacia_app` |
| `JWT_SECRET` | No | Clave para firmar las sesiones (mínimo 32 caracteres). Si no se define, se crea una al primer arranque en `~/.hraeo/jwt-secret` y se reutiliza, así reiniciar no cierra las sesiones |

Ejemplo en PowerShell, para la terminal actual:

```powershell
$env:DB_PASSWORD = "la-contraseña"
```

### Integración con Intelisis (opcional)

Intelisis es el sistema administrativo del hospital. HAERO lo consulta en **solo lectura** (nunca escribe en él) y copia cada 15 minutos el catálogo, los pedidos, las entradas y la existencia a tablas de consulta propias (`intelisis_*`). Si no se configura, el backend arranca sin integración.

| Variable | Para qué |
|---|---|
| `INTELISIS_HABILITADO` | `true` para activar la integración |
| `INTELISIS_URL` | Cadena JDBC del servidor de Intelisis |
| `INTELISIS_USUARIO` / `INTELISIS_PASSWORD` | Usuario de solo lectura |
| `INTELISIS_TLS_HEREDADO` | `true` si el servidor solo acepta TLS 1.0 (SQL Server 2012) |

Las credenciales de Intelisis se comparten por fuera del repositorio.

## Levantar el proyecto

**Backend** (puerto 8080):

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Al arrancar, Flyway aplica solo las migraciones pendientes (`backend/src/main/resources/db/migration`). Hibernate está en modo `validate`: si una entidad no coincide con la base, el backend no arranca y dice qué falta. Todo cambio de tablas va en una migración nueva (`V21__...sql`), nunca editando una ya aplicada.

**Frontend** (puerto 4200):

```bash
cd hraeo-farmacia-frontend
npm install
npm start
```

Abrir http://localhost:4200.

### Desde un celular en la misma red

- `npm run start:red`: el celular entra por `http://<IP de la computadora>:4200`. Las llamadas a `/api` se reenvían al backend (ver `proxy.conf.json`).
- `npm run start:ambos`: además levanta HTTPS en el puerto 4201, necesario para usar la cámara como lector de códigos en el iPhone. Usa los certificados de `hraeo-farmacia-frontend/certs/` (`haero.pem` y `haero-key.pem`), que no se suben al repositorio y deben incluir la IP de la computadora. En el iPhone hay que instalar y marcar como confiable la autoridad `public/haero-ca.crt`.

## Usuarios y roles

Hay cuatro roles: **Administrador**, **Almacén**, **Farmacia** y **Supervisión** (solo lectura). Las migraciones V2 y V3 crean un usuario de prueba por rol: `admin`, `almacen`, `farmacia` y `supervision`. Sus contraseñas no están en el repositorio; pídelas al equipo. Con el administrador se crean usuarios nuevos, que entran con contraseña temporal y la cambian al primer inicio de sesión.

Seguridad del inicio de sesión:
- La sesión se cierra tras 15 minutos de inactividad.
- 5 intentos fallidos seguidos bloquean la cuenta 10 minutos. Restablecer la contraseña desde Administración la desbloquea.
- Al entrar por primera vez, cada usuario acepta las políticas de uso y privacidad. Queda registrado en el servidor con fecha y hora, y se vuelve a pedir si cambia la versión.

## Pruebas

```bash
cd backend && ./mvnw test                                   # backend
cd hraeo-farmacia-frontend && npx ng test --watch=false     # frontend
```

`BackendApplicationTests` arranca la aplicación completa contra la base configurada, así que también aplica las migraciones pendientes.

## Carpetas de apoyo

- **`herramientas/`**: consultas SQL de solo lectura para revisar en Intelisis la existencia, los lotes, la orden de suministro y la factura o remisión de las entradas. Se ejecutan en SQL Server Management Studio y no modifican nada.
- **`docs/`**: documentación de las iteraciones (historias de usuario, diseño y plan de integración con Intelisis).

## Pendientes conocidos

- **Orden de suministro.** No está en Intelisis; se espera obtenerla del sistema del equipo de Contratos.
- **Cajas sin registro en Intelisis.** Por definir qué hace Almacén con una caja que llega antes de que la capturen en Intelisis, o que está en el anaquel pero Intelisis tiene en cero.
- **Módulo de Farmacia.** La dispensación corresponde a la Iteración 3.
- **PDF en la base de datos.** Los documentos de préstamos y transferencias se guardan en `documento_externo` (máximo 10 MB cada uno). Si crecen mucho, conviene moverlos a una carpeta del servidor.
