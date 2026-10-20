# EcoRuta: app del chofer

La app que usa el chofer en el celular durante el turno: ve la ruta que le asignó el sistema, la inicia y cierra cada parada (vaciado o "no se pudo", con el motivo). Es una página web que se instala en el celular (PWA), separada del panel del Centro de Monitoreo.

Necesita el backend (carpeta `backend/`): no tiene datos simulados propios.

## Cómo ejecutar

```bash
cd backend && ./mvnw spring-boot:run      # en una terminal
cd chofer && npm install && npm run dev   # en otra
```

Abrir http://localhost:5174 (el panel usa el 5173). Por defecto llama a la API en `http://localhost:8080/api/v1`; para otra dirección, copiar `.env.example` como `.env.local` y cambiar `VITE_API_URL`.

Usuarios de prueba: `chofer22`, `chofer31`, `chofer14`, `chofer17` y `chofer40`, todos con la contraseña `chofer123`. Cada uno ve solo su camión. Los del panel (director, operador) no pueden entrar.

Para probarla en el celular, en la misma red Wi-Fi: `npm run dev -- --host`, abrir la dirección que muestra la terminal y agregarla a `CORS_ORIGENES` del backend.

## Cómo funciona

Sigue el diseño del equipo (siete pantallas: inicio de jornada, navegación, llegada, recolectado, parada nueva, no se pudo y ruta terminada).

1. **Empezar turno.** Mientras el chofer no empieza el turno, el emulador del backend maneja su camión. Cuando lo empieza, el sistema le sigue asignando rutas, pero iniciarlas y cerrar cada parada pasa a ser cosa del chofer.
2. **Inicio de jornada.** La ruta completa en el mapa, los km por las calles, el fin estimado y las primeras paradas con su llenado. Las prioritarias (pasaron el 80%, por desbordar o con reclamo) van en ámbar.
3. **Navegación.** Indicaciones giro a giro con las maniobras de OSRM ("80 m, Lima"), la calle por la que va, la velocidad y la máxima (40 en calles, 60 en avenidas, que es la regla de la Ciudad; no hay un dato por cuadra). Las indicaciones se dicen en voz alta con la voz del celular; el botón del parlante las silencia.
4. **Llegada.** A menos de 25 m del contenedor: su llenado, prioridad y la última vez que se vació, con "Marcar como recolectado" y "No se pudo recolectar".
5. **Recolectado.** Se puede deshacer durante 5 segundos; recién ahí se manda al backend (si la app pasa a segundo plano, se manda enseguida).
6. **No se pudo.** El motivo, una nota (escrita o dictada, donde el celular lo permita) y una foto opcional, achicada antes de mandarla. El Centro de Monitoreo recibe el incidente y puede ver la foto desde el panel.
7. **Parada nueva prioritaria.** Si un contenedor del sector se vuelve urgente, el sistema propone sumarlo donde menos desvío agrega. Se acepta solo a los 8 segundos si el chofer no contesta.
8. **Ruta terminada.** Duración, recorrido e incidentes; "Volver a la base" navega hasta la base.

**De dónde sale la posición.** En el botón de la lista se elige: *simulada* (para la demo, el camión avanza solo por el recorrido y se detiene en cada parada; se puede acelerar x4) o *GPS del celular* (para usarla manejando). La app manda la posición al backend cada 5 segundos, así el panel ve dónde está el camión.

**El mapa** es el de OpenStreetMap, sin clave. El modo oscuro es el mismo mapa invertido con un filtro; el botón de capas lo cambia al mapa claro.

**Para la demo:** abrir la app como `chofer22` y empezar el turno; después, desde el panel, el director reinicia la demo. Los turnos abiertos siguen abiertos, así que la primera ruta del camión 22 le llega al chofer. Para mostrar una parada nueva, en Swagger: `POST /api/v1/simulacion/casos` con `{"tipo":"llenar","contenedorId":"<uno del sector 1 que no esté en la ruta>","nivel":97}` y dejar correr la simulación unos segundos.

## Pendiente

- Notificaciones con la app cerrada del todo: hacen falta notificaciones push (Web Push); hoy el aviso llega con la app abierta o en segundo plano.
- Aviso al operador si el chofer no inicia la ruta a los 10 minutos.
- Probarla en celulares reales (Android e iPhone) y con GPS en la calle.
- El mapa de OpenStreetMap pide no abusar de sus servidores: para muchos choferes a la vez conviene un proveedor de mapas propio.

## Estructura

```
src/
  api.ts            cliente de la API y avisos en vivo (mismo esquema que el panel)
  tipos.ts          lo que devuelve /api/v1/chofer
  pantallas/        Login y Turno (la pantalla principal: decide qué se ve en cada momento)
  componentes/      mapa, íconos y la hoja de "no se pudo"
  navegacion.ts     tramos, próxima maniobra, calle actual, distancias y velocidad máxima
  usePosicion.ts    la posición del camión: GPS del celular o simulada para la demo
  voz.ts, dictado.ts, foto.ts   indicaciones en voz alta, nota dictada y foto achicada
public/
  manifest.webmanifest, sw.js, íconos   lo que hace falta para instalarla
```

Pruebas: `npm test` (con un backend falso).
