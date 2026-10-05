import type { Coordenada } from '../types';

const RADIO_TIERRA_KM = 6371;

function aRadianes(grados: number): number {
  return (grados * Math.PI) / 180;
}

// Distancia en línea recta entre dos puntos (fórmula de Haversine).
// Es una aproximación: las calles reales dan un recorrido algo más largo.
export function distanciaKm(a: Coordenada, b: Coordenada): number {
  const dLat = aRadianes(b.lat - a.lat);
  const dLng = aRadianes(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(aRadianes(a.lat)) * Math.cos(aRadianes(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * RADIO_TIERRA_KM * Math.asin(Math.sqrt(h));
}

export interface Punto {
  id: string;
  posicion: Coordenada;
}

export interface RutaPlanificada {
  orden: string[]; // ids de los contenedores en el orden de visita
  km: number; // largo total contando la vuelta a la base
}

// Ordena las paradas con el algoritmo del vecino más cercano: desde la base
// va siempre al contenedor pendiente que le queda más cerca.
// Más adelante se puede reemplazar por la API de rutas de Google Maps.
export function planificarRuta(base: Coordenada, puntos: Punto[]): RutaPlanificada {
  const pendientes = [...puntos];
  const orden: string[] = [];
  let actual = base;
  let km = 0;

  while (pendientes.length > 0) {
    let mejor = 0;
    let menor = Infinity;
    pendientes.forEach((punto, i) => {
      const d = distanciaKm(actual, punto.posicion);
      if (d < menor) {
        menor = d;
        mejor = i;
      }
    });
    const elegido = pendientes.splice(mejor, 1)[0];
    orden.push(elegido.id);
    km += menor;
    actual = elegido.posicion;
  }

  if (orden.length > 0) km += distanciaKm(actual, base);
  return { orden, km };
}
