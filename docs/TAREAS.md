# Lista de tareas

Estado: [x] hecho, [ ] pendiente.

## Estado del trabajo (19/10/2026)

El backend del panel está completo y el panel ya puede funcionar con él. Sin la variable `VITE_API_URL` el panel sigue con los datos simulados en el navegador, como se mostró en las Sprints 1 y 2, así `main` no cambia para quien no levante el backend.

| Parte | Estado |
| --- | --- |
| Panel web del gerente (7 pantallas, mapas con las zonas oficiales) | Hecho, en `main` |
| Backend del panel: login, sensores, prioridad, rutas a demanda, incidentes, indicadores | Hecho, en `main` |
| Solo la zona 1: 180 contenedores reales de la planilla del GCBA, en 4 sectores con 5 camiones | Hecho, en `main` |
| Simulación de sensores con todos los casos (fallas, trabado, eventos, reportes) | Hecho, en `main` |
| Panel conectado al backend, con avisos en vivo (SSE) en lugar de preguntar cada tantos segundos | Hecho, rama `feature/conectar-panel` |
| Rutas que siguen las calles (OSRM, sin Google) | Hecho, rama `feature/conectar-panel` |
| App del chofer (web que se instala en el celular, carpeta `chofer/`) | Versión funcional, rama `feature/app-chofer`; falta el diseño final |
| Demo publicada y cierre | Sin empezar |

Decisiones tomadas:

- La Sprint 3 es la última: lo que estaba en las Sprints 4 y 5 se hace ahora.
- Sin clave de Google por ahora (para evitar gastos inesperados): mapa alternativo y rutas con OSRM.
- Sin Firebase: el backend avisa por Server-Sent Events, al panel y después a la app del chofer.
- Los indicadores se comparan contra la ruta fija de 3 rondas por día (8, 14 y 20): es lo más parecido a cómo se trabaja hoy. La ronda nocturna acumula demasiado y no es real.

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
- [x] Contenedores sobre la calle: 180 contenedores de la zona 1 tomados de la planilla oficial del GCBA y ubicados con el normalizador de direcciones de la Ciudad (USIG). Migración `V7` del backend; con datos simulados en el navegador siguen las posiciones de ejemplo
- [ ] Si la Dirección General nos pasa lecturas históricas, importarlas y comparar con el simulador

## Fase 3: Lógica del sistema

- [x] Priorización: más del 80%, riesgo de desborde y reportes vecinales
- [x] Rutas por zona con el vecino más cercano
- [x] Detección de anomalías en el sensor
- [x] Incidentes con confirmación del operador
- [x] Rutas por las calles: el recorrido se pide a OSRM (gratis, sin clave), se guarda en la ruta y se dibuja en Rutas y camiones. Si OSRM no responde, se dibujan líneas rectas
- [x] Sumar a la ruta en curso un contenedor que se vuelve urgente (se le propone al chofer con la app)
- [x] Detección de sensores con falla usando solo el nivel de llenado: sin señal, valor congelado y lecturas irregulares
- [x] Nivel estimado para los contenedores con el sensor caído o trabado (se usa para priorizar)
- [x] Detectar el sensor que no baja después de una recolección confirmada (estado `trabado`, en el backend)

## Fase 4: Pantallas del panel web

- [x] Panel general
- [x] Mapa en vivo
- [x] Rutas y camiones
- [x] Incidentes
- [x] Reportes vecinales
- [x] Indicadores
- [x] Estado de sensores: resumen, lista por estado y curva de las últimas 24 h de cada sensor
- [x] Ajustar las pantallas al diseño de Figma (Panel general, Indicadores, Rutas y camiones)
- [ ] Revisar con el equipo los detalles visuales que falten, ya con la página abierta en el navegador
- [x] Login simple con roles Director y Operador (usuarios de prueba, sin backend)
- [x] Mapa con Google Maps (con mapa alternativo si no hay clave)
- [ ] Crear la clave de Google Maps del equipo, restringirla y cargarla en cada `.env.local`
- [ ] Crear un Map ID propio en Google Cloud y reemplazar `DEMO_MAP_ID` en `src/components/mapa/MapaGoogle.tsx`
- [x] Zonas dibujadas en el mapa con su color (en Google Maps y en el mapa alternativo)
- [x] Contenedores con nivel estimado marcados con borde punteado en los mapas
- [ ] Estilo del mapa más claro, como en el Figma
- [x] Login real validado por el backend (con `VITE_API_URL`)
- [ ] Probar en celular y en distintos navegadores

