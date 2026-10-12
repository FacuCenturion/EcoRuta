import type { Coordenada } from '../types';

// Indica si un punto está dentro de un polígono (método del rayo).
// Para las distancias que manejamos alcanza con tratar lat/lng como un plano.
export function puntoEnPoligono(punto: Coordenada, poligono: Coordenada[]): boolean {
  let adentro = false;
  for (let i = 0, j = poligono.length - 1; i < poligono.length; j = i++) {
    const a = poligono[i];
    const b = poligono[j];
    const cruza =
      a.lat > punto.lat !== b.lat > punto.lat &&
      punto.lng < ((b.lng - a.lng) * (punto.lat - a.lat)) / (b.lat - a.lat) + a.lng;
    if (cruza) adentro = !adentro;
  }
  return adentro;
}
