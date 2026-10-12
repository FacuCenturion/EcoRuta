import L from 'leaflet';
import { useEffect } from 'react';
import {
  CircleMarker,
  MapContainer,
  Marker,
  Polygon,
  Polyline,
  Popup,
  TileLayer,
  Tooltip,
  useMap,
} from 'react-leaflet';
import { diaDeSemana } from '../../logic/llenado';
import { motivoDelContenedor, nivelVisual } from '../../logic/prioridad';
import { BORDE_ESTIMADO, BORDE_ZONA, COLOR_NIVEL, OPACIDAD_ZONA } from '../../utils/colores';
import InfoContenedor from './InfoContenedor';
import type { PropsMapa, Vista } from './tipos';

// Cuando cambia la vista (por ejemplo al filtrar por zona) movemos el mapa
function AjustarVista({ vista }: { vista: Vista }) {
  const mapa = useMap();
  useEffect(() => {
    mapa.setView([vista.lat, vista.lng], vista.zoom);
  }, [mapa, vista.lat, vista.lng, vista.zoom]);
  return null;
}

function iconoCamion(numero: number) {
  return L.divIcon({
    className: 'marca-camion',
    html: `<span>${numero}</span>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });
}

// Mapa alternativo con OpenStreetMap: se usa cuando no hay clave de Google Maps.
export default function MapaLeaflet({
  contenedores,
  camiones = [],
  relojMs,
  vista,
  seleccionadoId,
  recorridoHecho,
  recorridoPendiente,
  zonas = [],
  className = 'mapa',
}: PropsMapa) {
  const fecha = new Date(relojMs);
  const hora = fecha.getHours();
  const dia = diaDeSemana(fecha);

  return (
    <MapContainer className={className} center={[vista.lat, vista.lng]} zoom={vista.zoom} scrollWheelZoom>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <AjustarVista vista={vista} />

      {zonas.map((zona) => (
        <Polygon
          key={zona.id}
          // Cada parte va como un polígono aparte (si no, Leaflet la toma como un hueco)
          positions={zona.limites.map((parte) => [parte.map((p) => [p.lat, p.lng] as [number, number])])}
          pathOptions={{
            color: BORDE_ZONA,
            weight: 1.5,
            opacity: 0.8,
            fillColor: zona.color,
            fillOpacity: OPACIDAD_ZONA,
          }}
        >
          <Tooltip sticky>
            <strong>Zona {zona.id}</strong> · {zona.empresa}
          </Tooltip>
        </Polygon>
      ))}

      {recorridoHecho && recorridoHecho.length > 1 && (
        <Polyline positions={recorridoHecho.map((p) => [p.lat, p.lng])} pathOptions={{ color: '#7c8576', weight: 4 }} />
      )}
      {recorridoPendiente && recorridoPendiente.length > 1 && (
        <Polyline
          positions={recorridoPendiente.map((p) => [p.lat, p.lng])}
          pathOptions={{ color: '#2f6fd6', weight: 4 }}
        />
      )}

      {contenedores.map((contenedor) => {
        const nivel = nivelVisual(contenedor, hora, dia);
        const motivo = motivoDelContenedor(contenedor, hora, dia);
        const elegido = contenedor.id === seleccionadoId;
        const estimado = contenedor.nivelEstimado !== undefined;
        return (
          <CircleMarker
            key={contenedor.id}
            center={[contenedor.posicion.lat, contenedor.posicion.lng]}
            radius={elegido ? 11 : nivel === 'bajo' ? 5 : 7}
            pathOptions={{
              color: elegido ? '#2f6fd6' : estimado ? BORDE_ESTIMADO : nivel === 'desborde' ? '#d9a82b' : '#ffffff',
              weight: elegido ? 3 : estimado ? 2 : 1.5,
              dashArray: estimado && !elegido ? '3 3' : undefined,
              fillColor: COLOR_NIVEL[nivel],
              fillOpacity: estimado ? 0.6 : 0.95,
            }}
          >
            <Popup>
              <InfoContenedor contenedor={contenedor} nivel={nivel} motivo={motivo} />
            </Popup>
          </CircleMarker>
        );
      })}

      {camiones.map((camion) => (
        <Marker
          key={camion.id}
          position={[camion.posicion.lat, camion.posicion.lng]}
          icon={iconoCamion(camion.numero)}
          title={`Camión ${camion.numero}`}
        >
          <Popup>
            <strong>Camión {camion.numero}</strong>
            <br />
            Chofer: {camion.chofer}
            <br />
            Zona {camion.zonaId}
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  );
}
