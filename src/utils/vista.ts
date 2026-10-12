import { ZONAS } from '../data/zonas';
import type { FiltroZona } from '../context/SimulacionContext';
import type { Vista } from '../components/MapaContenedores';
import type { Zona } from '../types';

const VISTA_CIUDAD: Vista = { lat: -34.615, lng: -58.43, zoom: 12 };

// Vista del mapa: toda la ciudad o la zona elegida completa
export function vistaParaZona(zona: FiltroZona): Vista {
  if (zona === 'todas') return VISTA_CIUDAD;
  const datos = ZONAS.find((z) => z.id === zona);
  if (!datos) return VISTA_CIUDAD;

  // Centro del rectángulo que encierra la zona
  const puntos = datos.limites.flat();
  const lats = puntos.map((p) => p.lat);
  const lngs = puntos.map((p) => p.lng);
  return {
    lat: (Math.min(...lats) + Math.max(...lats)) / 2,
    lng: (Math.min(...lngs) + Math.max(...lngs)) / 2,
    zoom: 13,
  };
}

// Zonas que se dibujan en el mapa: todas o solo la elegida
export function zonasParaFiltro(zona: FiltroZona): Zona[] {
  return zona === 'todas' ? ZONAS : ZONAS.filter((z) => z.id === zona);
}
