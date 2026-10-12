import { describe, expect, it } from 'vitest';
import { crearContenedores } from '../data/semilla';
import { ZONAS, zonaDeUbicacion } from '../data/zonas';
import { avanzar, crearEstadoInicial, decidirIncidente } from '../simulator/simulador';
import type { Contenedor } from '../types';
import { detectarAnomalia } from './anomalias';
import { actualizarSensor, estadoDelSensor, nivelEstimado, resumenSensores } from './estadoSensor';
import { generarHistorial } from './historial';
import { clasificarNivel, motivoDePrioridad } from './prioridad';
import { distanciaKm, planificarRuta } from './rutas';

describe('prioridad', () => {
  it('clasifica el nivel de llenado según los umbrales', () => {
    expect(clasificarNivel(30)).toBe('bajo');
    expect(clasificarNivel(65)).toBe('medio');
    expect(clasificarNivel(80)).toBe('prioritario');
    expect(clasificarNivel(97)).toBe('desborde');
  });

  it('marca como prioritario un contenedor que supera el 80%', () => {
    expect(motivoDePrioridad(82, 3, 10, 1, 0)).toBe('llenado');
  });

  it('marca riesgo de desborde si no llega a la próxima ronda', () => {
    // A las 17 la próxima ronda es a las 20 y el pico de llenado está en el medio
    expect(motivoDePrioridad(70, 8, 17, 1, 0)).toBe('riesgo');
  });

  it('no marca un contenedor casi vacío', () => {
    expect(motivoDePrioridad(20, 3, 10, 1, 0)).toBeNull();
  });

  it('un reporte vecinal alcanza para priorizar', () => {
    expect(motivoDePrioridad(30, 3, 10, 1, 1)).toBe('reporte');
  });
});

describe('rutas', () => {
  it('calcula una distancia razonable entre dos puntos', () => {
    const obelisco = { lat: -34.6037, lng: -58.3816 };
    const congreso = { lat: -34.6099, lng: -58.3926 };
    const d = distanciaKm(obelisco, congreso);
    expect(d).toBeGreaterThan(1);
    expect(d).toBeLessThan(1.5);
  });

  it('visita primero el punto más cercano y vuelve a la base', () => {
    const base = { lat: 0, lng: 0 };
    const ruta = planificarRuta(base, [
      { id: 'lejos', posicion: { lat: 0, lng: 0.02 } },
      { id: 'cerca', posicion: { lat: 0, lng: 0.005 } },
    ]);
    expect(ruta.orden).toEqual(['cerca', 'lejos']);
    expect(ruta.km).toBeCloseTo(2 * distanciaKm(base, { lat: 0, lng: 0.02 }), 3);
  });

  it('sin puntos no hay recorrido', () => {
    expect(planificarRuta({ lat: 0, lng: 0 }, [])).toEqual({ orden: [], km: 0 });
  });
});

describe('anomalías', () => {
  it('no detecta nada en un llenado normal', () => {
    expect(detectarAnomalia([20, 21, 22, 23, 25, 26])).toBe(false);
  });

  it('no confunde un vaciado con una anomalía', () => {
    expect(detectarAnomalia([70, 75, 80, 4, 5, 6])).toBe(false);
  });

  it('detecta lecturas que suben y bajan de golpe', () => {
    expect(detectarAnomalia([52, 61, 49, 62, 48, 60])).toBe(true);
  });
});

describe('simulador', () => {
  it('arranca con camiones en ruta y algo para revisar', () => {
    const estado = crearEstadoInicial();
    expect(estado.contenedores).toHaveLength(7 * 18);
    expect(estado.camiones.some((c) => c.estado === 'en_ruta')).toBe(true);
    expect(estado.incidentes.some((i) => i.estado === 'pendiente')).toBe(true);
  });

  it('al avanzar el tiempo los camiones recolectan contenedores', () => {
    let estado = crearEstadoInicial();
    for (let i = 0; i < 40; i++) estado = avanzar(estado);
    expect(estado.recolectadosHoy).toBeGreaterThan(0);
    estado.contenedores.forEach((c) => {
      expect(c.nivelReal).toBeGreaterThanOrEqual(0);
      expect(c.nivelReal).toBeLessThanOrEqual(100);
    });
  });

  it('suma los km evitados del día cuando se arman las rutas', () => {
    const estado = crearEstadoInicial();
    expect(estado.kmEvitadosHoy).toBeGreaterThan(0);
  });

  it('el operador puede confirmar y descartar incidentes', () => {
    const estado = crearEstadoInicial();
    const [primero, segundo] = estado.incidentes.filter((i) => i.estado === 'pendiente');
    const confirmado = decidirIncidente(estado, primero.id, 'confirmado');
    const descartado = decidirIncidente(confirmado, segundo.id, 'descartado');
    expect(descartado.incidentes.find((i) => i.id === primero.id)?.estado).toBe('confirmado');
    expect(descartado.incidentes.find((i) => i.id === segundo.id)?.estado).toBe('descartado');
  });
});

