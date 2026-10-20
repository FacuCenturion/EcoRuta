export interface Coordenada {
  lat: number;
  lng: number;
}

export interface Zona {
  id: number;
  empresa: string;
  comunas: number[];
  color: string; // mismo color que el mapa oficial de zonas
  centro: Coordenada; // sector donde están los contenedores de la demo
  base: Coordenada; // playón desde el que salen los camiones
  calles: string[];
  callesLimite: string[]; // calles que separan la zona de las vecinas
  limites: Coordenada[][]; // contorno de la zona (puede tener más de una parte)
}

export type NivelLlenado = 'bajo' | 'medio' | 'prioritario' | 'desborde';

// Lo único que mandan los sensores: el nivel de llenado y cuándo se midió
export interface LecturaSensor {
  ms: number;
  nivel: number;
}

// Estado del sensor deducido de sus lecturas (no sabemos nada de la batería)
// "trabado" lo detecta solo el backend: el chofer vació el contenedor y el sensor siguió marcando lo mismo
export type EstadoSensor = 'funcionando' | 'irregular' | 'congelado' | 'sin_senal' | 'trabado';

// Falla que el simulador le pone a un sensor. El sistema no la conoce:
// la tiene que descubrir mirando las lecturas.
export type FallaSimulada = 'ninguna' | 'sin_senal' | 'congelado';

// Las zonas son de las empresas recolectoras. Con datos simulados en el navegador el panel
// filtra por zona; conectado al backend trabaja solo con la zona 1 dividida en sectores.
// areaId es lo que usa el filtro en cada caso: la zona o el sector.
export interface Contenedor {
  id: string;
  direccion: string;
  zonaId: number;
  areaId: number;
  barrio?: string;
  posicion: Coordenada;
  velocidad: number; // % de llenado por hora en condiciones normales
  nivelReal: number; // lo que realmente hay dentro (el sensor no lo ve directo)
  llenado: number; // última lectura del sensor, en %
  lecturas: number[]; // últimas lecturas, la más nueva al final
  ultimaLecturaMs: number;
  irregularHasta: number; // tick hasta el que se simula una persona revolviendo
  anomalia: boolean; // el sistema detectó lecturas irregulares
  reportesVecinales: number; // reportes abiertos hechos por vecinos
  historial: LecturaSensor[]; // lecturas de las últimas 24 h, para la curva
  fallaSimulada: FallaSimulada;
  estadoSensor: EstadoSensor;
  nivelEstimado?: number; // se calcula cuando no se puede confiar en el sensor
  ultimaRecoleccionMs: number; // última vez que un chofer confirmó que lo vació (0 si no hay dato)
  // Lo que ya calculó el backend; con datos simulados en el navegador se calcula acá
  motivo?: 'llenado' | 'riesgo' | 'reporte' | 'tiempo' | null;
  nivel?: NivelLlenado;
  desbordeEstimadoMs?: number; // pronóstico del backend, si es en las próximas 48 h
  desbordeDesdeMs?: number; // desde cuándo está lleno, si lo está
}

export type EstadoCamion = 'en_ruta' | 'en_descarga' | 'en_base' | 'demorado';

export type EstadoParada = 'pendiente' | 'recolectada' | 'no_se_pudo';

export interface Parada {
  contenedorId: string;
  direccion?: string;
  motivoVisita?: string; // por qué pasa el camión: llenado, riesgo, reporte, tiempo o de_paso
  estado: EstadoParada;
  etaMs: number; // hora estimada de llegada
  horaRealMs?: number; // hora en la que se recolectó
  motivo?: string; // por qué no se pudo recolectar
}

export interface Camion {
  id: string;
  numero: number;
  zonaId: number;
  areaId: number;
  rutaId?: number; // la ruta más nueva, con backend
  chofer: string;
  estado: EstadoCamion;
  paradas: Parada[];
  kmPlanificados: number; // largo total de la ruta actual, con vuelta a la base
  kmRecorridosHoy: number;
  cargaPct: number;
  posicion: Coordenada;
  minutosDisponibles: number;
  ticksDescarga: number;
}

export type TipoIncidente = 'llenado_irregular' | 'no_se_pudo' | 'reporte_vecinal' | 'falla_sensor' | 'desborde';
export type EstadoIncidente = 'pendiente' | 'confirmado' | 'descartado' | 'resuelto';

export interface Incidente {
  id: string;
  tipo: TipoIncidente;
  estado: EstadoIncidente;
  contenedorId: string;
  zonaId: number;
  areaId: number;
  direccion: string;
  descripcion: string;
  sugerencia: string;
  creadoMs: number;
  camionNumero?: number;
  origen?: string; // por ejemplo "Boti" en los reportes vecinales
}

export interface EstadoSimulacion {
  semilla: number;
  tick: number;
  relojMs: number; // hora simulada
  inicioDiaMs: number;
  contenedores: Contenedor[];
  camiones: Camion[];
  incidentes: Incidente[];
  recolectadosHoy: number;
  planificadosHoy: number;
  kmEvitadosHoy: number; // km que se ahorraron frente a visitar todos los contenedores
  contadorIncidentes: number;
}
