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

### Qué hace hoy

Como todavía no hay sensores en vivo, el backend trae un **emulador**: cada 4 segundos reales pasan 5 minutos simulados, los contenedores se llenan, los sensores mandan su nivel y los camiones recorren sus rutas. Con esos datos el sistema hace lo mismo que haría con datos reales:

- guarda cada lectura y aprende cómo se llena cada contenedor: su promedio por hora del día y día de la semana;
- con ese patrón pronostica a qué hora va a desbordar cada uno;
- calcula qué contenedores son prioritarios (ver abajo);
- detecta lecturas irregulares y sensores sin señal o congelados, y les estima el nivel;
- despacha un camión cuando una zona tiene 3 o más contenedores prioritarios o uno desbordado;
- abre incidentes y los cierra solos cuando se resuelven.

Un contenedor es prioritario por uno de estos motivos, en este orden:

| Motivo | Regla |
| --- | --- |
| `llenado` | Está al 80% o más |
| `riesgo` | Se pronostica que desborda en las próximas 3 horas, aunque hoy esté lejos del 80% |
| `reporte` | Tiene un reporte vecinal abierto |
| `tiempo` | Lleva más de 48 horas sin recolectarse y está al 20% o más |

Cuando un camión sale, además suma **paradas de paso**: contenedores al 50% o más que quedan a menos de 300 m de alguna parada. Cada parada de la ruta guarda por qué se pasa por ahí (`motivoVisita`).

El patrón de llenado se recalcula una vez por día con las lecturas de las últimas dos semanas. Donde todavía no hay datos suficientes se usa un ritmo fijo (pico de 18 a 21). Con el emulador, el patrón aprendido termina siendo parecido al fijo, porque el emulador llena los contenedores con ese mismo ritmo: la diferencia va a aparecer con sensores reales.

El emulador solo sabe lo que sabría el mundo real (cuánto se llenó cada contenedor, qué sensor se rompió). Al sistema le llega únicamente lo que mandaría un sensor: contenedor, nivel y hora. El día que haya sensores reales se apaga con `EMULADOR_ACTIVO=false` y las lecturas entran por `POST /api/v1/lecturas`.

La primera vez que arranca, carga 24 horas de lecturas para que las curvas no empiecen vacías, y la hora del sistema arranca a las 17:00, justo antes del pico. La hora simulada se guarda: al reiniciar el servidor, sigue donde estaba.

**Para empezar la demo de cero**, el director llama a `POST /api/v1/simulacion/reiniciar` (también desde Swagger). Borra lecturas, rutas, incidentes y lo aprendido, devuelve contenedores y camiones a su estado inicial y vuelve a cargar el historial de ejemplo. La simulación usa una semilla fija, así que después de reiniciar la demo sale siempre igual.

Si hace falta borrar la base entera (por ejemplo, porque cambió una migración):

```bash
dropdb -U ecoruta ecoruta && createdb ecoruta --owner ecoruta   # sin Docker
docker compose down -v && docker compose up -d                  # con Docker
```

### Endpoints

Todos van bajo `/api/v1`. Salvo el login y las lecturas, piden el token de director u operador.

| Método | Ruta | Qué hace |
| --- | --- | --- |
| POST | `/auth/login` | Público. Devuelve el token y los datos del usuario |
| GET | `/auth/me` | El usuario del token (cualquier rol) |
| GET | `/zonas` | Las 7 zonas con empresa, comunas, color, centro y base |
| GET | `/contenedores` | Contenedores con llenado, nivel, motivo de prioridad, estado del sensor y hora estimada de desborde. Filtro: `zonaId` |
| GET | `/contenedores/prioritarios` | Los que hay que ir a buscar, del más lleno al menos lleno. Filtro: `zonaId` |
| GET | `/contenedores/{id}` | Un contenedor |
| GET | `/contenedores/{id}/lecturas` | Lecturas del sensor para dibujar la curva. Parámetro: `horas` (24 por defecto) |
| POST | `/lecturas` | Entrada de los sensores reales. Usa el header `X-Api-Key` en vez de token |
| GET | `/camiones` | Camiones con chofer, estado, posición y las paradas de su ruta. Filtro: `zonaId` |
| GET | `/rutas` | Rutas del día. Filtros: `zonaId`, `estado` |
| GET | `/rutas/{id}` | Una ruta con sus paradas |
| POST | `/rutas/planificar` | Despacho manual: manda ahora el camión de una zona. Body: `zonaId` |
| GET | `/incidentes` | Incidentes. Filtros: `zonaId`, `tipo`, `estado` |
| POST | `/incidentes/{id}/decision` | El operador confirma o descarta. Body: `decision` |
| GET | `/reportes-vecinales` | Los incidentes que vienen de vecinos. Filtros: `zonaId`, `estado` |
| POST | `/reportes-vecinales` | Carga un reporte. Body: `contenedorId`, `comentario` |
| GET | `/panel/resumen` | Los números del día: recolectados, km evitados, prioritarios, sensores por estado. Filtro: `zonaId` |
| GET | `/simulacion` | Hora del sistema y si la simulación está en pausa |
| POST | `/simulacion` | Pausar o acelerar la demo. Body: `enPausa`, `velocidad` (1 a 20) |
| POST | `/simulacion/reiniciar` | Solo el director. Vuelve la demo al punto de partida |

