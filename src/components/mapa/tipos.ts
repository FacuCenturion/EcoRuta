import type { Camion, Contenedor, Coordenada } from '../../types';

export interface Vista {
  lat: number;
  lng: number;
  zoom: number;
}

// Datos que reciben todos los mapas, sin importar la librería que usen
export interface PropsMapa {
  contenedores: Contenedor[];
  camiones?: Camion[];
  relojMs: number;
  vista: Vista;
  seleccionadoId?: string;
  recorridoHecho?: Coordenada[];
  recorridoPendiente?: Coordenada[];
  className?: string;
}
