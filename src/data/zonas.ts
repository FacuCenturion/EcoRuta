import type { Coordenada, Zona } from '../types';
import { puntoEnPoligono } from '../utils/geometria';
import limitesZonas from './limites-zonas.json';

// Las 7 zonas del Servicio de Higiene Urbana con la empresa a cargo de cada una,
// según el mapa oficial de zonas (Control de Servicios Públicos de la Ciudad).
//
// Los contornos están en limites-zonas.json. Cada zona es la unión de sus comunas,
// con los límites de barrios de los datos abiertos del GCBA. Para regenerarlo:
// scripts/generar_limites_zonas.py
//
// El centro y la base son ubicaciones de ejemplo dentro de cada zona.

type DatosZona = Omit<Zona, 'comunas' | 'limites'>;

const DATOS: DatosZona[] = [
  {
    id: 1,
    empresa: 'AESA Aseo y Ecología',
    color: '#f2e94e',
    centro: { lat: -34.602, lng: -58.377 },
    base: { lat: -34.609, lng: -58.37 },
    calles: ['Esmeralda', 'Suipacha', 'Florida', 'Libertad', 'Maipú', 'Reconquista'],
    callesLimite: ['Uruguay', 'Av. Córdoba', 'Av. Entre Ríos', 'Av. Caseros', 'Av. Brasil'],
  },
  {
    id: 2,
    empresa: 'Cliba Ingeniería Urbana',
    color: '#a8dcd9',
    centro: { lat: -34.588, lng: -58.412 },
    base: { lat: -34.5815, lng: -58.419 },
    calles: ['Güemes', 'Av. Santa Fe', 'Salguero', 'Av. Las Heras', 'Scalabrini Ortiz', 'Av. Coronel Díaz'],
    callesLimite: [
      'Uruguay',
      'Av. Córdoba',
      'Av. Cabildo',
      'Av. Crisólogo Larralde',
      'Zapiola',
      'Monroe',
      'Rómulo Naón',
      'Av. Álvarez Thomas',
    ],
  },
  {
    id: 3,
    empresa: 'IMPSA Ambiental',
    color: '#c5dc8a',
    centro: { lat: -34.5745, lng: -58.488 },
    base: { lat: -34.581, lng: -58.4815 },
    calles: ['Av. Triunvirato', 'Av. Olazábal', 'Blanco Encalada', 'Bauness', 'Echeverría', 'Pacheco'],
    callesLimite: [
      'Av. Cabildo',
      'Av. Crisólogo Larralde',
      'Zapiola',
      'Monroe',
      'Rómulo Naón',
      'Av. Álvarez Thomas',
      'Av. Córdoba',
      'Av. Ángel Gallardo',
      'Av. San Martín',
      'Av. Lope de Vega',
      'Baigorria',
      'Joaquín V. González',
    ],
  },
  {
    id: 4,
    empresa: 'Ecohabitat - UTE',
    color: '#eea86b',
    centro: { lat: -34.638, lng: -58.495 },
    base: { lat: -34.6445, lng: -58.4885 },
    calles: ['Av. Rivadavia', 'Ramón L. Falcón', 'Yerbal', 'Av. Juan B. Alberdi', 'Bacacay', 'Av. Directorio'],
    callesLimite: [
      'Av. Lope de Vega',
      'Baigorria',
      'Joaquín V. González',
      'Portela',
      'Av. Castañares',
      'Av. Eva Perón',
      'Av. Escalada',
    ],
  },
  {
    id: 5,
    empresa: 'Ente de Higiene Urbana',
    color: '#c9c6d3',
    centro: { lat: -34.67, lng: -58.47 },
    base: { lat: -34.664, lng: -58.464 },
    calles: [
      'Av. Larrazábal',
      'Av. Cruz',
      'Av. Fernández de la Cruz',
      'Av. Lacarra',
      'Av. Mariano Acosta',
      'Av. Coronel Roca',
    ],
    callesLimite: ['Av. Eva Perón', 'Av. Escalada', 'Av. Perito Moreno', 'Av. Varela'],
  },
  {
    id: 6,
    empresa: 'Ashira Martín y Martín - UTE',
    color: '#ead8b0',
    centro: { lat: -34.618, lng: -58.44 },
    base: { lat: -34.6245, lng: -58.4335 },
    calles: ['Av. Rivadavia', 'Av. Directorio', 'Acoyte', 'Av. La Plata', 'Pedro Goyena', 'Av. Díaz Vélez'],
    callesLimite: [
      'Av. Ángel Gallardo',
      'Av. San Martín',
      'Portela',
      'Av. Castañares',
      'Av. Perito Moreno',
      'Av. Riestra',
      'Av. Caseros',
      'Sánchez de Loria',
    ],
  },
  {
    id: 7,
    empresa: 'Transportes Olivos - Urbaser - UTE',
    color: '#e3912b',
    centro: { lat: -34.643, lng: -58.38 },
    base: { lat: -34.637, lng: -58.387 },
    calles: ['Av. Montes de Oca', 'Iriarte', 'Suárez', 'Herrera', 'Pinzón', 'Olavarría'],
    callesLimite: [
      'Av. Córdoba',
      'Sánchez de Loria',
      'Av. Caseros',
      'Av. Riestra',
      'Av. Varela',
      'Av. Entre Ríos',
      'Av. Brasil',
    ],
  },
];

function aCoordenadas(poligono: number[][]): Coordenada[] {
  return poligono.map(([lat, lng]) => ({ lat, lng }));
}

export const ZONAS: Zona[] = DATOS.map((datos) => {
  const limites = limitesZonas.zonas.find((z) => z.id === datos.id);
  if (!limites) throw new Error(`Faltan los límites de la zona ${datos.id}`);
  return {
    ...datos,
    comunas: limites.comunas,
    limites: limites.poligonos.map(aCoordenadas),
  };
});

export function buscarZona(id: number): Zona {
  const zona = ZONAS.find((z) => z.id === id);
  if (!zona) throw new Error(`No existe la zona ${id}`);
  return zona;
}

// Zona a la que pertenece una ubicación, o undefined si queda fuera de la Ciudad
export function zonaDeUbicacion(punto: Coordenada): Zona | undefined {
  return ZONAS.find((zona) => zona.limites.some((parte) => puntoEnPoligono(punto, parte)));
}
