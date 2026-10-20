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

### Con qué datos trabaja

El sistema trabaja con **una sola zona, la 1 (Comuna 1)**, con **180 contenedores reales** y **5 camiones**, uno por sector y un segundo en el sector 1:

| Sector | Barrios | Contenedores | Camiones |
| --- | --- | --- | --- |
| 1 | Retiro y San Nicolás | 55 | C22 y C40 |
| 2 | Monserrat | 40 | C31 |
| 3 | San Telmo y Puerto Madero | 45 | C14 |
| 4 | Constitución | 40 | C17 |

Las ubicaciones salen de la planilla oficial "Ubicación contenedores RSU - fracción húmedos" (marzo 2025), que la Ciudad entregó adjunta a la [nota NO-2025-19872282-GCABA-DGLIM](https://buenosaires.gob.ar/sites/default/files/2025-07/NO-2025-19872282-GCABA-DGLIM.pdf) en respuesta a un pedido de acceso a la información. Cada dirección se convirtió en coordenadas con el normalizador oficial de la Ciudad (USIG), que la ubica sobre la calle. Se tomó una muestra al azar por barrio, porque los 2.253 contenedores de la Comuna 1 harían el emulador demasiado pesado. La misma Ciudad aclara que la planilla puede no coincidir del todo con la vía pública.

Para regenerar la muestra: `pip install openpyxl pypdf` y `python backend/scripts/importar_contenedores.py`. Deja la muestra en `backend/scripts/datos/contenedores_zona1.csv` y arma la migración. Si la muestra cambia, va en una migración nueva.

Cada contenedor tiene su tipo: lateral (3.200 litros), bilateral (1.100 litros) o soterrado. Uno chico se llena antes. Las otras 6 zonas siguen en la base para dibujar el mapa, sin contenedores ni camiones.

El sector 1 tiene dos camiones porque sus 40 contenedores chicos de oficinas se llenan todos a la vez al mediodía. Cuando hay más de un camión en un sector, no toman el mismo contenedor.

### La simulación: qué pasa y qué tiene que deducir el sistema

El emulador conoce la "verdad" del mundo y el sistema no: solo recibe contenedor, nivel y hora. Cada caso está cubierto por una prueba en `CasosDeSimulacionTests`.

| Caso | Qué simula el emulador | Qué hace el sistema |
| --- | --- | --- |
| Oficinas (Retiro, San Nicolás) | Pico al mediodía, poco el fin de semana | Lo aprende en su patrón de llenado |
| Gastronómico (San Telmo) | Pico de noche, más viernes y sábado; la feria de los domingos | Ídem |
| Residencial (Constitución) | Pico de 18 a 21 | Ídem |
| Mixto (Monserrat, Puerto Madero) | Cada contenedor con uno de los perfiles de arriba | Ídem |
| Contenedor chico (bilateral 1.100 l) | Se llena antes | Lo prioriza antes por el pronóstico |
| Poco uso (6% de los contenedores) | Casi no se llena | Lo recolecta por la regla de 48 horas |
| Evento masivo | Un sector genera el doble durante 3 a 5 horas (~1 cada 2 días) | Pronostica riesgo y despacha antes |
| Sensor sin señal | Deja de mandar; se arregla entre 6 y 30 horas después | Lo detecta a las 2 h, estima el nivel, abre un incidente y lo cierra cuando vuelve |
| Sensor congelado | Repite el valor; se arregla después | Lo detecta a las 4 h, igual que arriba |
| Sensor trabado | Al vaciarse sigue marcando lo de antes (3% de las recolecciones) | Lo detecta con la recolección confirmada y estima desde el vaciado |
| Alguien revuelve | El sensor sube y baja de golpe 40 minutos | Alerta de llenado irregular |
| Contenedor lleno de verdad | Marca 98 a 100 fijo | No lo confunde con un sensor congelado |
| Ruido del sensor | Las lecturas fluctúan ±1 punto | Un contenedor quieto no parece congelado; el patrón no se infla |
| Desborde | El camión no llega a tiempo | Lo registra, abre un incidente y se cierra al vaciarlo |
| No se pudo recolectar | 6% de las paradas (auto estacionado, calle cortada…) | Incidente para el operador |
| Reporte vecinal | ~9 por día; 3 de cada 10 sobre contenedores que no están llenos | Prioridad por reporte; el operador puede descartarlo |

**Calibración:** la Ciudad genera unas 7.000 toneladas diarias de residuos con unos 27.500 contenedores de húmedos, unos 50 litros por hora por contenedor. El emulador usa entre 40 y 80 litros por hora en los laterales y entre 20 y 45 en los bilaterales, que están más cerca uno de otro. Con eso, en 3 días simulados hay unos 14 desbordes (alrededor de 4 por día).

**Casos a pedido para la demo:** `POST /api/v1/simulacion/casos`, por ejemplo:

```json
{ "tipo": "falla_sensor", "contenedorId": "MON-10", "falla": "congelado", "horas": 6 }
{ "tipo": "evento_masivo", "sectorId": 3, "horas": 4 }
{ "tipo": "llenar", "contenedorId": "STE-02", "nivel": 100 }
```

Tipos: `falla_sensor` (con `falla` = `sin_senal`, `congelado` o `trabado`), `reparar_sensor`, `revolver`, `llenar`, `evento_masivo` y `reporte_vecinal`.

### Qué hace hoy

Como todavía no hay sensores en vivo, el backend trae un **emulador**: cada 4 segundos reales pasan 5 minutos simulados, los contenedores se llenan, los sensores mandan su nivel y los camiones recorren sus rutas. Con esos datos el sistema hace lo mismo que haría con datos reales:

- guarda cada lectura y aprende cómo se llena cada contenedor: su promedio por hora del día y día de la semana;
- con ese patrón pronostica a qué hora va a desbordar cada uno;
- calcula qué contenedores son prioritarios (ver abajo);
- detecta lecturas irregulares y sensores sin señal o congelados, y les estima el nivel;
- despacha un camión del sector cuando ese sector tiene 3 o más contenedores prioritarios o uno desbordado. Elige primero los que se pronostica que desbordan antes, y los que desbordan en la próxima hora y media los visita primero aunque estén lejos;
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
| GET | `/sectores` | Los 4 sectores de la zona 1, con sus barrios, su camión y cuántos contenedores tiene |
| GET | `/contenedores` | Contenedores con dirección, barrio, tipo, llenado, nivel, motivo de prioridad, estado del sensor y hora estimada de desborde. Filtros: `zonaId`, `sectorId` |
| GET | `/contenedores/prioritarios` | Los que hay que ir a buscar, del más lleno al menos lleno. Filtro: `zonaId` |
| GET | `/contenedores/{id}` | Un contenedor |
| GET | `/contenedores/{id}/lecturas` | Lecturas del sensor. Parámetro: `horas` (24 por defecto) |
| GET | `/contenedores/{id}/curva` | Lo que dibuja Estado de sensores: las lecturas y, si el sensor falla, la curva del nivel estimado |
| POST | `/lecturas` | Entrada de los sensores reales. Usa el header `X-Api-Key` en vez de token |
| GET | `/camiones` | Camiones con chofer, estado, posición y las paradas de su ruta. Filtros: `zonaId`, `sectorId` |
| GET | `/camiones/{id}` | Un camión con su ruta más nueva |
| GET | `/rutas` | Rutas del día. Filtros: `zonaId`, `estado` |
| GET | `/rutas/{id}` | Una ruta con sus paradas |
| GET | `/rutas/{id}/trazado` | El recorrido por las calles, un tramo por cada tramo de la ruta (base, paradas, base), y los km por calle. La primera vez responde `calculando` y avisa por `/eventos` cuando está listo |
| POST | `/rutas/planificar` | Despacho manual: manda ahora el camión de un sector. Body: `sectorId` |
| GET | `/incidentes` | Incidentes. Filtros: `zonaId`, `sectorId`, `tipo`, `estado` |
| GET | `/incidentes/{id}` | Un incidente |
| POST | `/incidentes/{id}/decision` | El operador confirma o descarta. Body: `decision` |
| GET | `/reportes-vecinales` | Los incidentes que vienen de vecinos. Filtros: `zonaId`, `estado` |
| POST | `/reportes-vecinales` | Carga un reporte. Body: `contenedorId`, `comentario` |
| GET | `/panel/resumen` | Los números del Panel general: recolectados y planificados hoy, km evitados, prioritarios, desbordados, incidentes abiertos y pendientes, camiones por estado, sensores por estado, llenado por hora y hora pico. Filtros: `zonaId`, `sectorId` |
| GET | `/panel/sectores` | La tabla por sector del Panel general: avance de cada camión, prioritarios, desbordados, incidentes, llenado promedio, sensores con falla y eficiencia |
| GET | `/indicadores` | Solo el director. Km, desbordes y visitas de la ruta dinámica contra una ruta fija, eficiencia por sector, llenado por hora y mapa de calor. Filtros: `sectorId`, `dias` (30 por defecto) |
| GET | `/simulacion` | Hora del sistema y si la simulación está en pausa |
| POST | `/simulacion` | Pausar o acelerar la demo. Body: `enPausa`, `velocidad` (1 a 20) |
| POST | `/simulacion/reiniciar` | Solo el director. Vuelve la demo al punto de partida |
| POST | `/simulacion/casos` | Dispara un caso de la simulación para la demo (ver arriba) |
| GET | `/eventos` | Avisos en vivo (Server-Sent Events): evento `actualizacion` cada vez que algo cambió, como mucho uno por segundo |

**Avisos en vivo.** El panel no pregunta cada tantos segundos: abre `/eventos` una vez y el servidor le avisa cuando hay datos nuevos (después de cada paso del emulador y de cualquier pedido que cambie algo). El aviso solo trae la hora del sistema; al recibirlo, el panel vuelve a pedir lo que muestra. Varios cambios seguidos se juntan en un solo aviso por segundo, así a velocidad 20 el panel no pide todo cinco veces por segundo. Usa el mismo token que el resto, en el header `Authorization` (por eso el panel no usa `EventSource`, que no deja mandar headers). Para verlo desde la terminal: `curl -N http://localhost:8080/api/v1/eventos -H "Authorization: Bearer $TOKEN"`.

Para probarlos desde Swagger: llamar a `/auth/login`, copiar el `token` de la respuesta y pegarlo con el botón **Authorize**. Desde la terminal:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"operador","contrasena":"operador123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl http://localhost:8080/api/v1/panel/resumen -H "Authorization: Bearer $TOKEN"
```

### Indicadores

Cada día, cuando empieza el siguiente, el sistema guarda un resumen por sector en `resumen_diario`: km recorridos, contenedores vaciados, cuántos tenían menos de 50%, cuántos se vaciaron en el momento justo (50% o más y antes de desbordar), desbordes y llenado promedio por hora.

Para comparar, calcula con **las mismas lecturas del día** qué habría pasado con una ruta fija que vacía todos los contenedores a las 8, 14 y 20 (`RONDAS_RUTA_FIJA` en `Parametros.java`). Toma cuánto subió cada contenedor entre lecturas y lo vuelve a acumular con esas rondas.

La primera vez que arranca la demo se generan 8 semanas de resúmenes previos con la misma simulación, marcados como `simulado`, para que los gráficos no empiecen vacíos. La respuesta de `/indicadores` dice cuántos días del período son simulados.

**El resultado depende de contra qué ruta fija se compare.** En 8 semanas simuladas:

| | Fija: 3 rondas (8, 14, 20) | Fija: 1 ronda nocturna | Dinámica |
| --- | --- | --- | --- |
| Km recorridos | 8.128 | 2.709 | 5.288 |
| Desbordes | 0 | 641 | 17 |
| Vaciados con menos de 50% | 96% | 47% | 5% |

**Decisión (19/10):** se compara contra la ruta fija de 3 rondas. La ronda nocturna acumula demasiada basura durante el día y no representa cómo se trabaja de verdad; la comparación tiene que ser contra lo más real.

Los km de las dos rutas se calculan en línea recta entre paradas, para que la comparación sea pareja. Por las calles los recorridos son bastante más largos (en una ruta de ejemplo, 10,9 km contra 4,0 km en línea recta, por las manos de las calles), así que los km absolutos de los indicadores subestiman lo que se recorre; la proporción entre fija y dinámica es lo que vale.

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
| `OSRM_URL` | Servidor OSRM para las rutas por las calles. Vacía: rutas en línea recta | `https://router.project-osrm.org` (el público de demostración) |

**Rutas por las calles.** Se piden a OSRM (gratis, sin clave) la primera vez que alguien mira una ruta, no al crearla, y quedan guardadas en `ruta.trazado`. Así, con la simulación acelerada, no se piden cientos de rutas que nadie ve. Los pedidos salen de a uno, con un segundo entre cada uno, que es lo que pide el servidor público. Si OSRM no responde, el panel dibuja líneas rectas y se vuelve a intentar al minuto. Para la demo publicada conviene levantar un OSRM propio con el mapa de Argentina y apuntar `OSRM_URL` ahí.

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
  zonas/  sectores/  camiones/  incidentes/
                un paquete por módulo, cada uno con su entidad, repositorio, DTO y controlador
  contenedores/ además tiene el patrón de llenado aprendido, el pronóstico y la regla de prioridad
  sensores/     entrada de lecturas, detección de anomalías y estado de cada sensor
  rutas/        despacho a demanda, orden de paradas y avance de la ruta
  panel/        números del día para el Panel general
  indicadores/  resumen diario, comparación con la ruta fija y los indicadores
  emulador/     hace de sensores y choferes mientras no haya datos en vivo
  (próximos)    tráfico, aviso al chofer, indicadores
src/main/resources/
  db/migration/ migraciones de Flyway (V1 esquema, V2 datos de ejemplo, V3 zonas oficiales, V4 sensores y reloj, V5 patrón de llenado, V6 nivel inicial para reiniciar la demo, V7 contenedores reales de la zona 1, V8 fallas de sensor y desbordes, V9 segundo camión del sector 1, V10 indicadores)
scripts/        importar_contenedores.py arma V7 con los contenedores reales; los otros generaron V2 y V3
```

## Reglas para no pisarse

- **Nunca se edita una migración que ya está en `develop`.** Para cambiar una tabla se agrega una nueva: `V3__agregar_columna_x.sql`. Flyway compara cada archivo con lo que ya corrió y falla si alguno cambió.
- Si dos personas crean la misma versión (dos `V3`), la segunda en hacer merge renombra la suya a `V4`.
- La lógica de anomalías, sensores y orden de rutas es la misma que la de `src/logic/` del front. La prioridad parte de la del front y le suma el pronóstico con el patrón aprendido, el motivo `tiempo` y las paradas de paso, que el front todavía no conoce. Si se cambia una regla, hay que cambiarla en los dos lados hasta que el panel lea todo de la API. Los umbrales están en `comun/Parametros.java`.
- Para saber qué hora es, el código usa siempre `RelojDelSistema.ahora()`, nunca la hora de la máquina: así funciona igual con la hora simulada y con la real.
- Los DTO usan los mismos nombres de campo que `src/types/index.ts` del front, para que el panel pueda tipar las respuestas con lo que ya tiene.
- Los errores se responden con `{ "codigo", "mensaje" }`. Para un 404 se lanza `RecursoNoEncontradoException` y para un 409, `EstadoInvalidoException`. Lo demás lo resuelve `ManejadorDeErrores`.
