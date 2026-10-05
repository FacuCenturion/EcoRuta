import { etiquetaMotivo, type MotivoPrioridad } from '../../logic/prioridad';
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
  return (
    <div className="info-mapa">
      <strong>{contenedor.direccion}</strong>
      <br />
      Zona {contenedor.zonaId} · {contenedor.id}
      <br />
      Llenado: {Math.round(contenedor.llenado)}% ({ETIQUETA_NIVEL[nivel]})
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
      {contenedor.anomalia && (
        <>
          <br />
          Lecturas irregulares del sensor
        </>
      )}
    </div>
  );
}
