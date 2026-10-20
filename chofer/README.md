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

1. **Empezar turno.** Mientras el chofer no empieza el turno, el emulador del backend maneja su camión como siempre. Cuando lo empieza, el sistema le sigue asignando rutas, pero iniciarlas y cerrar cada parada pasa a ser cosa del chofer.
2. **Ruta nueva.** El backend avisa por Server-Sent Events cuando algo cambia, igual que en el panel; la app vuelve a pedir el turno. Si aparece una ruta que el chofer no había visto, se marca como nueva y el celular vibra. Si la app está en segundo plano y el chofer dio permiso, sale una notificación.
3. **En ruta.** Arriba, la próxima parada con tres botones: "Cómo llegar" (abre la navegación del celular, sin clave de Google), "Vaciado" y "No se pudo" (pide el motivo). Abajo, el mapa con el recorrido por las calles y la lista de paradas.
4. **Al vaciar**, el contenedor queda registrado como recolectado y el emulador lo vacía en el "mundo simulado", así el sensor marca el nivel nuevo. Al marcar "no se pudo", el Centro de Monitoreo recibe un incidente con el motivo.
5. **Al cerrar la última parada** la ruta termina y el camión vuelve a la base a descargar.

**Para la demo:** abrir la app como `chofer22` y empezar el turno; después, desde el panel, el director reinicia la demo. Los turnos abiertos siguen abiertos, así que la primera ruta del camión 22 le llega al chofer como "Ruta nueva". También sirve "Despachar ahora" en Rutas y camiones: si en el sector hay un chofer con el turno abierto, la ruta es para él.

## Pendiente

- Diseño final de las pantallas (hoy es una versión funcional con la paleta del panel).
- Notificaciones con la app cerrada del todo: hacen falta notificaciones push (Web Push); hoy el aviso llega mientras la app está abierta o en segundo plano.
- Aviso al operador si el chofer no inicia la ruta a los 10 minutos.

## Estructura

```
src/
  api.ts            cliente de la API y avisos en vivo (mismo esquema que el panel)
  tipos.ts          lo que devuelve /api/v1/chofer
  pantallas/        Login y Turno (la pantalla principal)
  componentes/      mapa de la ruta y hoja de "no se pudo"
public/
  manifest.webmanifest, sw.js, íconos   lo que hace falta para instalarla
```

Pruebas: `npm test` (con un backend falso).
