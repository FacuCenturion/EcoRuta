import type { Zona } from '../types';

// Las 7 zonas de recolección de CABA con la empresa a cargo de cada una.
// Los centros y las calles son aproximados: sirven para la demo y se pueden
// reemplazar por los datos abiertos del Gobierno de la Ciudad.
export const ZONAS: Zona[] = [
  {
    id: 1,
    empresa: 'AESA Aseo y Ecología',
    centro: { lat: -34.598, lng: -58.372 },
    base: { lat: -34.6045, lng: -58.3665 },
    calles: ['Esmeralda', 'Suipacha', 'Florida', 'Libertad', 'Maipú', 'Reconquista'],
  },
  {
    id: 2,
    empresa: 'Cliba',
    centro: { lat: -34.587, lng: -58.405 },
    base: { lat: -34.5925, lng: -58.4},
    calles: ['Güemes', 'Av. Santa Fe', 'Salguero', 'Av. Las Heras', 'Scalabrini Ortiz', 'Av. Callao'],
  },
  {
    id: 3,
    empresa: 'Impsa Ambiental',
    centro: { lat: -34.617, lng: -58.383 },
    base: { lat: -34.6235, lng: -58.377 },
    calles: ['Chile', 'Tacuarí', 'Independencia', 'Lima', 'Salta', 'Perú'],
  },
  {
    id: 4,
    empresa: 'Ecohabitat',
    centro: { lat: -34.635, lng: -58.395 },
    base: { lat: -34.641, lng: -58.389 },
    calles: ['Av. Montes de Oca', 'Iriarte', 'Suárez', 'Herrera', 'Pinzón', 'Olavarría'],
  },
  {
    id: 5,
    empresa: 'Ente de Higiene Urbana',
    centro: { lat: -34.62, lng: -58.44 },
    base: { lat: -34.626, lng: -58.434 },
    calles: ['Av. Rivadavia', 'Av. Directorio', 'Acoyte', 'Av. La Plata', 'Pedro Goyena', 'Av. Díaz Vélez'],
  },
  {
    id: 6,
    empresa: 'Martín y Martín',
    centro: { lat: -34.575, lng: -58.48 },
    base: { lat: -34.581, lng: -58.474 },
    calles: ['Av. Triunvirato', 'Monroe', 'Donado', 'Tamborini', 'Plaza', 'Av. Congreso'],
  },
  {
    id: 7,
    empresa: 'Urbaser Argentina',
    centro: { lat: -34.66, lng: -58.47 },
    base: { lat: -34.666, lng: -58.464 },
    calles: ['Av. Lacarra', 'Av. Eva Perón', 'Av. Escalada', 'Av. Larrazábal', 'Av. Cruz', 'Av. Cobo'],
  },
];

export function buscarZona(id: number): Zona {
  const zona = ZONAS.find((z) => z.id === id);
  if (!zona) throw new Error(`No existe la zona ${id}`);
  return zona;
}