describe('estado de los sensores', () => {
  const AHORA = new Date(2026, 9, 13, 12, 0).getTime();
  const MINUTO = 60 * 1000;

  // Contenedor de prueba con una lectura cada 5 minutos que termina en "hasta"
  function contenedorCon(niveles: number[], hasta = AHORA): Contenedor {
    const [base] = crearContenedores(2026);
    const historial = niveles.map((nivel, i) => ({ ms: hasta - (niveles.length - 1 - i) * 5 * MINUTO, nivel }));
    return {
      ...base,
      historial,
      lecturas: niveles.slice(-8),
      llenado: niveles[niveles.length - 1],
      ultimaLecturaMs: hasta,
      anomalia: false,
      ultimaRecoleccionMs: 0,
    };
  }

  // 6 horas de lecturas que van subiendo de a poco
  const subiendo = Array.from({ length: 73 }, (_, i) => 20 + Math.floor(i / 3));

  it('un sensor que mide normal está funcionando', () => {
    expect(estadoDelSensor(contenedorCon(subiendo), AHORA)).toBe('funcionando');
  });

  it('si no manda datos hace más de 2 horas no tiene señal', () => {
    const contenedor = contenedorCon(subiendo, AHORA - 3 * 60 * MINUTO);
    expect(estadoDelSensor(contenedor, AHORA)).toBe('sin_senal');
  });

  it('si marca lo mismo durante 4 horas quedó congelado', () => {
    const congelado = [...subiendo.slice(0, 20), ...Array(53).fill(40)];
    expect(estadoDelSensor(contenedorCon(congelado), AHORA)).toBe('congelado');
  });

  it('un contenedor lleno que siempre marca 100 no es una falla', () => {
    expect(estadoDelSensor(contenedorCon(Array(73).fill(100)), AHORA)).toBe('funcionando');
  });

  it('sin datos de toda la ventana no se lo da por congelado', () => {
    expect(estadoDelSensor(contenedorCon(Array(10).fill(40)), AHORA)).toBe('funcionando');
  });

  it('las lecturas irregulares no cuentan como sensor caído', () => {
    const contenedor = { ...contenedorCon(subiendo), anomalia: true };
    expect(estadoDelSensor(contenedor, AHORA)).toBe('irregular');
  });

  it('sin señal, el nivel se estima desde la última lectura', () => {
    const contenedor = contenedorCon(subiendo, AHORA - 5 * 60 * MINUTO);
    actualizarSensor(contenedor, AHORA);
    expect(contenedor.nivelEstimado).toBeGreaterThan(contenedor.llenado);
    expect(contenedor.nivelEstimado).toBeLessThanOrEqual(100);
  });

  it('si un chofer lo vació después de la última lectura, se estima desde la recolección', () => {
    const contenedor = contenedorCon(Array(73).fill(85), AHORA - 5 * 60 * MINUTO);
    contenedor.ultimaRecoleccionMs = AHORA - 60 * MINUTO;
    actualizarSensor(contenedor, AHORA);
    expect(nivelEstimado(contenedor, AHORA)).toBeLessThan(20);
  });

  it('con el sensor funcionando no se estima nada', () => {
    const contenedor = contenedorCon(subiendo);
    actualizarSensor(contenedor, AHORA);
    expect(contenedor.nivelEstimado).toBeUndefined();
  });

  it('el simulador arranca con sensores fallados y el sistema los detecta', () => {
    const estado = crearEstadoInicial();
    const fallados = estado.contenedores.filter((c) => c.fallaSimulada !== 'ninguna');
    expect(fallados.length).toBeGreaterThan(0);
    fallados.forEach((c) => {
      expect(['sin_senal', 'congelado']).toContain(c.estadoSensor);
      expect(c.nivelEstimado).toBeDefined();
    });
    const resumen = resumenSensores(estado.contenedores);
    expect(resumen.porcentajeOperativo).toBeGreaterThan(80);
    expect(resumen.porcentajeOperativo).toBeLessThan(100);
  });

  it('cada sensor arranca con un día de lecturas para la curva', () => {
    const estado = crearEstadoInicial();
    const sano = estado.contenedores.find((c) => c.fallaSimulada === 'ninguna');
    expect(sano?.historial.length).toBe(24 * 12);
  });
});

