// Simulador de sensores y camiones.
// Cada llamada a avanzar() hace pasar MINUTOS_POR_TICK minutos simulados:
// los contenedores se llenan, los sensores informan su nivel, se detectan
// anomalías y los camiones van recolectando sus rutas.
import {
  CARGA_POR_PARADA,
  HORA_INICIAL,
  MAX_PARADAS_POR_RUTA,
  MINUTOS_POR_PARADA,
  MINUTOS_POR_TICK,
  MIN_PARADAS_PARA_SALIR,
  PROBABILIDAD_NO_SE_PUDO,
  TICKS_DE_DESCARGA,
  UMBRAL_DESBORDE,
  VELOCIDAD_CAMION_KMH,
} from '../config';
import { crearCamiones, crearContenedores } from '../data/semilla';
import { buscarZona } from '../data/zonas';
import { detectarAnomalia } from '../logic/anomalias';
import { diaDeSemana, incrementoPorHora } from '../logic/llenado';
import { lecturaConfiable, motivoDelContenedor } from '../logic/prioridad';
import { kmRutaFija } from '../logic/indicadores';
import { distanciaKm, planificarRuta } from '../logic/rutas';
import type { Camion, Contenedor, EstadoIncidente, EstadoSimulacion, Incidente } from '../types';
import { crearAleatorio, elegir, entre } from '../utils/aleatorio';

const SEMILLA_POR_DEFECTO = 2026;
const MS_POR_MINUTO = 60000;

const MOTIVOS_NO_SE_PUDO = [
  'Auto estacionado',
  'Obra o calle cortada',
  'Contenedor dañado',
  'Basura fuera del contenedor',
  'Sensor marca mal',
];

const COMENTARIOS_VECINALES = [
  'Hay bolsas tiradas al lado del contenedor.',
  'El contenedor está lleno y huele mal.',
  'Se desbordó y no entra más basura.',
  'Hay residuos desparramados en la vereda.',
];

function limitar(valor: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, valor));
}

function horaYDia(relojMs: number) {
  const fecha = new Date(relojMs);
  return { hora: fecha.getHours(), dia: diaDeSemana(fecha) };
}

function buscarContenedor(estado: EstadoSimulacion, id: string): Contenedor {
  const contenedor = estado.contenedores.find((c) => c.id === id);
  if (!contenedor) throw new Error(`No existe el contenedor ${id}`);
  return contenedor;
}

// ---------- Incidentes ----------

function agregarIncidente(
  estado: EstadoSimulacion,
  datos: Pick<Incidente, 'tipo' | 'contenedorId' | 'descripcion' | 'sugerencia' | 'camionNumero' | 'origen'>,
) {
  const contenedor = buscarContenedor(estado, datos.contenedorId);
  estado.contadorIncidentes += 1;
  estado.incidentes.unshift({
    ...datos,
    id: `INC-${String(estado.contadorIncidentes).padStart(4, '0')}`,
    estado: 'pendiente',
    zonaId: contenedor.zonaId,
    direccion: contenedor.direccion,
    creadoMs: estado.relojMs,
  });
}

function registrarReporteVecinal(estado: EstadoSimulacion, contenedorId: string, comentario: string) {
  const contenedor = buscarContenedor(estado, contenedorId);
  contenedor.reportesVecinales += 1;
  agregarIncidente(estado, {
    tipo: 'reporte_vecinal',
    contenedorId,
    descripcion: comentario,
    sugerencia: 'Enviar cuadrilla de limpieza',
    origen: 'Boti',
  });
}

// ---------- Camiones ----------

