# EcoRuta

Sistema web para visualizar el estado de contenedores inteligentes y apoyar la gestión de recolección de residuos en CABA. En lugar de recorridos fijos, el sistema prioriza los contenedores según su nivel de llenado y arma rutas dinámicas para los camiones.

Esta primera etapa es solo la **sección web para el gerente del área**. La app del chofer se desarrolla más adelante.

Proyecto del Seminario de Gestión de Tecnología (UADE, 2C 2026, Equipo 7).

## Qué incluye esta primera versión

Panel del Director de Higiene Urbana con las pantallas diseñadas en Figma:

- **Panel general:** resumen del día, mapa en vivo, incidentes para revisar, estado por zona y llenado promedio por hora.
- **Mapa en vivo:** todos los contenedores según su nivel, con la lista de prioritarios.
- **Rutas y camiones:** flota, ruta de cada camión con el orden de paradas y su avance.
- **Incidentes:** alertas del sistema, avisos de choferes y reportes de vecinos. El operador confirma o descarta.
- **Reportes vecinales:** reportes que llegan por Boti, con un formulario para simular uno.
- **Indicadores:** km evitados, CO₂ estimado, desbordes por semana, zonas calientes.

Los datos son **simulados en el frontend**: 126 contenedores en las 7 zonas, un camión por zona y un simulador de sensores que va actualizando los niveles de llenado. Los números de la pantalla de Indicadores salen del mismo modelo, así que son estimaciones y no mediciones reales.

## Stack

- React 18, TypeScript y Vite
- React Router para las pantallas
- Google Maps (con `@vis.gl/react-google-maps`) para el mapa, y Leaflet con OpenStreetMap como mapa alternativo si no hay clave
- Recharts para los gráficos
- Vitest para las pruebas

## Cómo ejecutar

Hace falta Node 18 o superior (recomendado 20 o 22).

```bash
npm install
npm run dev
```

