import { UMBRAL_BAJO, UMBRAL_DESBORDE, UMBRAL_PRIORITARIO, UMBRAL_RIESGO_PROYECTADO } from '../config';
import type { Contenedor, NivelLlenado } from '../types';
import { horasHastaProximaRonda, proyectarNivel } from './llenado';

export type MotivoPrioridad = 'llenado' | 'riesgo' | 'reporte';

export function clasificarNivel(llenado: number): NivelLlenado {
  if (llenado >= UMBRAL_DESBORDE) return 'desborde';
  if (llenado >= UMBRAL_PRIORITARIO) return 'prioritario';
  if (llenado >= UMBRAL_BAJO) return 'medio';
  return 'bajo';
}

// Si el sensor está dando lecturas irregulares no nos fiamos de la última:
// usamos el promedio de las últimas tres.
export function lecturaConfiable(contenedor: Contenedor): number {
  if (!contenedor.anomalia) return contenedor.llenado;
  const ultimas = contenedor.lecturas.slice(-3);
  return ultimas.reduce((suma, valor) => suma + valor, 0) / ultimas.length;
}

// Decide si un contenedor tiene que entrar en la próxima ronda y por qué.
// Reglas:
//  1. Llenado actual mayor o igual al umbral (80%).
//  2. Riesgo de desborde: si de acá a la próxima ronda se va a pasar del 100%.
//  3. Tiene un reporte vecinal abierto.
export function motivoDePrioridad(
  llenado: number,
  velocidad: number,
  hora: number,
  dia: number,
  reportes: number,
): MotivoPrioridad | null {
  if (llenado >= UMBRAL_PRIORITARIO) return 'llenado';
  const horas = horasHastaProximaRonda(hora);
  if (proyectarNivel(llenado, velocidad, hora, horas, dia) >= UMBRAL_RIESGO_PROYECTADO) return 'riesgo';
  if (reportes > 0) return 'reporte';
  return null;
}

export function motivoDelContenedor(contenedor: Contenedor, hora: number, dia: number): MotivoPrioridad | null {
  return motivoDePrioridad(lecturaConfiable(contenedor), contenedor.velocidad, hora, dia, contenedor.reportesVecinales);
}

export function etiquetaMotivo(motivo: MotivoPrioridad): string {
  if (motivo === 'llenado') return 'Superó el 80%';
  if (motivo === 'riesgo') return 'Riesgo de desborde';
  return 'Reporte vecinal';
}

// Nivel que se muestra en el mapa: un contenedor que todavía no llegó al 80%
// pero corre riesgo de desbordar también se pinta como prioritario.
export function nivelVisual(contenedor: Contenedor, hora: number, dia: number): NivelLlenado {
  const lectura = lecturaConfiable(contenedor);
  if (lectura >= UMBRAL_DESBORDE) return 'desborde';
  if (motivoDelContenedor(contenedor, hora, dia) !== null) return 'prioritario';
  return clasificarNivel(lectura) === 'bajo' ? 'bajo' : 'medio';
}