## Fase 5: Backend

- [x] Elegir tecnología: Spring Boot con PostgreSQL, en la carpeta `backend/`
- [x] Esqueleto del backend: migraciones con las 10 tablas, datos de ejemplo, Swagger y formato de error
- [x] Migración `V3` con las zonas oficiales: empresas, comunas, color y ubicaciones nuevas de contenedores y camiones
- [x] Sumar al modelo el estado del sensor y el nivel estimado

Sprint 3, un bloque por integrante:

- [x] Bloque 1, login y datos (backend): login con JWT y roles; endpoints para leer zonas, contenedores, camiones e incidentes
- [x] Bloque 1, login y datos (front): el login del panel pasa a la API. Las contraseñas de `src/data/usuarios.ts` quedan solo para el modo sin backend
- [x] Bloque 2, sensores y prioridad: recibir lecturas, detectar anomalías y fallas de sensor, calcular la prioridad en el servidor
- [x] Bloque 3, rutas a demanda: despacho automático y manual, con el orden de paradas por vecino más cercano
- [x] Pronóstico de desborde con el patrón de llenado aprendido de cada contenedor (promedio por hora y día de la semana)
- [x] Garantía de servicio: tiempo máximo de 48 horas sin recolectar y paradas de paso
- [x] En el front: mostrar el motivo `tiempo` y los estados nuevos (sensor trabado, falla de sensor, desborde)
- [x] En el front: mostrar la hora estimada de desborde y marcar las paradas de paso
- [x] Despacho manual desde Rutas y camiones (botón "Despachar ahora")
- [x] En el front: adaptar las respuestas de la API a los tipos del panel (`src/api/adaptadores.ts`). `src/logic/` se queda para el modo sin backend; con backend se usa lo que ya calculó el servidor
- [x] Bloque 4, incidentes y emulador (backend): confirmar y descartar incidentes, reportes vecinales, cierre automático; emulador de sensores y choferes dentro del backend
- [x] Bloque 4 (front): conectar `SimulacionContext` con la API, para que el panel deje de simular
- [x] Avisos en vivo del backend al panel con Server-Sent Events (`GET /api/v1/eventos`): el panel pide los datos solo cuando algo cambió


Lo que falta de la Sprint 3 (antes estaba en las Sprints 4 y 5):

- [ ] Hora de salida con menos tráfico: con una tabla simulada por hora (sin Google por ahora)
- [x] Aviso al chofer cuando se le asigna una ruta, por el mismo canal de eventos (sin Firebase)
- [x] Endpoints de la app del chofer: ver la ruta, iniciarla, confirmar cada parada y reportar "no se pudo"
- [ ] Aviso al operador si el chofer no inicia la ruta a los 10 minutos
- [x] Indicadores calculados desde la base (resumen diario y ruta fija calculada con las mismas lecturas)
- [x] Decidir la ruta fija de referencia de los indicadores: 3 rondas por día (la más real)
- [ ] Levantar un OSRM propio para la demo publicada (el público de demostración es para pruebas)

## Fase 6: App del chofer (se divide en mini sprints)

- [x] Elegir la tecnología: una web que se instala en el celular (PWA), en la carpeta `chofer/`
- [x] Mini sprint A, backend: turno con la app, iniciar la ruta, vaciado y "no se pudo"; el emulador deja de manejar ese camión
- [x] Mini sprint B, pantallas: login, empezar turno, ruta nueva, parada actual con "Cómo llegar", mapa por las calles y lista de paradas
- [x] Mini sprint C, aviso de ruta nueva con la app abierta o en segundo plano (vibra y notifica); se puede instalar en el celular
- [x] Ajustar las pantallas al diseño del equipo: navegación giro a giro, llegada, deshacer, parada nueva prioritaria, foto y nota en el no se pudo, resumen de la ruta
- [ ] Notificaciones con la app cerrada (Web Push)
- [ ] Mini sprint D: aviso al operador si el chofer no inicia la ruta a los 10 minutos
- [ ] Probarla en celulares reales (Android e iPhone) con la señal de la calle

## Fase 7: Reportes vecinales reales

- [ ] Conexión con el bot de la Ciudad (Boti de WhatsApp)
- [ ] Recepción de ubicación y foto del vecino

## Fase 8: Cierre

- [ ] Publicar una demo en línea
- [ ] Guion y datos de ejemplo para la presentación
- [ ] Revisión final del código y del README
