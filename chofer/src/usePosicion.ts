// De dónde sale la posición del camión:
//  - "gps": el GPS del celular, para usarla manejando de verdad.
//  - "simulada": para la demo. El camión avanza solo por el recorrido, a velocidad de ciudad,
//    y se detiene al llegar a cada parada hasta que el chofer la cierra.
import { useEffect, useRef, useState } from 'react';
import { proyectar, puntoEn, rumbo as rumboEntre } from './geo';
import { METROS_PARA_LLEGAR, type Tramo } from './navegacion';
import type { Coordenada } from './tipos';

export type ModoUbicacion = 'simulada' | 'gps';

export interface Posicion {
  punto: Coordenada;
  rumbo: number; // grados, 0 = norte
  velocidadKmh: number;
}

const PASO_MS = 500;

export function usePosicion(
  modo: ModoUbicacion,
  tramo: Tramo | undefined,
  andando: boolean, // false: el camión está parado (en una parada, en la base o con la ruta sin iniciar)
  inicio: Coordenada | undefined, // dónde estaba el camión según el backend; vacío hasta que llega el turno
  acelerar: number, // en la simulación, cuántas veces más rápido que en la realidad
): Posicion {
  const [posicion, setPosicion] = useState<Posicion>({ punto: inicio ?? { lat: 0, lng: 0 }, rumbo: 0, velocidadKmh: 0 });
  const actual = useRef(posicion);
  actual.current = posicion;

  // La primera vez que llega el turno, el camión aparece donde lo dejó el backend: así, si se
  // cierra y se vuelve a abrir la app a mitad de ruta, sigue desde la última parada
  const ubicado = useRef(Boolean(inicio));
  useEffect(() => {
    if (!ubicado.current && inicio) {
      ubicado.current = true;
      setPosicion({ punto: inicio, rumbo: 0, velocidadKmh: 0 });
    }
  }, [inicio]);

  // Al cambiar de tramo sin estar andando (por ejemplo al empezar la ruta) el camión arranca del principio
  const clave = tramo ? `${tramo.puntos[0].lat},${tramo.puntos[0].lng}` : '';
  useEffect(() => {
    if (modo === 'simulada' && tramo && !andando && proyectar(actual.current.punto, tramo.puntos).distancia > 80) {
      setPosicion({ punto: tramo.puntos[0], rumbo: 0, velocidadKmh: 0 });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave, modo]);

  useEffect(() => {
    if (modo !== 'simulada') return;
    if (!tramo || !andando) {
      setPosicion((p) => ({ ...p, velocidadKmh: 0 }));
      return;
    }
    const temporizador = setInterval(() => {
      const ahora = actual.current;
      const { recorrido } = proyectar(ahora.punto, tramo.puntos);
      const falta = tramo.largo - recorrido;
      if (falta <= 1) {
        setPosicion({ ...ahora, velocidadKmh: 0 });
        return;
      }
      // Más despacio al acercarse a un giro o a la parada, como en la calle
      const proximoGiro = tramo.maniobras.find((m) => m.recorrido > recorrido + 3);
      const cerca = Math.min(falta, proximoGiro ? proximoGiro.recorrido - recorrido : Infinity);
      const objetivo = cerca < 30 ? 14 : cerca < 80 ? 24 : 28 + Math.sin(Date.now() / 4000) * 6;
      const velocidad = ahora.velocidadKmh + Math.max(-8, Math.min(5, objetivo - ahora.velocidadKmh));
      const avance = (velocidad / 3.6) * (PASO_MS / 1000) * acelerar;
      const siguiente = puntoEn(tramo.puntos, Math.min(tramo.largo, recorrido + avance));
      setPosicion({ punto: siguiente.punto, rumbo: siguiente.rumbo, velocidadKmh: Math.round(velocidad) });
    }, PASO_MS);
    return () => clearInterval(temporizador);
  }, [modo, tramo, andando, acelerar]);

  useEffect(() => {
    if (modo !== 'gps' || !('geolocation' in navigator)) return;
    const id = navigator.geolocation.watchPosition(
      (p) => {
        const punto = { lat: p.coords.latitude, lng: p.coords.longitude };
        const anterior = actual.current.punto;
        setPosicion({
          punto,
          rumbo: p.coords.heading ?? rumboEntre(anterior, punto),
          velocidadKmh: Math.round((p.coords.speed ?? 0) * 3.6),
        });
      },
      () => {
        // sin permiso o sin señal: queda la última posición conocida
      },
      { enableHighAccuracy: true, maximumAge: 2000 },
    );
    return () => navigator.geolocation.clearWatch(id);
  }, [modo]);

  return posicion;
}

export function llego(tramo: Tramo | undefined, punto: Coordenada): boolean {
  if (!tramo) return false;
  return tramo.largo - proyectar(punto, tramo.puntos).recorrido <= METROS_PARA_LLEGAR;
}
