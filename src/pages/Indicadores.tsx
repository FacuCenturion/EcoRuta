import {
  Area,
  Bar,
  BarChart,
  CartesianGrid,
  ComposedChart,
  Cell,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import Encabezado from '../components/Encabezado';
import Tarjeta from '../components/Tarjeta';
import { useSimulacion } from '../context/SimulacionContext';
import { useIndicadores } from '../context/useIndicadores';
import { kgCo2 } from '../logic/indicadores';
import { formatearDecimal, formatearFechaCorta, formatearNumero } from '../utils/formato';

const DIAS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
const HORAS_MAPA = [0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22];
const SEMANAS_SIN_ECORUTA = 2; // las primeras semanas se muestran con ruta fija, como en el diseño

function claseCalor(valor: number): string {
  if (valor < 25) return 'calor--0';
  if (valor < 45) return 'calor--1';
  if (valor < 65) return 'calor--2';
  if (valor < 80) return 'calor--3';
  return 'calor--4';
}

function descargarCsv(nombre: string, filas: (string | number)[][]) {
  const texto = filas.map((fila) => fila.join(';')).join('\n');
  const blob = new Blob(['\ufeff' + texto], { type: 'text/csv;charset=utf-8' });
  const enlace = document.createElement('a');
  enlace.href = URL.createObjectURL(blob);
  enlace.download = nombre;
  enlace.click();
  URL.revokeObjectURL(enlace.href);
}

export default function Indicadores() {
  const { zonaFiltro, areas, palabraArea, nombreArea, areaDestacada, fuente } = useSimulacion();
  const idZona = zonaFiltro === 'todas' ? undefined : zonaFiltro;
  const { datos: historial, error } = useIndicadores(idZona);

  if (!historial) {
    return (
      <>
        <Encabezado titulo="Indicadores" conSimulacion={false} />
        <p className={error ? 'login__error' : 'vacio'} role={error ? 'alert' : undefined}>
          {error ?? 'Calculando indicadores…'}
        </p>
      </>
    );
  }

  const datosKm = historial.dias.map((dia) => ({
    fecha: formatearFechaCorta(dia.fechaMs),
    dinamica: Math.round(dia.kmDinamica),
    fija: Math.round(dia.kmFija),
  }));
  const kmEvitados = datosKm.reduce((suma, d) => suma + (d.fija - d.dinamica), 0);

  // Desbordes por semana: las primeras semanas con ruta fija, el resto con ruta dinámica
  const datosSemanas = historial.semanas.map((semana, i) => ({
    etiqueta: semana.etiqueta,
    desbordes: i < SEMANAS_SIN_ECORUTA ? semana.desbordesFija : semana.desbordesDinamica,
    antes: i < SEMANAS_SIN_ECORUTA,
  }));
  const promedioAntes =
    datosSemanas.filter((s) => s.antes).reduce((suma, s) => suma + s.desbordes, 0) / SEMANAS_SIN_ECORUTA;
  const despues = datosSemanas.filter((s) => !s.antes);
  const promedioDespues = despues.reduce((suma, s) => suma + s.desbordes, 0) / despues.length;
  const variacionDesbordes = promedioAntes ? Math.round(((promedioDespues - promedioAntes) / promedioAntes) * 100) : 0;
  // Con backend no hay un "antes": las dos rutas se calculan semana a semana con las mismas lecturas
  const desbordesFija = historial.semanas.reduce((suma, s) => suma + s.desbordesFija, 0);
  const desbordesDinamica = historial.semanas.reduce((suma, s) => suma + s.desbordesDinamica, 0);
  const comparaLasDos = fuente === 'api';

  const eficiencia = areas
    .map((z) => ({ zona: nombreArea(z.id), indice: Math.round(historial.eficienciaPorArea[z.id] ?? 0) }))
    .sort((a, b) => b.indice - a.indice);
  const menorEficiencia = Math.min(...eficiencia.map((e) => e.indice));

  const zonaCalor = idZona ?? areaDestacada;
  const textoArea = (id: number) => nombreArea(id).toLowerCase();
  const deArea = (id: number) => (palabraArea === 'Zona' ? `de la zona ${id}` : `del sector ${id}`);
  const calor = historial.mapaCalor[zonaCalor] ?? [];

  function exportar() {
    descargarCsv('ecoruta-indicadores.csv', [
      ['Fecha', 'Km ruta dinamica', 'Km ruta fija estimada'],
      ...datosKm.map((d) => [d.fecha, d.dinamica, d.fija]),
      [],
      [palabraArea, 'Indice de eficiencia'],
      ...eficiencia.map((e) => [e.zona, e.indice]),
    ]);
  }

  return (
    <>
      <Encabezado
        titulo="Indicadores"
        subtitulo={
          fuente === 'api'
            ? 'Cómo rinde el servicio desde que las rutas son dinámicas, contra una ruta fija con las mismas lecturas. Las primeras semanas son historial simulado.'
            : 'Cómo rinde el servicio desde que las rutas son dinámicas. Valores estimados con datos simulados.'
        }
        conSimulacion={false}
        acciones={
          <>
            <select aria-label="Período" defaultValue="30">
              <option value="30">Últimos 30 días</option>
            </select>
            <button type="button" className="boton boton--primario" onClick={exportar}>
              Exportar informe
            </button>
          </>
        }
      />

      <section className="tarjetas tarjetas--cuatro" aria-label="Resumen de indicadores">
        <Tarjeta
          variante="oscura"
          titulo="Km evitados, 30 días"
          valor={`${formatearNumero(kmEvitados)} km`}
          detalle={
            idZona
              ? `contra la ruta fija ${deArea(idZona)}`
              : `contra la ruta fija de cada ${palabraArea.toLowerCase()}`
          }
        />
        <Tarjeta
          variante="verde"
          titulo="CO₂ evitado, 30 días"
          valor={`${formatearDecimal(kgCo2(kmEvitados) / 1000)} t`}
          detalle="estimado por consumo de gasoil"
        />
        <Tarjeta
          titulo={comparaLasDos ? `Desbordes, ${historial.semanas.length} semanas` : 'Desbordes reportados'}
          valor={comparaLasDos ? formatearNumero(desbordesDinamica) : `${variacionDesbordes}%`}
          detalle={
            comparaLasDos
              ? `con la ruta fija habrían sido ${formatearNumero(desbordesFija)}`
              : 'contra las semanas con ruta fija'
          }
        />
        <Tarjeta
          titulo="Vaciados por debajo del 50%"
          valor={`${Math.round(historial.porcentajeVisitasBajasDinamica)}%`}
          detalle={`con ruta fija eran ${Math.round(historial.porcentajeVisitasBajasFija)}%`}
        />
      </section>

      <div className="grilla-indicadores">
        <section className="panel" aria-labelledby="titulo-km">
          <div className="panel__cabecera">
            <h2 id="titulo-km">Km recorridos por día</h2>
            <ul className="leyenda">
              <li>
                <span className="leyenda__linea" /> Ruta dinámica
              </li>
              <li>
                <span className="leyenda__linea leyenda__linea--punteada" /> Ruta fija estimada
              </li>
            </ul>
          </div>
          <p className="nota">{idZona ? nombreArea(idZona) : 'Toda la flota'}, últimos 30 días</p>
          <div className="grafico">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={datosKm} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e3dccb" vertical={false} />
                <XAxis dataKey="fecha" tick={{ fontSize: 11 }} interval={4} />
                <YAxis tick={{ fontSize: 11 }} domain={[0, 'auto']} />
                <Tooltip />
                <Area
                  type="monotone"
                  dataKey="dinamica"
                  name="Ruta dinámica"
                  stroke="#2c3526"
                  strokeWidth={2}
                  fill="#e5e9dc"
                />
                <Line
                  type="monotone"
                  dataKey="fija"
                  name="Ruta fija estimada"
                  stroke="#a39c8a"
                  strokeWidth={2}
                  strokeDasharray="5 4"
                  dot={false}
                />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        </section>

        <section className="panel" aria-labelledby="titulo-desbordes">
          <div className="panel__cabecera">
            <h2 id="titulo-desbordes">Desbordes por semana</h2>
          </div>
          <p className="nota">
            {comparaLasDos
              ? 'En mostaza, lo que habría pasado con la ruta fija; en verde, con Ecoruta.'
              : 'En mostaza, las semanas antes de Ecoruta (ruta fija).'}{' '}
            {idZona ? `${nombreArea(idZona)}.` : palabraArea === 'Zona' ? 'Todas las zonas.' : 'Todos los sectores.'}
          </p>
          <div className="grafico">
            <ResponsiveContainer width="100%" height="100%">
              {comparaLasDos ? (
                <BarChart data={historial.semanas} margin={{ top: 16, right: 8, left: -18, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e3dccb" vertical={false} />
                  <XAxis dataKey="etiqueta" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                  <Tooltip />
                  <Bar dataKey="desbordesFija" name="Ruta fija" fill="#a07a2c" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="desbordesDinamica" name="Ecoruta" fill="#8ba67c" radius={[3, 3, 0, 0]} />
                </BarChart>
              ) : (
                <BarChart data={datosSemanas} margin={{ top: 16, right: 8, left: -18, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e3dccb" vertical={false} />
                  <XAxis dataKey="etiqueta" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                  <Tooltip formatter={(valor) => [valor, 'Desbordes']} />
                  <Bar dataKey="desbordes" radius={[3, 3, 0, 0]} label={{ position: 'top', fontSize: 11 }}>
                    {datosSemanas.map((s) => (
                      <Cell key={s.etiqueta} fill={s.antes ? '#a07a2c' : '#8ba67c'} />
                    ))}
                  </Bar>
                </BarChart>
              )}
            </ResponsiveContainer>
          </div>
        </section>

        <section className="panel" aria-labelledby="titulo-calor">
          <div className="panel__cabecera">
            <h2 id="titulo-calor">Zonas calientes</h2>
            <ul className="leyenda">
              <li>Bajo</li>
              {['calor--0', 'calor--1', 'calor--2', 'calor--3', 'calor--4'].map((c) => (
                <li key={c}>
                  <span className={`leyenda__cuadrito ${c}`} />
                </li>
              ))}
              <li>Alto</li>
            </ul>
          </div>
          <p className="nota">Llenado promedio por día y hora, {textoArea(zonaCalor)}</p>
          <div className="mapa-calor" role="img" aria-label={`Mapa de calor del llenado ${deArea(zonaCalor)}`}>
            {DIAS.map((nombre, d) => (
              <div key={nombre} className="mapa-calor__fila">
                <span>{nombre}</span>
                {HORAS_MAPA.map((h) => (
                  <span
                    key={h}
                    className={`mapa-calor__celda ${claseCalor(calor[d]?.[h] ?? 0)}`}
                    title={`${nombre} ${h}:00, ${Math.round(calor[d]?.[h] ?? 0)}%`}
                  />
                ))}
              </div>
            ))}
            <div className="mapa-calor__fila mapa-calor__fila--horas">
              <span />
              {HORAS_MAPA.map((h) => (
                <span key={h}>{h}</span>
              ))}
            </div>
          </div>
        </section>

        <section className="panel" aria-labelledby="titulo-eficiencia">
          <div className="panel__cabecera">
            <h2 id="titulo-eficiencia">Índice de eficiencia por {palabraArea.toLowerCase()}</h2>
          </div>
          <p className="nota">Contenedores vaciados en el momento justo (entre 50% y 99% de llenado), sobre 100</p>
          <ul className="barras-zona">
            {eficiencia.map((e) => (
              <li key={e.zona}>
                <span>{e.zona}</span>
                <div className="avance__barra avance__barra--ancha">
                  <span
                    style={{ width: `${e.indice}%`, background: e.indice === menorEficiencia ? '#a07a2c' : undefined }}
                  />
                </div>
                <strong>{e.indice}</strong>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  );
}
