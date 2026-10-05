import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { IconoCampana } from './Iconos';
import { ZONAS } from '../data/zonas';
import { useSimulacion, type FiltroZona } from '../context/SimulacionContext';
import { esAbierto } from '../logic/indicadores';
import { formatearFechaLarga, formatearHora } from '../utils/formato';

interface Props {
  titulo: string;
  subtitulo?: string;
  conFiltroZona?: boolean;
  conReloj?: boolean;
  conSimulacion?: boolean; // botones de pausa y velocidad
  acciones?: ReactNode; // botones propios de cada pantalla
}

export default function Encabezado({
  titulo,
  subtitulo,
  conFiltroZona = true,
  conReloj = true,
  conSimulacion = true,
  acciones,
}: Props) {
  const { estado, zonaFiltro, cambiarZona, enPausa, alternarPausa, velocidad, cambiarVelocidad } = useSimulacion();
  const pendientes = estado.incidentes.filter((i) => i.estado === 'pendiente').length;
  const abiertos = estado.incidentes.filter((i) => esAbierto(i.estado)).length;

  return (
    <header className="encabezado">
      <div>
        <h1>{titulo}</h1>
        <p className="encabezado__sub">
          {subtitulo ??
            (conReloj
              ? `${formatearFechaLarga(estado.relojMs)}, ${formatearHora(estado.relojMs)}. Datos de sensores simulados, actualizados a las ${formatearHora(estado.relojMs)}.`
              : '')}
        </p>
      </div>
      <div className="encabezado__acciones">
        {conFiltroZona && (
          <select
            aria-label="Filtrar por zona"
            value={zonaFiltro}
            onChange={(e) => cambiarZona(e.target.value === 'todas' ? 'todas' : (Number(e.target.value) as FiltroZona))}
          >
            <option value="todas">Todas las zonas</option>
            {ZONAS.map((zona) => (
              <option key={zona.id} value={zona.id}>
                Zona {zona.id}
              </option>
            ))}
          </select>
        )}
        {conSimulacion && (
          <div className="control-tiempo" role="group" aria-label="Control de la simulación">
            <button type="button" className="boton" onClick={alternarPausa}>
              {enPausa ? 'Reanudar' : 'Pausar'}
            </button>
            {[1, 2, 4].map((v) => (
              <button
                key={v}
                type="button"
                className={v === velocidad ? 'boton boton--activo' : 'boton'}
                onClick={() => cambiarVelocidad(v)}
                aria-pressed={v === velocidad}
              >
                x{v}
              </button>
            ))}
          </div>
        )}
        {acciones}
        <Link
          to="/incidentes"
          className="campana"
          aria-label={`${pendientes} incidentes esperan confirmación, ${abiertos} abiertos`}
        >
          <IconoCampana />
          {pendientes > 0 && <span className="campana__numero">{pendientes}</span>}
        </Link>
      </div>
    </header>
  );
}