describe('historial', () => {
  const historial = generarHistorial(crearContenedores(2026));

  it('la ruta dinámica recorre menos km que la fija', () => {
    const fija = historial.dias.reduce((s, d) => s + d.kmFija, 0);
    const dinamica = historial.dias.reduce((s, d) => s + d.kmDinamica, 0);
    expect(dinamica).toBeLessThan(fija);
  });

  it('la ruta dinámica tiene menos desbordes que la fija', () => {
    const fija = historial.semanas.reduce((s, x) => s + x.desbordesFija, 0);
    const dinamica = historial.semanas.reduce((s, x) => s + x.desbordesDinamica, 0);
    expect(dinamica).toBeLessThan(fija);
  });

  it('el índice de eficiencia de cada zona está entre 0 y 100', () => {
    Object.values(historial.eficienciaPorZona).forEach((indice) => {
      expect(indice).toBeGreaterThan(0);
      expect(indice).toBeLessThanOrEqual(100);
    });
  });

  it('genera 30 días y 8 semanas', () => {
    expect(historial.dias).toHaveLength(30);
    expect(historial.semanas).toHaveLength(8);
  });
});

describe('indicadores del día', () => {
  it('acumula km evitados al planificar las rutas', () => {
    const estado = crearEstadoInicial();
    expect(estado.kmEvitadosHoy).toBeGreaterThan(0);
  });

  it('el índice de eficiencia de cada zona está entre 0 y 100', () => {
    const historial = generarHistorial(crearContenedores(2026));
    Object.values(historial.eficienciaPorZona).forEach((indice) => {
      expect(indice).toBeGreaterThan(0);
      expect(indice).toBeLessThanOrEqual(100);
    });
  });
});

describe('zonas', () => {
  it('ubica lugares conocidos en la zona correcta', () => {
    expect(zonaDeUbicacion({ lat: -34.6037, lng: -58.3816 })?.id).toBe(1); // Obelisco
    expect(zonaDeUbicacion({ lat: -34.5813, lng: -58.421 })?.id).toBe(2); // Plaza Italia
    expect(zonaDeUbicacion({ lat: -34.6006, lng: -58.5133 })?.id).toBe(3); // Plaza Arenales, Devoto
    expect(zonaDeUbicacion({ lat: -34.6354, lng: -58.5207 })?.id).toBe(4); // Estadio de Vélez
    expect(zonaDeUbicacion({ lat: -34.664, lng: -58.456 })?.id).toBe(5); // Parque Indoamericano
    expect(zonaDeUbicacion({ lat: -34.6066, lng: -58.4357 })?.id).toBe(6); // Parque Centenario
    expect(zonaDeUbicacion({ lat: -34.6393, lng: -58.3626 })?.id).toBe(7); // Caminito
  });

  it('un punto fuera de la Ciudad no pertenece a ninguna zona', () => {
    expect(zonaDeUbicacion({ lat: -34.66, lng: -58.56 })).toBeUndefined(); // Ramos Mejía
  });

  it('la base de cada zona queda dentro de su zona', () => {
    ZONAS.forEach((zona) => {
      expect(zonaDeUbicacion(zona.base)?.id).toBe(zona.id);
    });
  });

  it('cada contenedor está dentro de la zona que tiene asignada', () => {
    crearContenedores(2026).forEach((contenedor) => {
      expect(zonaDeUbicacion(contenedor.posicion)?.id).toBe(contenedor.zonaId);
    });
  });
});
