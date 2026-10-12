# Lista de tareas

Estado: [x] hecho, [ ] pendiente.

## Estado del trabajo (12/10/2026)

El panel web del gerente está completo y funciona con datos simulados. El backend tiene la base armada (tablas, datos de ejemplo, Swagger), pero todavía no tiene endpoints: el panel no lo usa. Lo que sigue es conectarlos (Sprint 3).

| Parte | Estado |
| --- | --- |
| Panel web del gerente (6 pantallas, mapas con las zonas oficiales) | Hecho, en `main` |
| Pantalla de estado de sensores y detección de fallas | Hecho, en revisión en la rama `feature/estado-sensores` |
| Backend: proyecto, base de datos y datos de ejemplo | Hecho, en `main` |
| Backend: login, endpoints y lógica en el servidor | Sin empezar (Sprint 3) |
| Panel conectado al backend | Sin empezar (Sprint 3) |
| Despacho a demanda con tráfico y aviso al chofer | Sin empezar (Sprint 4) |
| App del chofer | Sin empezar (Sprint 4, versión mínima) |

Para tener en cuenta:

- Los datos de zonas que carga el backend quedaron viejos: se generaron antes de pasar a las zonas oficiales (empresas, comunas y ubicaciones). Se corrige con una migración nueva, ver Fase 5.
- La rama `develop` quedó atrás de `main`: el backend y los cambios de mapas entraron directo a `main`. Hay que decidir si se sigue usando `develop` (y actualizarla) o si se trabaja con ramas que salen de `main`.

Plan por sprint:

| Sprint | Qué deja | Estado |
| --- | --- | --- |
| 1 | Panel web con datos simulados | Terminada |
| 2 | Base del backend, zonas oficiales en el mapa y estado de sensores | Se presenta el 13/10 |
| 3 | El panel funciona con la API y la base de datos (parte central) | Próxima |
| 4 | Tráfico con Google, aviso al chofer y app del chofer mínima | Pendiente |
| 5 | Indicadores desde la base, demo publicada y cierre | Pendiente |

## Fase 1: Base del proyecto

- [x] Proyecto con React, TypeScript y Vite
- [x] Estructura de carpetas y convenciones del equipo
- [x] Pruebas automáticas de la lógica y de las pantallas
- [ ] Configurar GitHub Actions para correr las pruebas en cada Pull Request
- [ ] Proteger las ramas `main` y `develop` en GitHub
- [x] Crear la rama `develop`
- [ ] Decidir si se sigue con `develop` (hoy está atrás de `main`) y dejarlo escrito en `GUIA_EQUIPO.md`

## Fase 2: Datos y simulación

- [x] Modelo de datos: zonas, contenedores, camiones, incidentes
- [x] Simulador de sensores con pico de llenado entre las 18 y las 21 h
- [x] Simulación de llenado irregular (persona revolviendo)
- [x] Historial simulado de 8 semanas para los indicadores
- [x] Límites reales de las 7 zonas (unión de comunas, con los barrios de los datos abiertos del GCBA)
- [ ] Reemplazar las posiciones de ejemplo de los contenedores por las del dataset de contenedores de la Ciudad
- [ ] Si la Dirección General nos pasa lecturas históricas, importarlas y comparar con el simulador

## Fase 3: Lógica del sistema

- [x] Priorización: más del 80%, riesgo de desborde y reportes vecinales
- [x] Rutas por zona con el vecino más cercano
- [x] Detección de anomalías en el sensor
- [x] Incidentes con confirmación del operador
- [ ] Usar la API de rutas de Google Maps en lugar de la distancia en línea recta
- [ ] Recalcular la ruta automáticamente cuando aparece un contenedor prioritario nuevo (Release 2)
- [ ] Detección de sensores con falla (Release 2)

## Fase 4: Pantallas del panel web

- [x] Panel general
- [x] Mapa en vivo
- [x] Rutas y camiones
- [x] Incidentes
- [x] Reportes vecinales
- [x] Indicadores
- [x] Ajustar las pantallas al diseño de Figma (Panel general, Indicadores, Rutas y camiones)
- [ ] Revisar con el equipo los detalles visuales que falten, ya con la página abierta en el navegador
- [x] Login simple con roles Director y Operador (usuarios de prueba, sin backend)
- [x] Mapa con Google Maps (con mapa alternativo si no hay clave)
- [ ] Crear la clave de Google Maps del equipo, restringirla y cargarla en cada `.env.local`
- [ ] Crear un Map ID propio en Google Cloud y reemplazar `DEMO_MAP_ID` en `src/components/mapa/MapaGoogle.tsx`
- [x] Zonas dibujadas en el mapa con su color (en Google Maps y en el mapa alternativo)
- [ ] Estilo del mapa más claro, como en el Figma
- [ ] Login real validado por el backend
- [ ] Probar en celular y en distintos navegadores

## Fase 5: Backend

- [x] Elegir tecnología: Spring Boot con PostgreSQL, en la carpeta `backend/`
- [x] Esqueleto del backend: migraciones con las 10 tablas, datos de ejemplo, Swagger y formato de error
- [ ] Migración nueva (`V3`) con las zonas oficiales: empresas, comunas, color y ubicaciones; volver a generar los contenedores y camiones con `backend/scripts/exportar-semilla.ts`
- [ ] Sumar al modelo el estado del sensor y el nivel estimado (cuando entre `feature/estado-sensores`)

Sprint 3, un bloque por integrante:

- [ ] Bloque 1, login y datos: login con JWT y roles; endpoints para leer zonas, contenedores, camiones e incidentes; el login del front pasa a la API
- [ ] Bloque 2, sensores y prioridad: recibir lecturas, detectar anomalías y fallas de sensor, calcular la prioridad en el servidor
- [ ] Bloque 3, rutas a demanda: despacho automático y manual, con el orden de paradas por vecino más cercano
- [ ] Bloque 4, incidentes y emulador: confirmar y descartar incidentes, reportes vecinales; el simulador pasa a mandar datos a la API; conectar `SimulacionContext` con la API

Sprint 4:

- [ ] Elegir la hora de salida con menos tráfico (Google Routes API, con una tabla simulada si no hay clave)
- [ ] Notificación push al chofer cuando se le asigna una ruta (Firebase)
- [ ] Endpoints de la app del chofer: registrar el celular, ver la ruta e iniciarla
- [ ] Aviso al operador si el chofer no inicia la ruta a los 10 minutos

Sprint 5:

- [ ] Indicadores calculados desde la base

## Fase 6: App del chofer (versión mínima en la Sprint 4)

- [ ] Elegir la tecnología: React Native, Flutter o una web que se instala en el celular
- [ ] Recibir la notificación de ruta nueva y abrir la ruta
- [ ] Pantalla "Tu ruta de hoy" con las paradas ordenadas
- [ ] Navegación y confirmación de cada contenedor recolectado
- [ ] Reporte "No se pudo recolectar" con motivo
- [ ] Aviso de nueva parada prioritaria

## Fase 7: Reportes vecinales reales

- [ ] Conexión con el bot de la Ciudad (Boti de WhatsApp)
- [ ] Recepción de ubicación y foto del vecino

## Fase 8: Cierre

- [ ] Publicar una demo en línea
- [ ] Guion y datos de ejemplo para la presentación
- [ ] Revisión final del código y del README
