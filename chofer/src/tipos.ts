// Lo que manda el backend en /api/v1/chofer (ver backend/README.md)

export interface Coordenada {
  lat: number;
  lng: number;
}

export type EstadoParada = 'pendiente' | 'recolectada' | 'no_se_pudo';
export type EstadoRuta = 'programada' | 'en_curso' | 'terminada';
export type EstadoCamion = 'en_base' | 'en_ruta' | 'en_descarga' | 'demorado';

export interface Parada {
  id: number;
  contenedorId: string;
  direccion: string;
  posicion: Coordenada;
  orden: number;
  estado: EstadoParada;
  motivoVisita: string | null; // llenado, riesgo, reporte, tiempo o de_paso
  eta: string | null;
  horaReal: string | null;
  motivo: string | null; // por qué no se pudo
}

export interface Ruta {
  id: number;
  estado: EstadoRuta;
  creada: string;
  salidaProgramada: string | null;
  kmPlanificados: number;
  duracionEstimadaMin: number;
  paradas: Parada[];
}

export interface Turno {
  chofer: string;
  camion: {
    id: string;
    numero: number;
    sectorId: number;
    sector: string | null;
    estado: EstadoCamion;
    cargaPct: number;
    posicion: Coordenada;
    kmRecorridosHoy: number;
  };
  manejadoPorApp: boolean;
  base: Coordenada;
  ruta: Ruta | null;
  reloj: string;
}

export interface Trazado {
  rutaId: number;
  estado: 'listo' | 'calculando' | 'sin_servicio' | 'fallo';
  kmPorCalle: number | null;
  tramos: Coordenada[][];
}

export interface UsuarioSesion {
  usuario: string;
  nombre: string;
  iniciales: string;
  rol: string;
}
