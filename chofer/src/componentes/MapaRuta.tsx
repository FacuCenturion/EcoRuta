import L from 'leaflet';
import { useEffect, useMemo } from 'react';
import { MapContainer, Marker, Polyline, TileLayer, useMap } from 'react-leaflet';
import type { Coordenada, Ruta, Trazado } from '../tipos';

interface Props {
  ruta: Ruta;
  trazado: Trazado | undefined;
  base: Coordenada;
  camion: Coordenada;
}

const COLOR_HECHO = '#7c8576';
const COLOR_PENDIENTE = '#2f6fd6';

function icono(texto: string, clase: string) {
  return L.divIcon({ className: '', html: `<span class="pin ${clase}">${texto}</span>`, iconSize: [28, 28], iconAnchor: [14, 14] });
}

// Encuadra la ruta completa la primera vez y cada vez que cambia de ruta
function Encuadrar({ puntos, rutaId }: { puntos: Coordenada[]; rutaId: number }) {
  const mapa = useMap();
  useEffect(() => {
    if (puntos.length === 0) return;
    mapa.fitBounds(L.latLngBounds(puntos.map((p) => [p.lat, p.lng])), { padding: [24, 24] });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapa, rutaId]);
  return null;
}

// El recorrido por las calles si el backend ya lo tiene; si no, líneas rectas entre paradas.
// Hay un tramo por cada tramo de la ruta (base, paradas, base): los de paradas ya cerradas
// van en gris y el resto en azul.
export default function MapaRuta({ ruta, trazado, base, camion }: Props) {
  const puntos = useMemo(() => [base, ...ruta.paradas.map((p) => p.posicion), base], [base, ruta.paradas]);
  const cerradas = ruta.paradas.filter((p) => p.estado !== 'pendiente').length;
  const tramos =
    trazado?.estado === 'listo' && trazado.tramos.length === ruta.paradas.length + 1
      ? trazado.tramos
      : puntos.slice(0, -1).map((p, i) => [p, puntos[i + 1]]);
  const hecho = tramos.slice(0, cerradas).flat();
  const pendiente = tramos.slice(cerradas).flat();
  const proxima = ruta.paradas.find((p) => p.estado === 'pendiente');

  return (
    <MapContainer className="mapa" center={[base.lat, base.lng]} zoom={15} zoomControl={false} attributionControl>
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
      />
      <Encuadrar puntos={puntos} rutaId={ruta.id} />
      {hecho.length > 1 && <Polyline positions={hecho.map((p) => [p.lat, p.lng])} pathOptions={{ color: COLOR_HECHO, weight: 5 }} />}
      {pendiente.length > 1 && (
        <Polyline positions={pendiente.map((p) => [p.lat, p.lng])} pathOptions={{ color: COLOR_PENDIENTE, weight: 5 }} />
      )}
      <Marker position={[base.lat, base.lng]} icon={icono('B', 'pin--base')} title="Base" />
      {ruta.paradas.map((p, i) => (
        <Marker
          key={p.id}
          position={[p.posicion.lat, p.posicion.lng]}
          icon={icono(
            p.estado === 'recolectada' ? '✓' : p.estado === 'no_se_pudo' ? '!' : String(i + 1),
            p === proxima ? 'pin--proxima' : `pin--${p.estado}`,
          )}
          title={p.direccion}
          zIndexOffset={p === proxima ? 1000 : 0}
        />
      ))}
      {ruta.estado === 'en_curso' && (
        <Marker position={[camion.lat, camion.lng]} icon={icono('🚛', 'pin--camion')} title="Tu camión" zIndexOffset={2000} />
      )}
    </MapContainer>
  );
}
