// Navegación de la ruta: arma los tramos (base, paradas, base) y, para una posición, calcula
// la próxima maniobra, la calle por la que se va y cuánto falta.
import { distanciaM, largo, proyectar } from './geo';
import type { Coordenada, Indicacion, Parada, Ruta, Trazado } from './tipos';

export const MINUTOS_POR_PARADA = 5; // lo mismo que usa el backend para estimar
const VELOCIDAD_MEDIA_KMH = 22; // ciudad, con semáforos: para estimar el final de la ruta
export const METROS_PARA_LLEGAR = 25;

export interface Maniobra extends Indicacion {
  recorrido: number; // a cuántos metros del inicio del tramo está
}

export interface Tramo {
  puntos: Coordenada[];
  largo: number;
  maniobras: Maniobra[];
  porCalle: boolean; // false si es una línea recta porque no hay recorrido de OSRM
}

/** Un tramo por cada tramo de la ruta: de la base a la parada 1, entre paradas y la vuelta a la base. */
export function armarTramos(ruta: Ruta, trazado: Trazado | undefined, base: Coordenada): Tramo[] {
  const puntos = [base, ...ruta.paradas.map((p) => p.posicion), base];
  const conCalles = trazado?.estado === 'listo' && trazado.tramos.length === puntos.length - 1;
  return puntos.slice(0, -1).map((desde, i) => {
    const linea = conCalles ? trazado!.tramos[i] : [desde, puntos[i + 1]];
    const maniobras = conCalles
      ? (trazado!.indicaciones[i] ?? []).map((m) => ({ ...m, recorrido: proyectar(m, linea).recorrido }))
      : [];
    return { puntos: linea, largo: largo(linea), maniobras, porCalle: conCalles };
  });
}

export interface EstadoDeNavegacion {
  recorrido: number; // metros hechos del tramo actual
  metrosAParada: number;
  proxima: Maniobra | null; // la próxima maniobra antes de la parada (vacía: seguir hasta la parada)
  metrosAManiobra: number;
  calleActual: string;
  fueraDeRuta: boolean;
}

export function navegar(tramo: Tramo, posicion: Coordenada): EstadoDeNavegacion {
  const { recorrido, distancia } = proyectar(posicion, tramo.puntos);
  const metrosAParada = Math.max(0, tramo.largo - recorrido);
  // Las maniobras de salida y de llegada no son giros: la llegada se muestra como la parada
  const giros = tramo.maniobras.filter((m) => m.tipo !== 'depart' && m.tipo !== 'arrive');
  const proxima = giros.find((m) => m.recorrido > recorrido + 3) ?? null;
  const hechas = tramo.maniobras.filter((m) => m.recorrido <= recorrido + 3);
  const calleActual = hechas.length ? hechas[hechas.length - 1].calle : (tramo.maniobras[0]?.calle ?? '');
  return {
    recorrido,
    metrosAParada,
    proxima,
    metrosAManiobra: proxima ? proxima.recorrido - recorrido : metrosAParada,
    calleActual,
    fueraDeRuta: distancia > 60,
  };
}

/** Lo que falta de la ruta: el resto del tramo actual, los que siguen y el tiempo de cada parada. */
export function loQueFalta(tramos: Tramo[], actual: number, metrosDelActual: number, paradasPendientes: number) {
  const metros = metrosDelActual + tramos.slice(actual + 1).reduce((suma, t) => suma + t.largo, 0);
  const minutos = (metros / 1000 / VELOCIDAD_MEDIA_KMH) * 60 + paradasPendientes * MINUTOS_POR_PARADA;
  return { metros, minutos: Math.round(minutos) };
}

// Velocidad máxima en la Ciudad: 40 en calles, 60 en avenidas, 80 en autopistas
export function velocidadMaxima(calle: string): number {
  const nombre = calle.toLowerCase();
  if (nombre.startsWith('autopista') || nombre.startsWith('au ')) return 80;
  if (
    nombre.startsWith('avenida') ||
    nombre.startsWith('av.') ||
    nombre.startsWith('av ') ||
    nombre.startsWith('diagonal')
  )
    return 60;
  return 40;
}

export function textoDistancia(metros: number): string {
  if (metros >= 1000) return `${(metros / 1000).toLocaleString('es-AR', { maximumFractionDigits: 1 })} km`;
  if (metros >= 100) return `${Math.round(metros / 50) * 50} m`;
  return `${Math.max(0, Math.round(metros / 10) * 10)} m`;
}

// La calle de una dirección: "Lima 775" -> "Lima"
export function calleDe(direccion: string): string {
  return direccion.replace(/\s+\d+\s*$/, '');
}

export type Flecha =
  'izquierda' | 'derecha' | 'leve-izquierda' | 'leve-derecha' | 'derecho' | 'vuelta' | 'rotonda' | 'llegada';

export function flechaDe(m: Maniobra | null): Flecha {
  if (!m) return 'llegada';
  if (m.tipo === 'roundabout' || m.tipo === 'rotary' || m.tipo === 'roundabout turn') return 'rotonda';
  switch (m.modificador) {
    case 'left':
    case 'sharp left':
      return 'izquierda';
    case 'right':
    case 'sharp right':
      return 'derecha';
    case 'slight left':
      return 'leve-izquierda';
    case 'slight right':
      return 'leve-derecha';
    case 'uturn':
      return 'vuelta';
    default:
      return 'derecho';
  }
}

/** La indicación para decir en voz alta: "En 100 metros, doblá a la izquierda en Lima". */
export function textoManiobra(m: Maniobra | null, destino: string): string {
  if (!m) return `seguí hasta ${destino}`;
  const en = m.calle ? ` en ${m.calle}` : '';
  if (m.tipo === 'roundabout' || m.tipo === 'rotary') return `en la rotonda, tomá la ${m.salida ?? 1}.ª salida${en}`;
  switch (flechaDe(m)) {
    case 'izquierda':
      return `doblá a la izquierda${en}`;
    case 'derecha':
      return `doblá a la derecha${en}`;
    case 'leve-izquierda':
      return `tomá levemente a la izquierda${en}`;
    case 'leve-derecha':
      return `tomá levemente a la derecha${en}`;
    case 'vuelta':
      return `pegá la vuelta${en}`;
    default:
      return m.calle ? `seguí por ${m.calle}` : 'seguí derecho';
  }
}

export type Prioridad = 'Alta' | 'Media' | 'Baja';

export function prioridadDe(parada: Pick<Parada, 'motivoVisita' | 'llenado'>): Prioridad {
  if (parada.motivoVisita === 'llenado' || parada.motivoVisita === 'riesgo' || parada.motivoVisita === 'reporte')
    return 'Alta';
  if (parada.motivoVisita === 'tiempo' || parada.llenado >= 50) return 'Media';
  return 'Baja';
}

export function esPrioritaria(parada: Parada): boolean {
  return prioridadDe(parada) === 'Alta';
}

export { distanciaM };
