import type { EstadoSensor, NivelLlenado } from '../types';

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

// Borde de los contenedores con el sensor caído o trabado (nivel estimado)
export const BORDE_ESTIMADO = '#4d4a42';

// Contorno de las zonas en el mapa: relleno suave para que se sigan viendo las calles
export const BORDE_ZONA = '#4d5a52';
export const OPACIDAD_ZONA = 0.22;

// Estado de los sensores. El rojo ladrillo se reserva para el sensor que no manda nada.
export const COLOR_ESTADO_SENSOR: Record<EstadoSensor, string> = {
  funcionando: '#8ba67c',
  irregular: '#ecd9a3',
  congelado: '#a07a2c',
  sin_senal: '#9b4a36',
};
