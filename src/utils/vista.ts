import { ZONAS } from '../data/zonas';
import type { FiltroZona } from '../context/SimulacionContext';
import type { Vista } from '../components/MapaContenedores';

// Vista del mapa: toda la ciudad o la zona elegida
export function vistaParaZona(zona: FiltroZona): Vista {
  if (zona === 'todas') return { lat: -34.615, lng: -58.43, zoom: 12 };
  const datos = ZONAS.find((z) => z.id === zona);
  if (!datos) return { lat: -34.615, lng: -58.43, zoom: 12 };
  return { lat: datos.centro.lat, lng: datos.centro.lng, zoom: 15 };
}