function planificarCamion(estado: EstadoSimulacion, camion: Camion) {
  const zona = buscarZona(camion.zonaId);
  const { hora, dia } = horaYDia(estado.relojMs);

  const candidatos = estado.contenedores
    .filter((c) => c.zonaId === camion.zonaId && motivoDelContenedor(c, hora, dia) !== null)
    .sort((a, b) => lecturaConfiable(b) - lecturaConfiable(a));

  const hayDesbordado = candidatos.some((c) => lecturaConfiable(c) >= UMBRAL_DESBORDE);
  if (candidatos.length < MIN_PARADAS_PARA_SALIR && !hayDesbordado) return;

  const elegidos = candidatos.slice(0, MAX_PARADAS_POR_RUTA);
  const ruta = planificarRuta(
    zona.base,
    elegidos.map((c) => ({ id: c.id, posicion: c.posicion })),
  );

  let reloj = estado.relojMs;
  let posicion = zona.base;
  camion.paradas = ruta.orden.map((id) => {
    const contenedor = buscarContenedor(estado, id);
    const viajeMin = (distanciaKm(posicion, contenedor.posicion) / VELOCIDAD_CAMION_KMH) * 60;
    reloj += (viajeMin + MINUTOS_POR_PARADA) * MS_POR_MINUTO;
    posicion = contenedor.posicion;
    return { contenedorId: id, estado: 'pendiente' as const, etaMs: reloj };
  });
  camion.estado = 'en_ruta';
  camion.kmPlanificados = ruta.km;
  camion.cargaPct = 0;
  camion.minutosDisponibles = 0;
  camion.posicion = zona.base;
  estado.planificadosHoy += ruta.orden.length;
  estado.kmEvitadosHoy += Math.max(0, kmRutaFija(estado, camion.zonaId) - ruta.km);
}

function recolectar(estado: EstadoSimulacion, contenedor: Contenedor, azar: () => number) {
  const nivel = Math.round(entre(azar, 2, 6));
  contenedor.nivelReal = nivel;
  contenedor.llenado = nivel;
  contenedor.lecturas = [...contenedor.lecturas.slice(-7), nivel];
  contenedor.ultimaLecturaMs = estado.relojMs;
  contenedor.anomalia = false;
  contenedor.irregularHasta = 0;
  contenedor.reportesVecinales = 0;
}

function avanzarCamion(estado: EstadoSimulacion, camion: Camion, azar: () => number) {
  const zona = buscarZona(camion.zonaId);

  if (camion.estado === 'en_base') {
    planificarCamion(estado, camion);
    return;
  }

  if (camion.estado === 'en_descarga') {
    camion.ticksDescarga -= 1;
    if (camion.ticksDescarga <= 0) {
      camion.estado = 'en_base';
      camion.cargaPct = 0;
    }
    return;
  }

  if (camion.estado === 'demorado') {
    camion.estado = 'en_ruta';
    return;
  }

  // El camión está en ruta: gasta el tiempo de este tick yendo de parada en parada.
  camion.minutosDisponibles += MINUTOS_POR_TICK;
  for (;;) {
    const parada = camion.paradas.find((p) => p.estado === 'pendiente');
    if (!parada) break;

    const contenedor = buscarContenedor(estado, parada.contenedorId);
    const distancia = distanciaKm(camion.posicion, contenedor.posicion);
    const costoMin = (distancia / VELOCIDAD_CAMION_KMH) * 60 + MINUTOS_POR_PARADA;
    if (camion.minutosDisponibles < costoMin) break;

    camion.minutosDisponibles -= costoMin;
    camion.kmRecorridosHoy += distancia;
    camion.posicion = contenedor.posicion;

    if (azar() < PROBABILIDAD_NO_SE_PUDO) {
      const motivo = elegir(azar, MOTIVOS_NO_SE_PUDO);
      parada.estado = 'no_se_pudo';
      parada.motivo = motivo;
      agregarIncidente(estado, {
        tipo: 'no_se_pudo',
        contenedorId: contenedor.id,
        descripcion: `El chofer reportó: ${motivo.toLowerCase()}.`,
        sugerencia: 'Sumar a la próxima ronda',
        camionNumero: camion.numero,
      });
      camion.estado = 'demorado';
      return;
    }

    recolectar(estado, contenedor, azar);
    parada.estado = 'recolectada';
    parada.horaRealMs = estado.relojMs;
    camion.cargaPct = Math.min(100, camion.cargaPct + CARGA_POR_PARADA);
    estado.recolectadosHoy += 1;
  }

  const quedanPendientes = camion.paradas.some((p) => p.estado === 'pendiente');
  if (!quedanPendientes) {
    camion.kmRecorridosHoy += distanciaKm(camion.posicion, zona.base);
    camion.posicion = zona.base;
    camion.estado = 'en_descarga';
    camion.ticksDescarga = TICKS_DE_DESCARGA;
    camion.minutosDisponibles = 0;
  }
}

