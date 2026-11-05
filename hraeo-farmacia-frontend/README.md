# HAERO · Frontend

Aplicación web del sistema HAERO, hecha en Angular 21 con TypeScript. Las instrucciones completas del proyecto (backend, base de datos e integración con Intelisis) están en el [README principal](../README.md).

## Comandos

| Comando | Para qué |
|---|---|
| `npm install` | Instala las dependencias (la primera vez) |
| `npm start` | Servidor de desarrollo en http://localhost:4200 |
| `npm run start:red` | Igual, pero accesible desde otros equipos de la red |
| `npm run start:ambos` | HTTP en el puerto 4200 y HTTPS en el 4201 (para usar la cámara del celular) |
| `npx ng build` | Compila la aplicación para producción en `dist/` |
| `npx ng test --watch=false` | Corre las pruebas unitarias (Vitest) |

El frontend se comunica con el backend por la API REST en `/api`. Desde la misma computadora llama directo a `http://localhost:8080/api`; desde otro equipo, el servidor de desarrollo reenvía `/api` al backend (`proxy.conf.json`).

## Organización de `src/app`

| Carpeta | Qué contiene |
|---|---|
| `auth/` | Inicio de sesión, cambio de contraseña y políticas de uso |
| `admin/` | Administración de usuarios (rol Administrador) |
| `almacen/` | Recepción, inventario, existencias, despacho, rotación, alertas, movimientos especiales y avisos de Intelisis |
| `farmacia/` | Solicitudes de medicamento a Almacén |
| `supervision/` | Consultas y reportes en solo lectura |
| `shared/` | Componentes reutilizables: menú lateral, barra superior, escáner con cámara, avisos y lectura de códigos GS1 |

Cada pantalla tiene su propia carpeta con su componente (`.ts`), su plantilla (`.html`) y sus estilos (`.css`).
