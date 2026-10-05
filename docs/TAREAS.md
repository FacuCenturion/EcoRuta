# Lista de tareas

Primera etapa: **solo la sección web para el gerente del área** (dashboards y datos de sensores, por ahora ficticios). La app del chofer queda para una etapa posterior.

Estado: [x] hecho, [ ] pendiente.

## Fase 1: Base del proyecto

- [x] Proyecto con React, TypeScript y Vite
- [x] Estructura de carpetas y convenciones del equipo
- [x] Pruebas automáticas de la lógica y de las pantallas
- [ ] Configurar GitHub Actions para correr las pruebas en cada Pull Request
- [ ] Proteger las ramas `main` y `develop` en GitHub

## Fase 2: Datos y simulación

- [x] Modelo de datos: zonas, contenedores, camiones, incidentes
- [x] Simulador de sensores con pico de llenado entre las 18 y las 21 h
- [x] Simulación de llenado irregular (persona revolviendo)
- [x] Historial simulado de 8 semanas para los indicadores
- [ ] Reemplazar las posiciones aproximadas por los datos abiertos del Gobierno de la Ciudad (zonas y contenedores)
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
- [ ] Estilo del mapa más claro, con las zonas dibujadas, como en el Figma
- [ ] Login real validado por el backend
- [ ] Probar en celular y en distintos navegadores

## Fase 5: Backend

- [ ] Elegir tecnología (opción: Spring Boot con PostgreSQL)
- [ ] API para recibir las lecturas de sensores
- [ ] API de contenedores, rutas, incidentes y reportes
- [ ] Guardar los datos en una base real
- [ ] Conectar `SimulacionContext` con la API

## Fase 6: App del chofer (etapa posterior, todavía no se desarrolla)

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