// ---------- Estado inicial ----------

export function crearEstadoInicial(semilla: number = SEMILLA_POR_DEFECTO): EstadoSimulacion {
  const inicioDia = new Date();
  inicioDia.setHours(0, 0, 0, 0);
  const relojMs = inicioDia.getTime() + HORA_INICIAL * 60 * MS_POR_MINUTO;

  const estado: EstadoSimulacion = {
    semilla,
    tick: 0,
    relojMs,
    inicioDiaMs: inicioDia.getTime(),
    contenedores: crearContenedores(semilla),
    camiones: crearCamiones(),
    incidentes: [],
    recolectadosHoy: 0,
    planificadosHoy: 0,
    kmEvitadosHoy: 0,
    contadorIncidentes: 0,
  };
  estado.contenedores.forEach((c) => {
    c.ultimaLecturaMs = relojMs;
  });

  // Dos reportes de vecinos y un sensor con lecturas raras, para que el panel
  // no arranque vacío.
  registrarReporteVecinal(estado, 'Z2-04', 'Hay bolsas tiradas al lado del contenedor.');
  registrarReporteVecinal(estado, 'Z3-07', 'El contenedor está lleno y huele mal.');
  const sospechoso = buscarContenedor(estado, 'Z2-11');
  sospechoso.nivelReal = 55;
  sospechoso.lecturas = [52, 61, 49, 62, 48, 60];
  sospechoso.llenado = 60;
  sospechoso.anomalia = true;
  sospechoso.irregularHasta = 6;
  agregarIncidente(estado, {
    tipo: 'llenado_irregular',
    contenedorId: sospechoso.id,
    descripcion: 'El nivel sube y baja en pocos minutos: posible persona revolviendo.',
    sugerencia: 'Atención social (108)',
  });

  estado.camiones.forEach((camion) => planificarCamion(estado, camion));
  return estado;
}

// ---------- Avance del tiempo ----------

