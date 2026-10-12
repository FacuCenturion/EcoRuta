import { Area, ComposedChart, Line, ReferenceArea, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { HORAS_DE_HISTORIAL } from '../config';
import { curvaEstimada, ultimaLecturaValida } from '../logic/estadoSensor';
import type { Contenedor } from '../types';
import { formatearHora } from '../utils/formato';

interface Punto {
  ms: number;
  medido?: number;
  estimado?: number;
}

// Curva de llenado de las últimas 24 h. Si no se puede confiar en el sensor,
// el tramo sin datos buenos se sombrea y el nivel estimado va punteado.
export default function CurvaSensor({ contenedor, relojMs }: { contenedor: Contenedor; relojMs: number }) {
  const desde = relojMs - HORAS_DE_HISTORIAL * 60 * 60 * 1000;
  const conEstimacion = contenedor.nivelEstimado !== undefined;

  const puntos: Punto[] = contenedor.historial
    .filter((l) => l.ms >= desde)
    .map((l) => ({ ms: l.ms, medido: l.nivel }));
  if (conEstimacion) {
    curvaEstimada(contenedor, relojMs)
      .filter((l) => l.ms >= desde)
      .forEach((l) => puntos.push({ ms: l.ms, estimado: l.nivel }));
    puntos.sort((a, b) => a.ms - b.ms);
  }
  // El tramo gris arranca cuando el sensor dejó de dar datos buenos
  const ultimaBuena = ultimaLecturaValida(contenedor)?.ms ?? relojMs;
  const inicioSinDatos = conEstimacion ? Math.max(desde, ultimaBuena) : undefined;

  return (
    <div className="grafico grafico--sensor">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={puntos} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
          <XAxis
            dataKey="ms"
            type="number"
            scale="time"
            domain={[desde, relojMs]}
            tickFormatter={(ms) => formatearHora(ms)}
            tickCount={7}
            tick={{ fontSize: 11 }}
          />
          <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 11 }} />
          {inicioSinDatos !== undefined && (
            <ReferenceArea x1={inicioSinDatos} x2={relojMs} fill="#e4e1d6" fillOpacity={0.6} />
          )}
          <Area
            type="linear"
            dataKey="medido"
            name="Medido"
            stroke="#5f7a52"
            fill="#c9d6bd"
            fillOpacity={0.5}
            strokeWidth={1.5}
            dot={false}
            connectNulls
            isAnimationActive={false}
          />
          {conEstimacion && (
            <Line
              type="linear"
              dataKey="estimado"
              name="Estimado"
              stroke="#9b4a36"
              strokeDasharray="5 4"
              strokeWidth={2}
              dot={false}
              connectNulls
              isAnimationActive={false}
            />
          )}
          <Tooltip
            labelFormatter={(ms) => formatearHora(Number(ms))}
            formatter={(valor, nombre) => [`${valor}%`, nombre]}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
