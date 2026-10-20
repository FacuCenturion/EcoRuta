import { describe, expect, it } from 'vitest';
import { armarTramos, flechaDe, navegar, textoDistancia, textoManiobra, velocidadMaxima } from './navegacion';
import type { Ruta, Trazado } from './tipos';

const base = { lat: -34.6, lng: -58.38 };
const esquina = { lat: -34.6, lng: -58.37 }; // ~900 m al este
const contenedor = { lat: -34.595, lng: -58.37 }; // ~550 m al norte de la esquina

const ruta = {
  id: 1,
  paradas: [{ id: 1, posicion: contenedor }],
} as unknown as Ruta;

const trazado: Trazado = {
  rutaId: 1,
  estado: 'listo',
  kmPorCalle: 2.9,
  tramos: [[base, esquina, contenedor], [contenedor, base]],
  indicaciones: [
    [
      { tipo: 'depart', modificador: null, calle: 'Av. Belgrano', salida: null, ...base, metros: 900 },
      { tipo: 'turn', modificador: 'left', calle: 'Lima', salida: null, ...esquina, metros: 550 },
      { tipo: 'arrive', modificador: null, calle: 'Lima', salida: null, ...contenedor, metros: 0 },
    ],
    [],
  ],
};

describe('navegación', () => {
  it('sin recorrido por las calles, cada tramo es una línea recta', () => {
    const tramos = armarTramos(ruta, undefined, base);
    expect(tramos).toHaveLength(2);
    expect(tramos[0].puntos).toEqual([base, contenedor]);
    expect(tramos[0].porCalle).toBe(false);
  });

  it('antes del giro indica cuánto falta y por qué calle se va', () => {
    const [tramo] = armarTramos(ruta, trazado, base);
    const estado = navegar(tramo, { lat: -34.6, lng: -58.3745 }); // a mitad de la primera cuadra
    expect(estado.calleActual).toBe('Av. Belgrano');
    expect(estado.proxima?.calle).toBe('Lima');
    expect(flechaDe(estado.proxima)).toBe('izquierda');
    expect(estado.metrosAManiobra).toBeGreaterThan(350);
    expect(estado.metrosAManiobra).toBeLessThan(450);
    expect(estado.metrosAParada).toBeGreaterThan(estado.metrosAManiobra);
  });

  it('después del giro ya no quedan maniobras: sigue hasta la parada', () => {
    const [tramo] = armarTramos(ruta, trazado, base);
    const estado = navegar(tramo, { lat: -34.597, lng: -58.37 });
    expect(estado.calleActual).toBe('Lima');
    expect(estado.proxima).toBeNull();
    expect(flechaDe(estado.proxima)).toBe('llegada');
    expect(estado.metrosAManiobra).toBe(estado.metrosAParada);
  });

  it('las indicaciones se dicen como las diría alguien de acá', () => {
    const [tramo] = armarTramos(ruta, trazado, base);
    expect(textoManiobra(tramo.maniobras[1], 'Lima 775')).toBe('doblá a la izquierda en Lima');
    expect(textoManiobra(null, 'Lima 775')).toBe('seguí hasta Lima 775');
  });

  it('la máxima es 40 en calles y 60 en avenidas', () => {
    expect(velocidadMaxima('Lima')).toBe(40);
    expect(velocidadMaxima('Avenida Independencia')).toBe(60);
    expect(velocidadMaxima('Av. de Mayo')).toBe(60);
  });

  it('las distancias se redondean como en un navegador', () => {
    expect(textoDistancia(83)).toBe('80 m');
    expect(textoDistancia(437)).toBe('450 m');
    expect(textoDistancia(1240)).toBe('1,2 km');
  });
});