export function avanzar(anterior: EstadoSimulacion): EstadoSimulacion {
  const estado = structuredClone(anterior);
  const azar = crearAleatorio(estado.semilla + estado.tick + 1);

  const diaAnterior = new Date(estado.relojMs).getDate();
  estado.tick += 1;
  estado.relojMs += MINUTOS_POR_TICK * MS_POR_MINUTO;
  const fecha = new Date(estado.relojMs);

  // Cambio de día: se reinician los contadores diarios
  if (fecha.getDate() !== diaAnterior) {
    estado.inicioDiaMs = new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate()).getTime();
    estado.recolectadosHoy = 0;
    estado.planificadosHoy = 0;
    estado.kmEvitadosHoy = 0;
    estado.camiones.forEach((c) => {
      c.kmRecorridosHoy = 0;
    });
  }

  const { hora, dia } = horaYDia(estado.relojMs);

  // 1) Los contenedores se llenan y los sensores informan
  for (const contenedor of estado.contenedores) {
    const incremento =
      incrementoPorHora(contenedor.velocidad, hora, dia) * (MINUTOS_POR_TICK / 60) * entre(azar, 0.7, 1.3);
    contenedor.nivelReal = Math.min(100, contenedor.nivelReal + incremento);

    // De vez en cuando alguien revuelve la basura y el sensor se vuelve irregular
    const nivel = contenedor.nivelReal;
    if (contenedor.irregularHasta <= estado.tick && nivel > 30 && nivel < 85 && azar() < 0.0005) {
      contenedor.irregularHasta = estado.tick + 8;
    }

    let lectura = contenedor.nivelReal;
    if (contenedor.irregularHasta > estado.tick) {
      lectura += (estado.tick % 2 === 0 ? 1 : -1) * entre(azar, 6, 12);
    }
    contenedor.llenado = Math.round(limitar(lectura, 0, 100));
    contenedor.lecturas = [...contenedor.lecturas.slice(-7), contenedor.llenado];
    contenedor.ultimaLecturaMs = estado.relojMs;

    // 2) Detección de anomalías
    const irregular = detectarAnomalia(contenedor.lecturas);
    if (irregular && !contenedor.anomalia) {
      contenedor.anomalia = true;
      agregarIncidente(estado, {
        tipo: 'llenado_irregular',
        contenedorId: contenedor.id,
        descripcion: 'El nivel sube y baja en pocos minutos: posible persona revolviendo.',
        sugerencia: 'Atención social (108)',
      });
    } else if (!irregular && contenedor.anomalia && contenedor.irregularHasta <= estado.tick) {
      contenedor.anomalia = false;
    }
  }

  // 3) A veces entra un reporte de un vecino por Boti
  const sinReporte = estado.contenedores.filter((c) => c.reportesVecinales === 0);
  if (azar() < 0.04 && sinReporte.length > 0) {
    registrarReporteVecinal(estado, elegir(azar, sinReporte).id, elegir(azar, COMENTARIOS_VECINALES));
  }

  // 4) Los camiones avanzan en sus rutas
  estado.camiones.forEach((camion) => avanzarCamion(estado, camion, azar));

  // 5) Se cierran solos los incidentes que ya se resolvieron
  for (const incidente of estado.incidentes) {
    if (incidente.estado !== 'pendiente' && incidente.estado !== 'confirmado') continue;
    const contenedor = buscarContenedor(estado, incidente.contenedorId);
    const vaciado = contenedor.nivelReal < 20;
    const sensorNormal = !contenedor.anomalia;
    if (incidente.tipo !== 'llenado_irregular' && vaciado) incidente.estado = 'resuelto';
    if (incidente.tipo === 'llenado_irregular' && sensorNormal) {
      // Si el sensor volvió a la normalidad y pasaron más de 2 horas, la alerta se cierra sola
      const antiguedadMin = (estado.relojMs - incidente.creadoMs) / MS_POR_MINUTO;
      if (incidente.estado === 'confirmado' || antiguedadMin > 120) incidente.estado = 'resuelto';
    }
  }

  return estado;
}

// ---------- Acciones del operador ----------

export function decidirIncidente(
  anterior: EstadoSimulacion,
  incidenteId: string,
  decision: Extract<EstadoIncidente, 'confirmado' | 'descartado'>,
): EstadoSimulacion {
  const estado = structuredClone(anterior);
  const incidente = estado.incidentes.find((i) => i.id === incidenteId);
  if (!incidente || incidente.estado !== 'pendiente') return anterior;

  incidente.estado = decision;
  if (decision === 'descartado' && incidente.tipo === 'reporte_vecinal') {
    const contenedor = buscarContenedor(estado, incidente.contenedorId);
    contenedor.reportesVecinales = Math.max(0, contenedor.reportesVecinales - 1);
  }
  return estado;
}

export function agregarReporteVecinal(
  anterior: EstadoSimulacion,
  contenedorId: string,
  comentario: string,
): EstadoSimulacion {
  const estado = structuredClone(anterior);
  registrarReporteVecinal(estado, contenedorId, comentario.trim() || 'Sin comentario.');
  return estado;
}
