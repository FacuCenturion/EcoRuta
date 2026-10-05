# Guía de trabajo del equipo

## Flujo con Git

- `main`: versión estable, lo que se muestra en las entregas. No se trabaja directo acá.
- `develop`: donde se junta el trabajo del equipo.
- `feature/<nombre>`: una rama por tarea, siempre salida de `develop`.

Para cada tarea:

```bash
git checkout develop
git pull
git checkout -b feature/panel-incidentes
# ...se trabaja y se hacen commits chicos...
git push -u origin feature/panel-incidentes
```

Después se abre un Pull Request hacia `develop` y otra persona del equipo lo revisa antes de aprobarlo. Antes de empezar a trabajar siempre conviene hacer `git pull` para no pisarse con los cambios de los demás.

### Mensajes de commit

Un prefijo y una frase corta:

- `feat:` algo nuevo (`feat: filtro por tipo en incidentes`)
- `fix:` corrección de un error
- `docs:` cambios en documentación
- `test:` pruebas
- `chore:` configuración y mantenimiento

### Para evitar conflictos

- Cada integrante trabaja en una pantalla o módulo distinto a la vez.
- Los archivos compartidos (`types/index.ts`, `config.ts`, `styles/global.css`) se tocan con cambios chicos y se suben rápido.
- Antes de abrir un Pull Request: `npm test` y `npm run typecheck` tienen que pasar.

## Dónde tocar cada cosa

| Quiero...                                | Archivo                                  |
| ---------------------------------------- | ---------------------------------------- |
| Cambiar un umbral (80%, 95%, rondas)     | `src/config.ts`                          |
| Cambiar la regla de prioridad            | `src/logic/prioridad.ts`                 |
| Cambiar cómo se arman las rutas          | `src/logic/rutas.ts`                     |
| Cambiar cómo se llenan los contenedores  | `src/logic/llenado.ts`                   |
| Agregar zonas, calles o camiones         | `src/data/zonas.ts`, `src/data/semilla.ts` |
| Agregar un tipo de incidente             | `src/types/index.ts` y `src/simulator/simulador.ts` |
| Agregar una pantalla                     | `src/pages/` y la ruta en `src/App.tsx`  |
| Cambiar colores y estilos                | `src/styles/global.css`                  |
| Cambiar usuarios o permisos por rol      | `src/data/usuarios.ts`                   |
| Cambiar el mapa de Google                | `src/components/mapa/MapaGoogle.tsx`     |

La clave de Google Maps va en `.env.local` (cada integrante tiene la suya o comparten una por privado). No se sube nunca al repositorio.

## Pasar de datos simulados a un backend

Toda la información pasa por `SimulacionContext`. Cuando exista la API, ese archivo es el único que cambia: en lugar de llamar a `avanzar()` cada pocos segundos, se piden los datos al servidor. Las pantallas no se tocan.
