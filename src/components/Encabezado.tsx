import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { IconoCampana } from './Iconos';
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
  const {
    estado,
    zonaFiltro,
    cambiarZona,
    enPausa,
    alternarPausa,
    velocidad,
    cambiarVelocidad,
    conControles,
    areas,
    palabraArea,
    aviso,
  } = useSimulacion();
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
        {aviso && (
          <p className="login__error" role="alert">
            {aviso}
          </p>
        )}
      </div>
      <div className="encabezado__acciones">
        {conFiltroZona && (
          <select
            aria-label={`Filtrar por ${palabraArea.toLowerCase()}`}
            value={zonaFiltro}
            onChange={(e) => cambiarZona(e.target.value === 'todas' ? 'todas' : (Number(e.target.value) as FiltroZona))}
          >
            <option value="todas">{palabraArea === 'Zona' ? 'Todas las zonas' : 'Todos los sectores'}</option>
            {areas.map((area) => (
              <option key={area.id} value={area.id}>
                {palabraArea === 'Zona' ? area.nombre : `${palabraArea} ${area.id}: ${area.nombre}`}
              </option>
            ))}
          </select>
        )}
        {conSimulacion && conControles && (
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
