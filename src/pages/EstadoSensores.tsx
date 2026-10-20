import { useEffect, useState } from 'react';
import CurvaSensor from '../components/CurvaSensor';
import Encabezado from '../components/Encabezado';
import { useSimulacion, type Curva } from '../context/SimulacionContext';
import { DETALLE_ESTADO_SENSOR, ETIQUETA_ESTADO_SENSOR, resumenSensores } from '../logic/estadoSensor';
import { lecturaConfiable, textoDeDesborde } from '../logic/prioridad';
import type { Contenedor, EstadoSensor } from '../types';
import { COLOR_ESTADO_SENSOR } from '../utils/colores';
import { formatearHaceCuanto, formatearHora } from '../utils/formato';

type Filtro = 'con_falla' | 'todos' | EstadoSensor;

// Orden de la lista: primero lo más grave
const ESTADOS: EstadoSensor[] = ['sin_senal', 'trabado', 'congelado', 'irregular', 'funcionando'];

const QUE_HACER: Record<EstadoSensor, string> = {
  sin_senal:
    'Pedirle a la empresa de la zona que revise el sensor. Mientras tanto el contenedor se prioriza con el nivel estimado.',
  congelado:
    'Pedir revisión del sensor. Si el chofer vacía el contenedor y el valor no cambia, queda confirmado que está trabado.',
  trabado:
    'Pedir revisión del sensor: el chofer confirmó que lo vació y el sensor no se enteró. Se prioriza con el nivel estimado.',
  irregular: 'El sensor anda, pero alguien puede estar revolviendo la basura. Revisar el incidente.',
  funcionando: 'No hay nada pendiente con este sensor.',
};

function coincide(contenedor: Contenedor, filtro: Filtro): boolean {
  if (filtro === 'todos') return true;
  if (filtro === 'con_falla') return contenedor.estadoSensor !== 'funcionando';
  return contenedor.estadoSensor === filtro;
}

function EstadoChip({ estado }: { estado: EstadoSensor }) {
  return <span className={`estado estado-sensor--${estado}`}>{ETIQUETA_ESTADO_SENSOR[estado]}</span>;
}

