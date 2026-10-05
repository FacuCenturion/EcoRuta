import { COLOR_NIVEL, ETIQUETA_NIVEL } from '../utils/colores';

// Referencias de colores del mapa
export default function Leyenda({ conCamion = false }: { conCamion?: boolean }) {
  return (
    <ul className="leyenda">
      {(Object.keys(COLOR_NIVEL) as (keyof typeof COLOR_NIVEL)[]).map((nivel) => (
        <li key={nivel}>
          <span className="leyenda__punto" style={{ background: COLOR_NIVEL[nivel] }} />
          {ETIQUETA_NIVEL[nivel]}
        </li>
      ))}
      {conCamion && (
        <li>
          <span className="leyenda__cuadrado" />
          Camión
        </li>
      )}
    </ul>
  );
}
