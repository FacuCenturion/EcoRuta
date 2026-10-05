import { useSimulacion } from '../context/SimulacionContext';
import { esAbierto } from '../logic/indicadores';
import type { Incidente } from '../types';
import { formatearHora } from '../utils/formato';
import { IconoIncidentes, IconoReportes, IconoSensor } from './Iconos';

export const ETIQUETA_TIPO: Record<Incidente['tipo'], string> = {
  llenado_irregular: 'Llenado irregular',
  no_se_pudo: 'No se pudo recolectar',
  reporte_vecinal: 'Reporte vecinal',
};

export const ETIQUETA_ESTADO: Record<Incidente['estado'], string> = {
  pendiente: 'Pendiente',
  confirmado: 'Confirmado',
  descartado: 'Descartado',
  resuelto: 'Resuelto',
};

const ICONO_TIPO: Record<Incidente['tipo'], JSX.Element> = {
  llenado_irregular: <IconoSensor />,
  no_se_pudo: <IconoIncidentes />,
  reporte_vecinal: <IconoReportes />,
};

export function FilaIncidente({ incidente }: { incidente: Incidente }) {
  const { decidir } = useSimulacion();
  return (
    <li className="revisar__item">
      <span className={`revisar__icono revisar__icono--${incidente.tipo}`}>{ICONO_TIPO[incidente.tipo]}</span>
      <div className="revisar__texto">
        <strong>{ETIQUETA_TIPO[incidente.tipo]}</strong>
        <span>
          {incidente.direccion}, zona {incidente.zonaId}
          {incidente.camionNumero ? `, camión ${incidente.camionNumero}` : ''}
        </span>
        <span className="revisar__descripcion">{incidente.descripcion}</span>
        <span className="etiqueta etiqueta--sugerencia">Sugerido: {incidente.sugerencia}</span>
      </div>
      <div className="revisar__acciones">
        {incidente.estado === 'pendiente' ? (
          <>
            <button type="button" className="boton" onClick={() => decidir(incidente.id, 'descartado')}>
              Descartar
            </button>
            <button type="button" className="boton boton--primario" onClick={() => decidir(incidente.id, 'confirmado')}>
              Confirmar
            </button>
          </>
        ) : (
          <span className={`estado estado--${incidente.estado}`}>{ETIQUETA_ESTADO[incidente.estado]}</span>
        )}
        <small>{formatearHora(incidente.creadoMs)}</small>
      </div>
    </li>
  );
}

// Lista corta con los incidentes que esperan una decisión del operador
export default function ParaRevisar({ maximo = 4 }: { maximo?: number }) {
  const { estado, zonaFiltro } = useSimulacion();
  // Primero los que esperan una decisión, después los ya confirmados que siguen abiertos
  const abiertos = estado.incidentes
    .filter((i) => esAbierto(i.estado) && (zonaFiltro === 'todas' || i.zonaId === zonaFiltro))
    .sort((a, b) => Number(b.estado === 'pendiente') - Number(a.estado === 'pendiente'));
  const pendientes = abiertos.filter((i) => i.estado === 'pendiente');

  return (
    <section className="panel" aria-labelledby="titulo-revisar">
      <div className="panel__cabecera">
        <h2 id="titulo-revisar">Para revisar</h2>
        <span className="etiqueta">{pendientes.length} pendientes</span>
      </div>
      {abiertos.length === 0 ? (
        <p className="vacio">No hay incidentes abiertos.</p>
      ) : (
        <ul className="revisar">
          {abiertos.slice(0, maximo).map((incidente) => (
            <FilaIncidente key={incidente.id} incidente={incidente} />
          ))}
        </ul>
      )}
    </section>
  );
}
