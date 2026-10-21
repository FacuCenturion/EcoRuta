import L from 'leaflet';
import { useEffect, useMemo } from 'react';
import { AttributionControl, MapContainer, Marker, Polyline, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import { esPrioritaria, type Tramo } from '../navegacion';
import type { Coordenada, Parada, Propuesta } from '../tipos';
import type { Posicion } from '../usePosicion';

export type EstiloMapa = 'oscuro' | 'claro';

// Mapas de OpenStreetMap: gratis y sin clave, los mismos que usa el panel. El modo oscuro es el
// mismo mapa invertido con un filtro (ver estilos.css), así no depende de otro servicio.
const CAPA = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATRIBUCION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';

interface Props {
  tramos: Tramo[];
  paradas: Parada[];
  cerradas: number; // cuántas paradas ya se cerraron (incluye las que se están por mandar)
  base: Coordenada;
  posicion?: Posicion; // vacía en el inicio de jornada: se muestra la ruta entera
  seguir: boolean;
  alSoltar: () => void; // la persona movió el mapa: deja de seguir al camión
  estilo: EstiloMapa;
  propuesta?: Propuesta | null;
  posicionPropuesta?: Coordenada | null;
  verTodo: number; // cambia cada vez que hay que encuadrar la ruta completa
  margenAbajo?: number; // lo que tapa la hoja inferior, para que la ruta encuadrada quede a la vista
}

function insignia(clase: string, contenido: string, numero?: string) {
  return L.divIcon({
    className: '',
    html: `<span class="marca ${clase}">${contenido}${numero ? `<b>${numero}</b>` : ''}</span>`,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
  });
}

const PAPELERA =
  '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16"/><path d="M10 4h4"/><path d="M6 7l1 13h10l1-13"/></svg>';
const CHECK =
  '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';
const CASA =
  '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 11l8-7 8 7"/><path d="M6 10v10h12V10"/></svg>';

interface PropsCamara {
  posicion?: Posicion;
  seguir: boolean;
  puntos: Coordenada[];
  verTodo: number;
  margenAbajo: number;
}

function Camara({ posicion, seguir, puntos, verTodo, margenAbajo }: PropsCamara) {
  const mapa = useMap();
  useEffect(() => {
    if (puntos.length > 1) {
      mapa.fitBounds(L.latLngBounds(puntos.map((p) => [p.lat, p.lng])), {
        paddingTopLeft: [30, 30],
        paddingBottomRight: [30, margenAbajo + 20],
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [verTodo]);
  // Siguiendo al camión, queda en el medio de lo que se ve (no detrás de la hoja)
  useEffect(() => {
    if (!posicion || !seguir) return;
    const zoom = Math.max(mapa.getZoom(), 17);
    const centro = mapa.project([posicion.punto.lat, posicion.punto.lng], zoom).add([0, margenAbajo / 2]);
    mapa.setView(mapa.unproject(centro, zoom), zoom, { animate: true });
  }, [mapa, posicion, seguir, margenAbajo]);
  return null;
}

function AlMover({ alSoltar }: { alSoltar: () => void }) {
  useMapEvents({ dragstart: alSoltar });
  return null;
}

export default function Mapa(props: Props) {
  const { tramos, paradas, cerradas, base, posicion, estilo, propuesta, posicionPropuesta } = props;
  const todos = useMemo(() => tramos.flatMap((t) => t.puntos), [tramos]);
  const hecho = tramos.slice(0, cerradas).flatMap((t) => t.puntos);
  const pendiente = tramos.slice(cerradas).flatMap((t) => t.puntos);
  const proxima = paradas.find((p, i) => i >= cerradas && p.estado === 'pendiente');
  const flecha = L.divIcon({
    className: '',
    html: `<span class="flecha" style="transform: rotate(${posicion?.rumbo ?? 0}deg)"><svg width="30" height="34" viewBox="0 0 30 34"><path d="M15 2l12 30-12-7-12 7z" fill="#659df6" stroke="#fff9f3" stroke-width="2" stroke-linejoin="round"/></svg></span>`,
    iconSize: [64, 64],
    iconAnchor: [32, 32],
  });

  return (
    <MapContainer
      className={`mapa mapa--${estilo}`}
      center={[base.lat, base.lng]}
      zoom={15}
      zoomControl={false}
      attributionControl={false}
    >
      <AttributionControl position="topleft" prefix={false} />
      <TileLayer url={CAPA} attribution={ATRIBUCION} maxZoom={19} />
      <Camara
        posicion={posicion}
        seguir={props.seguir}
        puntos={todos}
        verTodo={props.verTodo}
        margenAbajo={props.margenAbajo ?? 0}
      />
      <AlMover alSoltar={props.alSoltar} />
      {hecho.length > 1 && (
        <Polyline
          positions={hecho.map((p) => [p.lat, p.lng])}
          pathOptions={{ color: '#7d8679', weight: 6, opacity: 0.7 }}
        />
      )}
      {pendiente.length > 1 && (
        <>
          <Polyline
            positions={pendiente.map((p) => [p.lat, p.lng])}
            pathOptions={{ color: '#1f3f7a', weight: 10, opacity: 0.6 }}
          />
          <Polyline positions={pendiente.map((p) => [p.lat, p.lng])} pathOptions={{ color: '#659df6', weight: 6 }} />
        </>
      )}
      <Marker position={[base.lat, base.lng]} icon={insignia('marca--base', CASA)} title="Base" />
      {paradas.map((p, i) => {
        const hecha = i < cerradas || p.estado !== 'pendiente';
        const clase = hecha
          ? p.estado === 'no_se_pudo'
            ? 'marca--problema'
            : 'marca--hecha'
          : `${esPrioritaria(p) ? 'marca--prioritaria' : 'marca--comun'}${p === proxima ? ' marca--proxima' : ''}`;
        const contenido = hecha ? (p.estado === 'no_se_pudo' ? '!' : CHECK) : posicion ? PAPELERA : String(i + 1);
        return (
          <Marker
            key={p.id}
            position={[p.posicion.lat, p.posicion.lng]}
            icon={insignia(
              `${clase}${posicion ? ' marca--cuadrada' : ''}`,
              contenido,
              posicion && !hecha ? String(i + 1) : undefined,
            )}
            title={p.direccion}
            zIndexOffset={p === proxima ? 500 : 0}
          />
        );
      })}
      {propuesta && posicionPropuesta && (
        <Marker
          position={[posicionPropuesta.lat, posicionPropuesta.lng]}
          icon={insignia('marca--prioritaria marca--cuadrada marca--proxima', PAPELERA, '+')}
          title={propuesta.direccion}
          zIndexOffset={800}
        />
      )}
      {posicion && (
        <Marker
          position={[posicion.punto.lat, posicion.punto.lng]}
          icon={flecha}
          zIndexOffset={1000}
          interactive={false}
        />
      )}
    </MapContainer>
  );
}
