import { useState } from 'react';
import Encabezado from '../components/Encabezado';
import { ETIQUETA_ESTADO, ETIQUETA_TIPO, FilaIncidente } from '../components/ParaRevisar';
import { useSimulacion } from '../context/SimulacionContext';
import type { EstadoIncidente, TipoIncidente } from '../types';

export default function Incidentes() {
  const { estado, zonaFiltro } = useSimulacion();
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoIncidente | 'todos'>('todos');
  const [tipoFiltro, setTipoFiltro] = useState<TipoIncidente | 'todos'>('todos');

  const lista = estado.incidentes.filter(
    (i) =>
      (zonaFiltro === 'todas' || i.zonaId === zonaFiltro) &&
      (estadoFiltro === 'todos' || i.estado === estadoFiltro) &&
      (tipoFiltro === 'todos' || i.tipo === tipoFiltro),
  );

  return (
    <>
      <Encabezado
        titulo="Incidentes"
        subtitulo="Alertas del sistema, avisos de los choferes y reportes de vecinos. Nada se despacha sin tu confirmación."
      />
      <section className="panel">
        <div className="barra-filtros">
          <label>
            Estado{' '}
            <select value={estadoFiltro} onChange={(e) => setEstadoFiltro(e.target.value as EstadoIncidente | 'todos')}>
              <option value="todos">Todos</option>
              {(Object.keys(ETIQUETA_ESTADO) as EstadoIncidente[]).map((e) => (
                <option key={e} value={e}>
                  {ETIQUETA_ESTADO[e]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Tipo{' '}
            <select value={tipoFiltro} onChange={(e) => setTipoFiltro(e.target.value as TipoIncidente | 'todos')}>
              <option value="todos">Todos</option>
              {(Object.keys(ETIQUETA_TIPO) as TipoIncidente[]).map((t) => (
                <option key={t} value={t}>
                  {ETIQUETA_TIPO[t]}
                </option>
              ))}
            </select>
          </label>
          <span className="nota">{lista.length} incidentes</span>
        </div>
        {lista.length === 0 ? (
          <p className="vacio">No hay incidentes con estos filtros.</p>
        ) : (
          <ul className="revisar">
            {lista.map((incidente) => (
              <FilaIncidente key={incidente.id} incidente={incidente} />
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