export default function EstadoSensores() {
  const { estado, zonaFiltro, palabraArea, nombreArea, pedirCurva, fuente } = useSimulacion();
  // El simulador del navegador no tiene sensores trabados: el botón aparece solo con backend
  const estados = fuente === 'api' ? ESTADOS : ESTADOS.filter((e) => e !== 'trabado');
  const [filtro, setFiltro] = useState<Filtro>('con_falla');
  const [elegidoId, setElegidoId] = useState<string>();

  const contenedores = estado.contenedores.filter((c) => zonaFiltro === 'todas' || c.areaId === zonaFiltro);
  const resumen = resumenSensores(contenedores);
  const conFalla = resumen.total - resumen.porEstado.funcionando;
  const alcance =
    zonaFiltro === 'todas'
      ? palabraArea === 'Zona'
        ? 'de la Ciudad'
        : 'de la zona 1'
      : palabraArea === 'Zona'
        ? `de la zona ${zonaFiltro}`
        : `del sector ${zonaFiltro}`;

  const lista = contenedores
    .filter((c) => coincide(c, filtro))
    .sort(
      (a, b) =>
        ESTADOS.indexOf(a.estadoSensor) - ESTADOS.indexOf(b.estadoSensor) || a.ultimaLecturaMs - b.ultimaLecturaMs,
    );
  const elegido = lista.find((c) => c.id === elegidoId) ?? lista[0];

  // Con backend, la curva de 24 h se pide solo para el sensor elegido, y de nuevo cada vez que hay datos nuevos
  const [curva, setCurva] = useState<{ id: string } & Curva>();
  useEffect(() => {
    if (!pedirCurva || !elegido) return;
    let vigente = true;
    pedirCurva(elegido.id)
      .then((nueva) => vigente && setCurva({ id: elegido.id, ...nueva }))
      .catch(() => vigente && setCurva(undefined));
    return () => {
      vigente = false;
    };
  }, [pedirCurva, elegido?.id, estado.relojMs]);
  const curvaElegida = curva && curva.id === elegido?.id ? curva : undefined;

  return (
    <>
      <Encabezado
        titulo="Estado de sensores"
        subtitulo="Los sensores solo informan el nivel de llenado. Las fallas se detectan por cómo cambian, o dejan de cambiar, esas lecturas."
      />

      <section className="panel sensores-resumen" aria-labelledby="titulo-resumen-sensores">
        <div className="sensores-resumen__cifra">
          <strong>{resumen.porcentajeOperativo}%</strong>
          <span id="titulo-resumen-sensores">de los sensores {alcance} están midiendo</span>
        </div>

        <div
          className="barra-estados"
          role="img"
          aria-label={`${resumen.total} sensores, ${conFalla} con alguna falla`}
        >
          {estados
            .slice()
            .reverse()
            .map((e) =>
              resumen.porEstado[e] > 0 ? (
                <span
                  key={e}
                  style={{
                    width: `${(resumen.porEstado[e] / resumen.total) * 100}%`,
                    background: COLOR_ESTADO_SENSOR[e],
                  }}
                />
              ) : null,
            )}
        </div>

        <div className="filtro-estados" role="group" aria-label="Filtrar por estado del sensor">
          <button
            type="button"
            className={filtro === 'con_falla' ? 'filtro-estado filtro-estado--activo' : 'filtro-estado'}
            onClick={() => setFiltro('con_falla')}
            aria-pressed={filtro === 'con_falla'}
          >
            Con alguna falla <strong>{conFalla}</strong>
          </button>
          {estados.map((e) => (
            <button
              key={e}
              type="button"
              className={filtro === e ? 'filtro-estado filtro-estado--activo' : 'filtro-estado'}
              onClick={() => setFiltro(e)}
              aria-pressed={filtro === e}
            >
              <span className="leyenda__punto" style={{ background: COLOR_ESTADO_SENSOR[e] }} />
              {ETIQUETA_ESTADO_SENSOR[e]} <strong>{resumen.porEstado[e]}</strong>
            </button>
          ))}
          <button
            type="button"
            className={filtro === 'todos' ? 'filtro-estado filtro-estado--activo' : 'filtro-estado'}
            onClick={() => setFiltro('todos')}
            aria-pressed={filtro === 'todos'}
          >
            Todos <strong>{resumen.total}</strong>
          </button>
        </div>
      </section>

      <div className="sensores">
        <section className="panel" aria-labelledby="titulo-lista-sensores">
          <div className="panel__cabecera">
            <h2 id="titulo-lista-sensores">Sensores</h2>
            <span className="nota">{lista.length} en la lista</span>
          </div>
          {lista.length === 0 ? (
            <p className="vacio">No hay sensores en este estado. Probá con otro filtro o con otra zona.</p>
          ) : (
            <div className="tabla-scroll">
              <table className="tabla tabla--sensores">
                <thead>
                  <tr>
                    <th>Contenedor</th>
                    <th>{palabraArea}</th>
                    <th>Estado</th>
                    <th>Última lectura</th>
                    <th>Nivel</th>
                  </tr>
                </thead>
                <tbody>
                  {lista.map((c) => (
                    <tr key={c.id} className={c.id === elegido?.id ? 'tabla__fila--activa' : undefined}>
                      <th scope="row">
                        <button
                          type="button"
                          className="tabla__boton"
                          onClick={() => setElegidoId(c.id)}
                          aria-pressed={c.id === elegido?.id}
                        >
                          <strong>{c.direccion}</strong>
                          <small>{c.id}</small>
                        </button>
                      </th>
                      <td>{c.areaId}</td>
                      <td>
                        <EstadoChip estado={c.estadoSensor} />
                      </td>
                      <td>{formatearHaceCuanto(c.ultimaLecturaMs, estado.relojMs)}</td>
                      <td>
                        {Math.round(lecturaConfiable(c))}%
                        {c.nivelEstimado !== undefined && <small className="nivel-estimado">estimado</small>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {elegido && (
          <section className="panel detalle-sensor" aria-labelledby="titulo-detalle-sensor">
            <div className="panel__cabecera">
              <div>
                <h2 id="titulo-detalle-sensor">{elegido.direccion}</h2>
                <span className="nota">
                  {nombreArea(elegido.areaId)}, contenedor {elegido.id}
                </span>
              </div>
              <EstadoChip estado={elegido.estadoSensor} />
            </div>

            <p className="detalle-sensor__motivo">{DETALLE_ESTADO_SENSOR[elegido.estadoSensor]}.</p>

            {pedirCurva && !curvaElegida ? (
              <p className="nota grafico grafico--sensor">Cargando la curva del sensor…</p>
            ) : (
              <CurvaSensor
                contenedor={curvaElegida ? { ...elegido, historial: curvaElegida.historial } : elegido}
                estimadas={curvaElegida?.estimadas}
                relojMs={estado.relojMs}
              />
            )}
            <ul className="leyenda">
              <li>
                <span className="leyenda__cuadrito" style={{ background: '#c9d6bd' }} />
                Lo que informó el sensor
              </li>
              {elegido.nivelEstimado !== undefined && (
                <>
                  <li>
                    <span className="leyenda__linea leyenda__linea--estimada" />
                    Nivel estimado
                  </li>
                  <li>
                    <span className="leyenda__cuadrito" style={{ background: '#e4e1d6' }} />
                    Sin datos confiables
                  </li>
                </>
              )}
            </ul>

            <dl className="datos-sensor">
              <div>
                <dt>Último dato</dt>
                <dd>
                  {elegido.llenado}% a las {formatearHora(elegido.ultimaLecturaMs)}
                </dd>
              </div>
              <div>
                <dt>Nivel con el que se prioriza</dt>
                <dd>
                  {Math.round(lecturaConfiable(elegido))}%{elegido.nivelEstimado !== undefined && ' (estimado)'}
                </dd>
              </div>
              <div>
                <dt>Última recolección confirmada</dt>
                <dd>
                  {elegido.ultimaRecoleccionMs
                    ? `${formatearHora(elegido.ultimaRecoleccionMs)}, ${formatearHaceCuanto(elegido.ultimaRecoleccionMs, estado.relojMs)}`
                    : 'Sin datos'}
                </dd>
              </div>
              {textoDeDesborde(elegido, estado.relojMs) && (
                <div>
                  <dt>Pronóstico</dt>
                  <dd>{textoDeDesborde(elegido, estado.relojMs)}</dd>
                </div>
              )}
            </dl>

            <p className="detalle-sensor__accion">
              <strong>Qué hacer: </strong>
              {QUE_HACER[elegido.estadoSensor]}
            </p>
          </section>
        )}
      </div>
    </>
  );
}
