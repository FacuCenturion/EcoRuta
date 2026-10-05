import {
  AdvancedMarker,
  AdvancedMarkerAnchorPoint,
  APIProvider,
  InfoWindow,
  Map as MapaDeGoogle,
  Polyline,
  useMap,
} from '@vis.gl/react-google-maps';
import { useEffect, useState } from 'react';
import { diaDeSemana } from '../../logic/llenado';
import { motivoDelContenedor, nivelVisual } from '../../logic/prioridad';
import { COLOR_NIVEL } from '../../utils/colores';
import InfoContenedor from './InfoContenedor';
import type { PropsMapa, Vista } from './tipos';

// Mueve el mapa cuando cambia la vista (por ejemplo al filtrar por zona)
function AjustarVista({ vista }: { vista: Vista }) {
  const mapa = useMap();
  useEffect(() => {
    if (!mapa) return;
    mapa.setCenter({ lat: vista.lat, lng: vista.lng });
    mapa.setZoom(vista.zoom);
  }, [mapa, vista.lat, vista.lng, vista.zoom]);
  return null;
}

interface Props extends PropsMapa {
  clave: string;
}

export default function MapaGoogle({
  clave,
  contenedores,
  camiones = [],
  relojMs,
  vista,
  seleccionadoId,
  recorridoHecho,
  recorridoPendiente,
  className = 'mapa',
}: Props) {
  const [abiertoId, setAbiertoId] = useState<string>();
  const fecha = new Date(relojMs);
  const hora = fecha.getHours();
  const dia = diaDeSemana(fecha);
  const abierto = contenedores.find((c) => c.id === abiertoId);

  // Si se elige un contenedor desde la lista, se abre su globo de información
  useEffect(() => {
    if (seleccionadoId) setAbiertoId(seleccionadoId);
  }, [seleccionadoId]);

  return (
    <APIProvider apiKey={clave} language="es" region="AR">
      <MapaDeGoogle
        className={className}
        defaultCenter={{ lat: vista.lat, lng: vista.lng }}
        defaultZoom={vista.zoom}
        // Identificador de prueba de Google. Para producción conviene crear uno propio (gratis).
        mapId="DEMO_MAP_ID"
        gestureHandling="greedy"
        clickableIcons={false}
        mapTypeControl={false}
        streetViewControl={false}
        fullscreenControl={false}
      >
        <AjustarVista vista={vista} />

        {recorridoHecho && recorridoHecho.length > 1 && (
          <Polyline path={recorridoHecho} strokeColor="#7c8576" strokeWeight={5} strokeOpacity={0.9} />
        )}
        {recorridoPendiente && recorridoPendiente.length > 1 && (
          <Polyline path={recorridoPendiente} strokeColor="#2f6fd6" strokeWeight={5} strokeOpacity={0.9} />
        )}

        {contenedores.map((contenedor) => {
          const nivel = nivelVisual(contenedor, hora, dia);
          const elegido = contenedor.id === seleccionadoId;
          const diametro = elegido ? 24 : nivel === 'bajo' ? 12 : 16;
          return (
            <AdvancedMarker
              key={contenedor.id}
              position={contenedor.posicion}
              title={contenedor.direccion}
              anchorPoint={AdvancedMarkerAnchorPoint.CENTER}
              zIndex={nivel === 'bajo' ? 1 : nivel === 'medio' ? 2 : 3}
              onClick={() => setAbiertoId(contenedor.id)}
            >
              <div
                className="pin-contenedor"
                style={{
                  width: diametro,
                  height: diametro,
                  background: COLOR_NIVEL[nivel],
                  borderColor: elegido ? '#2f6fd6' : nivel === 'desborde' ? '#d9a82b' : '#ffffff',
                  borderWidth: elegido ? 3 : 2,
                }}
              />
            </AdvancedMarker>
          );
        })}

        {camiones.map((camion) => (
          <AdvancedMarker
            key={camion.id}
            position={camion.posicion}
            title={`Camión ${camion.numero}, ${camion.chofer}`}
            anchorPoint={AdvancedMarkerAnchorPoint.CENTER}
            zIndex={10}
          >
            <div className="marca-camion">
              <span>{camion.numero}</span>
            </div>
          </AdvancedMarker>
        ))}

        {abierto && (
          <InfoWindow position={abierto.posicion} pixelOffset={[0, -10]} onCloseClick={() => setAbiertoId(undefined)}>
            <InfoContenedor
              contenedor={abierto}
              nivel={nivelVisual(abierto, hora, dia)}
              motivo={motivoDelContenedor(abierto, hora, dia)}
            />
          </InfoWindow>
        )}
      </MapaDeGoogle>
    </APIProvider>
  );
}