Para probarlos desde Swagger: llamar a `/auth/login`, copiar el `token` de la respuesta y pegarlo con el botón **Authorize**. Desde la terminal:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"operador","contrasena":"operador123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl http://localhost:8080/api/v1/panel/resumen -H "Authorization: Bearer $TOKEN"
```

### Variables de entorno

Todas tienen un valor por defecto para desarrollo, en `src/main/resources/application.properties`.

| Variable | Para qué | Por defecto |
| --- | --- | --- |
| `PUERTO` | Puerto de la API | `8080` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/ecoruta`, `ecoruta`, `ecoruta` |
| `CORS_ORIGENES` | Desde dónde puede llamar el front | `http://localhost:5173` |
| `SENSORES_API_KEY` | Clave del header `X-Api-Key` del emulador | `clave-de-desarrollo` |
| `EMULADOR_ACTIVO` | Prende el emulador de sensores y choferes | `true` |
| `JWT_SECRETO` | Clave con la que se firman los tokens (32 caracteres o más) | Una de desarrollo; cambiarla al publicar la demo |

## Pruebas

```bash
./mvnw test
```

Las pruebas de integración levantan la aplicación completa contra un PostgreSQL de verdad. Usan Docker (Testcontainers) si está; si no, una base local que hay que crear una sola vez:

```bash
createdb ecoruta_test --owner ecoruta
```

Esa base se borra y se vuelve a armar en cada corrida: no usar la de la demo. Si no hay Docker ni base de prueba, esas pruebas se saltean y el resto corre igual. La conexión se cambia con `TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD`.

En las pruebas el emulador no avanza solo: cada prueba lo hace avanzar a mano con `avanzarPasos(n)`, así los resultados no dependen del tiempo.

## Estructura

```
src/main/java/ar/edu/uade/ecoruta/
  comun/        errores, seguridad y Swagger, compartidos por todos los módulos
  auth/         login, token y usuarios
  zonas/  camiones/  incidentes/
                un paquete por módulo, cada uno con su entidad, repositorio, DTO y controlador
  contenedores/ además tiene el patrón de llenado aprendido, el pronóstico y la regla de prioridad
  sensores/     entrada de lecturas, detección de anomalías y estado de cada sensor
  rutas/        despacho a demanda, orden de paradas y avance de la ruta
  panel/        números del día para el Panel general
  emulador/     hace de sensores y choferes mientras no haya datos en vivo
  (próximos)    tráfico, aviso al chofer, indicadores
src/main/resources/
  db/migration/ migraciones de Flyway (V1 esquema, V2 datos de ejemplo, V3 zonas oficiales, V4 sensores y reloj, V5 patrón de llenado, V6 nivel inicial para reiniciar la demo)
scripts/        generan V2 y V3 a partir de los datos del front
```

## Reglas para no pisarse

- **Nunca se edita una migración que ya está en `develop`.** Para cambiar una tabla se agrega una nueva: `V3__agregar_columna_x.sql`. Flyway compara cada archivo con lo que ya corrió y falla si alguno cambió.
- Si dos personas crean la misma versión (dos `V3`), la segunda en hacer merge renombra la suya a `V4`.
- La lógica de anomalías, sensores y orden de rutas es la misma que la de `src/logic/` del front. La prioridad parte de la del front y le suma el pronóstico con el patrón aprendido, el motivo `tiempo` y las paradas de paso, que el front todavía no conoce. Si se cambia una regla, hay que cambiarla en los dos lados hasta que el panel lea todo de la API. Los umbrales están en `comun/Parametros.java`.
- Para saber qué hora es, el código usa siempre `RelojDelSistema.ahora()`, nunca la hora de la máquina: así funciona igual con la hora simulada y con la real.
- Los DTO usan los mismos nombres de campo que `src/types/index.ts` del front, para que el panel pueda tipar las respuestas con lo que ya tiene.
- Los errores se responden con `{ "codigo", "mensaje" }`. Para un 404 se lanza `RecursoNoEncontradoException` y para un 409, `EstadoInvalidoException`. Lo demás lo resuelve `ManejadorDeErrores`.
