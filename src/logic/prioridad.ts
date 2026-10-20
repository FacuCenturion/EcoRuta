import { UMBRAL_BAJO, UMBRAL_DESBORDE, UMBRAL_PRIORITARIO, UMBRAL_RIESGO_PROYECTADO } from '../config';
import type { Contenedor, NivelLlenado } from '../types';
import { formatearCuando, formatearHaceCuanto } from '../utils/formato';
import { horasHastaProximaRonda, proyectarNivel } from './llenado';

// "tiempo" (más de 48 h sin recolectar) solo lo calcula el backend
export type MotivoPrioridad = 'llenado' | 'riesgo' | 'reporte' | 'tiempo';

export function clasificarNivel(llenado: number): NivelLlenado {
  if (llenado >= UMBRAL_DESBORDE) return 'desborde';
  if (llenado >= UMBRAL_PRIORITARIO) return 'prioritario';
  if (llenado >= UMBRAL_BAJO) return 'medio';
  return 'bajo';
}

// Nivel con el que se decide. Si el sensor no manda datos o quedó trabado,
// se usa el nivel estimado. Si da lecturas irregulares no nos fiamos de la
// última: usamos el promedio de las últimas tres.
export function lecturaConfiable(contenedor: Contenedor): number {
  if (contenedor.nivelEstimado !== undefined) return contenedor.nivelEstimado;
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
  if (contenedor.motivo !== undefined) return contenedor.motivo; // ya lo decidió el backend
  return motivoDePrioridad(lecturaConfiable(contenedor), contenedor.velocidad, hora, dia, contenedor.reportesVecinales);
}

export function etiquetaMotivo(motivo: MotivoPrioridad): string {
  if (motivo === 'llenado') return 'Superó el 80%';
  if (motivo === 'riesgo') return 'Riesgo de desborde';
  if (motivo === 'tiempo') return 'Más de 48 h sin recolectar';
  return 'Reporte vecinal';
}

// Nivel que se muestra en el mapa: un contenedor que todavía no llegó al 80%
// pero corre riesgo de desbordar también se pinta como prioritario.
export function nivelVisual(contenedor: Contenedor, hora: number, dia: number): NivelLlenado {
  if (contenedor.nivel) return contenedor.nivel;
  const lectura = lecturaConfiable(contenedor);
  if (lectura >= UMBRAL_DESBORDE) return 'desborde';
  if (motivoDelContenedor(contenedor, hora, dia) !== null) return 'prioritario';
  return clasificarNivel(lectura) === 'bajo' ? 'bajo' : 'medio';
}

// El pronóstico de desborde del backend, en palabras. Sin backend no hay pronóstico.
export function textoDeDesborde(contenedor: Contenedor, relojMs: number): string | null {
  if (contenedor.desbordeDesdeMs) return `Desbordado ${formatearHaceCuanto(contenedor.desbordeDesdeMs, relojMs)}`;
  if (contenedor.desbordeEstimadoMs && contenedor.desbordeEstimadoMs > relojMs) {
    return `Desborda ${formatearCuando(contenedor.desbordeEstimadoMs, relojMs)} (estimado)`;
  }
  return null;
}

export const ETIQUETA_MOTIVO_VISITA: Record<string, string> = {
  llenado: 'Superó el 80%',
  riesgo: 'Riesgo de desborde',
  reporte: 'Reporte vecinal',
  tiempo: 'Más de 48 h sin recolectar',
  de_paso: 'De paso: está cerca y pasa del 50%',
};
