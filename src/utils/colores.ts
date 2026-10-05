import type { NivelLlenado } from '../types';

// Colores del mapa, iguales a los del diseño de Figma
export const COLOR_NIVEL: Record<NivelLlenado, string> = {
  bajo: '#c9d6bd',
  medio: '#8ba67c',
  prioritario: '#a07a2c',
  desborde: '#3b2a12',
};

export const ETIQUETA_NIVEL: Record<NivelLlenado, string> = {
  bajo: 'Menos de 50%',
  medio: '50 a 80%',
  prioritario: 'Prioritario',
  desborde: 'Desborde',
};
