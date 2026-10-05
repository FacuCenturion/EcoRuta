import { HORAS_DE_RONDA } from '../config';

// Cuánto se llena un contenedor según la hora del día (1 = ritmo normal).
// El pico está entre las 18 y las 21, cuando los vecinos sacan la basura.
export const FACTOR_HORA: number[] = [
  0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // 0 a 5
  0.6, 0.8, 1.0, 1.0, 1.0, 1.0, // 6 a 11
  1.1, 1.1, 1.1, 1.2, 1.2, 1.2, // 12 a 17
  2.0, 2.2, 2.0, 1.5, 0.8, 0.5, // 18 a 23
];

// Lunes = 0 ... domingo = 6. Los fines de semana se genera un poco más de basura.
export const FACTOR_DIA: number[] = [1, 1, 1, 1, 1.1, 1.2, 1.1];

export function diaDeSemana(fecha: Date): number {
  return (fecha.getDay() + 6) % 7;
}

// Cuántos puntos de llenado suma un contenedor durante una hora.
export function incrementoPorHora(velocidad: number, hora: number, dia: number): number {
  return velocidad * FACTOR_HORA[hora % 24] * FACTOR_DIA[dia];
}

// Estima el nivel que tendría el contenedor dentro de "horas" horas.
export function proyectarNivel(nivel: number, velocidad: number, horaActual: number, horas: number, dia: number): number {
  let proyectado = nivel;
  for (let i = 1; i <= horas; i++) {
    proyectado += incrementoPorHora(velocidad, horaActual + i, dia);
  }
  return proyectado;
}

// Horas que faltan hasta la próxima ronda de recolección.
export function horasHastaProximaRonda(hora: number): number {
  const proxima = HORAS_DE_RONDA.find((h) => h > hora);
  return proxima !== undefined ? proxima - hora : HORAS_DE_RONDA[0] + 24 - hora;
}
