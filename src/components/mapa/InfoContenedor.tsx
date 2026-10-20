import { useSimulacion } from '../../context/SimulacionContext';
import { ETIQUETA_ESTADO_SENSOR } from '../../logic/estadoSensor';
import { etiquetaMotivo, lecturaConfiable, textoDeDesborde, type MotivoPrioridad } from '../../logic/prioridad';
import type { Contenedor, NivelLlenado } from '../../types';
import { ETIQUETA_NIVEL } from '../../utils/colores';
import { formatearHora } from '../../utils/formato';

interface Props {
  contenedor: Contenedor;
  nivel: NivelLlenado;
  motivo: MotivoPrioridad | null;
}

// Contenido del globo que se abre al tocar un contenedor en el mapa
export default function InfoContenedor({ contenedor, nivel, motivo }: Props) {
  const { nombreArea, estado } = useSimulacion();
  const desborde = textoDeDesborde(contenedor, estado.relojMs);
  return (
    <div className="info-mapa">
      <strong>{contenedor.direccion}</strong>
      <br />
      {nombreArea(contenedor.areaId)}
      {contenedor.barrio ? `, ${contenedor.barrio}` : ''} · {contenedor.id}
      <br />
      Llenado: {Math.round(lecturaConfiable(contenedor))}%{contenedor.nivelEstimado !== undefined ? ' estimado' : ''} (
      {ETIQUETA_NIVEL[nivel]})
      <br />
      Se llena a {contenedor.velocidad}% por hora
      <br />
      Última lectura: {formatearHora(contenedor.ultimaLecturaMs)}
      {motivo && (
        <>
          <br />
          Prioridad: {etiquetaMotivo(motivo)}
        </>
      )}
      {desborde && (
        <>
          <br />
          {desborde}
        </>
      )}
      {contenedor.estadoSensor !== 'funcionando' && (
        <>
          <br />
          Sensor: {ETIQUETA_ESTADO_SENSOR[contenedor.estadoSensor].toLowerCase()}
        </>
      )}
    </div>
  );
}
