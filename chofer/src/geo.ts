// Cuentas de distancia sobre el mapa. En unas cuadras la Tierra se puede tomar como plana.
import type { Coordenada } from './tipos';

const METROS_POR_GRADO = 111_320;

export function distanciaM(a: Coordenada, b: Coordenada): number {
  const x = (b.lng - a.lng) * METROS_POR_GRADO * Math.cos((a.lat * Math.PI) / 180);
  const y = (b.lat - a.lat) * METROS_POR_GRADO;
  return Math.hypot(x, y);
}

// Rumbo de a hacia b, en grados (0 = norte, 90 = este)
export function rumbo(a: Coordenada, b: Coordenada): number {
  const x = (b.lng - a.lng) * Math.cos((a.lat * Math.PI) / 180);
  const y = b.lat - a.lat;
  return ((Math.atan2(x, y) * 180) / Math.PI + 360) % 360;
}

export function largo(linea: Coordenada[]): number {
  let total = 0;
  for (let i = 1; i < linea.length; i++) total += distanciaM(linea[i - 1], linea[i]);
  return total;
}

/** El punto de la línea más cercano a p: cuántos metros desde el inicio y a qué distancia queda. */
export function proyectar(p: Coordenada, linea: Coordenada[]): { recorrido: number; distancia: number } {
  let mejor = { recorrido: 0, distancia: Infinity };
  let acumulado = 0;
  for (let i = 1; i < linea.length; i++) {
    const a = linea[i - 1];
    const b = linea[i];
    const tramo = distanciaM(a, b);
    // Proyección en metros sobre el segmento a-b
    const escala = Math.cos((a.lat * Math.PI) / 180) * METROS_POR_GRADO;
    const bx = (b.lng - a.lng) * escala;
    const by = (b.lat - a.lat) * METROS_POR_GRADO;
    const px = (p.lng - a.lng) * escala;
    const py = (p.lat - a.lat) * METROS_POR_GRADO;
    const t = tramo === 0 ? 0 : Math.max(0, Math.min(1, (px * bx + py * by) / (tramo * tramo)));
    const distancia = Math.hypot(px - t * bx, py - t * by);
    if (distancia < mejor.distancia) mejor = { recorrido: acumulado + t * tramo, distancia };
    acumulado += tramo;
  }
  if (linea.length === 1) mejor = { recorrido: 0, distancia: distanciaM(p, linea[0]) };
  return mejor;
}

/** El punto que está a tantos metros del inicio de la línea, y hacia dónde va la línea ahí. */
export function puntoEn(linea: Coordenada[], metros: number): { punto: Coordenada; rumbo: number } {
  let acumulado = 0;
  for (let i = 1; i < linea.length; i++) {
    const a = linea[i - 1];
    const b = linea[i];
    const tramo = distanciaM(a, b);
    if (acumulado + tramo >= metros && tramo > 0) {
      const t = (metros - acumulado) / tramo;
      return {
        punto: { lat: a.lat + (b.lat - a.lat) * t, lng: a.lng + (b.lng - a.lng) * t },
        rumbo: rumbo(a, b),
      };
    }
    acumulado += tramo;
  }
  const ultimo = linea[linea.length - 1];
  const anterior = linea[linea.length - 2] ?? ultimo;
  return { punto: ultimo, rumbo: rumbo(anterior, ultimo) };
}
