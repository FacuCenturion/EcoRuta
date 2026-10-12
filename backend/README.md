# EcoRuta: backend

API REST del panel de Higiene Urbana, del emulador de sensores y de la app del chofer. Los endpoints, el modelo de datos y el plan de trabajo están en la guía de backend del equipo.

Stack: Java 21, Spring Boot 4.1, PostgreSQL 17, Flyway, Spring Security, springdoc (Swagger).

## Requisitos

- JDK 21 o superior (`java -version`)
- Una base PostgreSQL. Hay dos formas de tenerla:
  - **Con Docker** (recomendado): instalar Docker Desktop y levantar la base con `docker compose up -d` desde esta carpeta.
  - **Sin Docker** (macOS): `brew install postgresql@17`, `brew services start postgresql@17` y crear el usuario y la base:
    ```bash
    createuser ecoruta --pwprompt   # contraseña: ecoruta
    createdb ecoruta --owner ecoruta
    ```

Maven no hace falta instalarlo: el proyecto trae `./mvnw`.

## Cómo ejecutar

Desde la carpeta `backend/`:

```bash
docker compose up -d   # solo si usás Docker
./mvnw spring-boot:run
```

Al arrancar, Flyway crea las tablas y carga los datos de ejemplo. La API queda en http://localhost:8080:

| Qué | Dónde |
| --- | --- |
| Swagger (probar los endpoints) | http://localhost:8080/api/docs |
| Contrato OpenAPI | http://localhost:8080/api/openapi |
| Estado del servidor | http://localhost:8080/actuator/health |

### Usuarios de prueba

| Rol | Usuario | Contraseña |
| --- | --- | --- |
| Director | `director` | `director123` |
| Operador | `operador` | `operador123` |
| Chofer (uno por camión) | `chofer22`, `chofer31`, `chofer14`, `chofer17`, `chofer5`, `chofer26`, `chofer12` | `chofer123` |

### Endpoints que ya funcionan

| Método | Ruta | Quién | Qué devuelve |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | Público | Token y datos del usuario |
| GET | `/api/v1/auth/me` | Cualquier usuario con token | El usuario del token |
| GET | `/api/v1/zonas` | Director, Operador | Las 7 zonas con empresa, comunas, color, centro y base |
| GET | `/api/v1/contenedores` | Director, Operador | Contenedores con su llenado. Filtro: `zonaId` |
| GET | `/api/v1/contenedores/{id}` | Director, Operador | Un contenedor |
| GET | `/api/v1/camiones` | Director, Operador | Camiones con chofer, estado y posición. Filtro: `zonaId` |
| GET | `/api/v1/incidentes` | Director, Operador | Incidentes. Filtros: `zonaId`, `tipo`, `estado` |

Para probarlos desde Swagger: llamar a `/auth/login`, copiar el `token` de la respuesta y pegarlo con el botón **Authorize**. Desde la terminal:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"director","contrasena":"director123"}'

curl http://localhost:8080/api/v1/zonas -H "Authorization: Bearer <token>"
```

Los datos todavía son los de ejemplo, quietos: el llenado empieza a moverse cuando exista el emulador de sensores.

### Variables de entorno

Todas tienen un valor por defecto para desarrollo, en `src/main/resources/application.properties`.

| Variable | Para qué | Por defecto |
| --- | --- | --- |
| `PUERTO` | Puerto de la API | `8080` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/ecoruta`, `ecoruta`, `ecoruta` |
| `CORS_ORIGENES` | Desde dónde puede llamar el front | `http://localhost:5173` |
| `SENSORES_API_KEY` | Clave del header `X-Api-Key` del emulador | `clave-de-desarrollo` |
| `JWT_SECRETO` | Clave con la que se firman los tokens (32 caracteres o más) | Una de desarrollo; cambiarla al publicar la demo |

## Pruebas

```bash
./mvnw test
```

Las pruebas que levantan la aplicación completa usan Testcontainers: crean un PostgreSQL temporal en Docker. Si Docker no está, se saltean y el resto corre igual.

## Estructura

```
src/main/java/ar/edu/uade/ecoruta/
  comun/        errores, seguridad y Swagger, compartidos por todos los módulos
  auth/         login, token y usuarios
  zonas/  contenedores/  camiones/  incidentes/
                un paquete por módulo, cada uno con su entidad, repositorio, DTO y controlador
  (próximos)    lecturas de sensores, rutas, indicadores
src/main/resources/
  db/migration/ migraciones de Flyway (V1 esquema, V2 datos de ejemplo, V3 zonas oficiales)
scripts/        generan V2 y V3 a partir de los datos del front
```

## Reglas para no pisarse

- **Nunca se edita una migración que ya está en `develop`.** Para cambiar una tabla se agrega una nueva: `V3__agregar_columna_x.sql`. Flyway compara cada archivo con lo que ya corrió y falla si alguno cambió.
- Si dos personas crean la misma versión (dos `V3`), la segunda en hacer merge renombra la suya a `V4`.
- Los DTO usan los mismos nombres de campo que `src/types/index.ts` del front, para que el panel pueda tipar las respuestas con lo que ya tiene.
- Los errores se responden con `{ "codigo", "mensaje" }`. Para un 404 se lanza `RecursoNoEncontradoException` y para un 409, `EstadoInvalidoException`. Lo demás lo resuelve `ManejadorDeErrores`.