Después abrir la dirección que muestra la terminal (normalmente http://localhost:5173). El mapa necesita conexión a internet para cargar las calles.

### Con el backend

Así como está, el panel simula los datos en el navegador. Para usar el backend (carpeta `backend/`, ver su README):

1. Levantar el backend: `cd backend && ./mvnw spring-boot:run`.
2. Crear `.env.local` (copiando `.env.example`) con `VITE_API_URL=http://localhost:8080/api/v1`.
3. Reiniciar `npm run dev` y entrar con los mismos usuarios de prueba.

Con backend, el panel muestra solo la zona 1 dividida en 4 sectores, con los 180 contenedores reales, y el filtro del encabezado pasa a ser por sector. Los datos se actualizan cuando el backend avisa que hubo cambios (Server-Sent Events), sin preguntar cada tantos segundos. Pausar y acelerar desde el encabezado controla la simulación del backend, que es compartida: si dos personas tienen el panel abierto, ven lo mismo.

### Usuarios de prueba

La página pide iniciar sesión. Hay dos usuarios de ejemplo:

| Rol | Usuario | Contraseña | Qué ve |
| --- | --- | --- | --- |
| Director | `director` | `director123` | Todas las pantallas, incluido Indicadores |
| Operador | `operador` | `operador123` | Todo menos Indicadores |

Los permisos se cambian en la tabla `PANTALLAS_POR_ROL` de `src/data/usuarios.ts`. Este login es **solo para la demo**: las contraseñas están en el código y la sesión vive en el navegador. Cuando exista el backend, la validación pasa al servidor.

### Activar Google Maps

Sin configurar nada, la página muestra un mapa alternativo (OpenStreetMap). Para usar Google Maps:

1. En [Google Cloud Console](https://console.cloud.google.com/) crear un proyecto y asociarle una cuenta de facturación (Google la pide aunque el uso quede dentro del cupo gratuito mensual).
2. Habilitar la API **Maps JavaScript API**.
3. Crear una clave en *Credenciales, Crear credenciales, Clave de API*.
4. Restringir la clave: en *Restricciones de aplicaciones* elegir *Sitios web* y agregar `http://localhost:5173/*` (y después la dirección de la demo publicada). En *Restricciones de API* elegir solo Maps JavaScript API.
5. Copiar el archivo `.env.example` con el nombre `.env.local` y pegar la clave: `VITE_GOOGLE_MAPS_API_KEY=la_clave`.
6. Reiniciar `npm run dev`.

`.env.local` no se sube a GitHub. La clave de Google Maps igual queda visible en el navegador, por eso es importante restringirla. Conviene también poner un límite de gasto o una alerta de presupuesto en la cuenta de facturación.

Otros comandos:

```bash
npm test          # pruebas de la lógica y de las pantallas
npm run typecheck # revisión de tipos
npm run build     # versión para producción
```

## Cómo funciona

1. **Simulador** (`src/simulator`): cada 4 segundos reales pasan 5 minutos simulados. Los contenedores se llenan según la hora del día (pico entre las 18 y las 21), los sensores informan su nivel y los camiones recolectan sus rutas. En el encabezado se puede pausar o acelerar.
2. **Priorización** (`src/logic/prioridad.ts`): un contenedor entra en la próxima ronda si supera el 80%, si va a pasar el 95% antes de la ronda siguiente (riesgo de desborde) o si tiene un reporte vecinal abierto.
3. **Rutas** (`src/logic/rutas.ts`): para cada camión se ordenan las paradas con el algoritmo del vecino más cercano, partiendo y volviendo a la base de la zona.
4. **Anomalías** (`src/logic/anomalias.ts`): si un sensor sube y baja de golpe (por ejemplo, alguien revolviendo la basura) se genera una alerta. No dispara ninguna acción: un operador la revisa y confirma.
5. **Zonas** (`src/data/zonas.ts`): cada zona tiene la empresa a cargo y su contorno, que se dibuja en los mapas. Los contornos salen de unir las comunas de cada zona según el mapa oficial del Servicio de Higiene Urbana:

   | Zona | Empresa | Comunas | Límites con las zonas vecinas |
   | --- | --- | --- | --- |
   | 1 | AESA Aseo y Ecología | 1 | Uruguay, Av. Córdoba, Av. Entre Ríos, Av. Caseros, Av. Brasil |
   | 2 | Cliba Ingeniería Urbana | 2, 13, 14 | Uruguay, Av. Córdoba, Av. Cabildo, Larralde, Zapiola, Monroe, Rómulo Naón, Av. Álvarez Thomas |
   | 3 | IMPSA Ambiental | 11, 12, 15 | Av. Cabildo a Av. Álvarez Thomas (con la 2), Av. Córdoba, Av. Ángel Gallardo, Av. San Martín, Av. Lope de Vega, Baigorria, Joaquín V. González |
   | 4 | Ecohabitat - UTE | 9, 10 | Av. Lope de Vega, Baigorria, Joaquín V. González, Portela, Av. Castañares, Av. Eva Perón, Av. Escalada |
   | 5 | Ente de Higiene Urbana | 8 | Av. Eva Perón, Av. Escalada, Av. Perito Moreno, Av. Varela |
   | 6 | Ashira Martín y Martín - UTE | 5, 6, 7 | Av. Ángel Gallardo, Av. San Martín, Portela, Av. Castañares, Av. Perito Moreno, Av. Riestra, Av. Caseros, Sánchez de Loria |
   | 7 | Transportes Olivos - Urbaser - UTE | 3, 4 | Av. Córdoba, Sánchez de Loria, Av. Caseros, Av. Riestra, Av. Varela, Av. Entre Ríos, Av. Brasil |

   Para regenerar los contornos: `pip install shapely` y `python scripts/generar_limites_zonas.py`.
6. **Estado compartido** (`src/context/SimulacionContext.tsx`): todas las pantallas leen los datos de acá. Sin `VITE_API_URL` los arma el simulador del navegador; con `VITE_API_URL` los pide al backend (`src/api/`) y las pantallas no notan la diferencia.

## Estructura

```
src/
  api/          cliente de la API del backend, avisos en vivo y conversión de sus respuestas
  components/   piezas que se reutilizan (mapa, tarjetas, menú); mapa/ tiene Google Maps y el mapa alternativo
  context/      estado del sistema que leen las pantallas
  data/         zonas (con sus límites en limites-zonas.json), contenedores y camiones de ejemplo
  logic/        priorización, rutas, anomalías, indicadores e historial
  pages/        una carpeta de código por pantalla
  simulator/    simulador de sensores y camiones
  styles/       estilos globales
  utils/        funciones auxiliares (formatos, números aleatorios, colores)
docs/           guía del equipo y lista de tareas
scripts/        generar_limites_zonas.py, arma limites-zonas.json a partir de los barrios del GCBA
```

## Limitaciones conocidas

- Sin backend, las posiciones de los contenedores, las bases y las calles son de ejemplo, y al recargar la página la simulación vuelve a empezar. Con backend, los contenedores son los reales de la zona 1.
- Las distancias se calculan en línea recta y las rutas se dibujan con líneas rectas entre paradas; en la calle serían algo más largas.
- Sin backend, el login es de prueba (ver arriba): las contraseñas están en el código.
- No está la app del chofer (etapa posterior) ni la conexión real con Boti. En el simulador los camiones recolectan solos para que el panel tenga movimiento.
- En "Rutas y camiones" los botones "Reasignar paradas" y "Contactar chofer" están deshabilitados: son del Release 2.

Ver `docs/TAREAS.md` para lo que sigue.

## Equipo

- Bentivegna Valentina
- Centurion Facundo
- Villamil Segundo
- Zuchowicki Julian
