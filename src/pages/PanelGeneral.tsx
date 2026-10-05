import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import Leyenda from '../components/Leyenda';
import Encabezado from '../components/Encabezado';
import MapaContenedores from '../components/MapaContenedores';
import ParaRevisar from '../components/ParaRevisar';
import Tarjeta from '../components/Tarjeta';
import { useSimulacion } from '../context/SimulacionContext';
import { ZONAS } from '../data/zonas';
import {
  avanceDeRuta,
  contenedoresPrioritarios,
  esAbierto,
  kgCo2,
} from '../logic/indicadores';
import { obtenerHistorial } from '../logic/historial';
import { formatearDecimal, formatearNumero } from '../utils/formato';
import { vistaParaZona } from '../utils/vista';

export default function PanelGeneral() {
  const { estado, zonaFiltro } = useSimulacion();
  const idZona = zonaFiltro === 'todas' ? undefined : zonaFiltro;
  const hora = new Date(estado.relojMs).getHours();

  const contenedores = estado.contenedores.filter((c) => idZona === undefined || c.zonaId === idZona);
  const camiones = estado.camiones.filter((c) => idZona === undefined || c.zonaId === idZona);
  const prioritarios = contenedoresPrioritarios(estado, idZona);
  const enRuta = camiones.filter((c) => c.estado === 'en_ruta' || c.estado === 'demorado');
  const enBase = camiones.filter((c) => c.estado === 'en_base').length;
  const enDescarga = camiones.filter((c) => c.estado === 'en_descarga').length;
  const incidentes = estado.incidentes.filter((i) => idZona === undefined || i.zonaId === idZona);
  const abiertos = incidentes.filter((i) => esAbierto(i.estado)).length;
  const pendientes = incidentes.filter((i) => i.estado === 'pendiente').length;
  const kmEvitados = estado.kmEvitadosHoy;
  const porcentajeHecho = estado.planificadosHoy ? Math.round((estado.recolectadosHoy / estado.planificadosHoy) * 100) : 0;

  const historial = obtenerHistorial();
  const datosHora = historial.llenadoPorHora.map((valor, h) => ({ hora: h, llenado: Math.round(valor) }));
  const picoHora = datosHora.reduce((mayor, d) => (d.llenado > mayor.llenado ? d : mayor), datosHora[0]).hora;

  return (
    <>
      <Encabezado titulo="Panel general" />

      <section className="tarjetas" aria-label="Resumen">
        <Tarjeta
          variante="oscura"
          etiqueta="En vivo"
          titulo="Contenedores prioritarios"
          valor={prioritarios.length}
          detalle="superan el 80% o corren riesgo de desborde"
        />
        <Tarjeta
          titulo="Camiones en ruta"
          valor={
            <>
              {enRuta.length} <small>de {camiones.length}</small>
            </>
          }
          detalle={`${enBase} en base, ${enDescarga} en descarga`}
        />
        <Tarjeta
          titulo="Recolectados hoy"
          valor={formatearNumero(estado.recolectadosHoy)}
          detalle={`${porcentajeHecho}% de lo planificado`}
        />
        <Tarjeta
          variante="alerta"
          titulo="Incidentes abiertos"
          valor={abiertos}
          detalle={`${pendientes} esperan tu confirmación`}
        />
        <Tarjeta
          variante="verde"
          titulo="Km evitados hoy"
          valor={`${formatearNumero(kmEvitados)} km`}
          detalle={`${formatearDecimal(kgCo2(kmEvitados) / 1000)} t de CO₂ menos que la ruta fija`}
        />
      </section>

      <div className="grilla-panel">
        <section className="panel panel--mapa" aria-labelledby="titulo-mapa">
          <div className="panel__cabecera">
            <h2 id="titulo-mapa">Mapa en vivo</h2>
            <Leyenda conCamion />
          </div>
          <MapaContenedores
            contenedores={contenedores}
            camiones={camiones}
            relojMs={estado.relojMs}
            vista={vistaParaZona(zonaFiltro)}
          />
          <p className="nota">{formatearNumero(contenedores.length)} contenedores con sensor (datos simulados)</p>
        </section>

        <ParaRevisar />
      </div>

      <div className="grilla-panel grilla-panel--abajo">
        <section className="panel" aria-labelledby="titulo-zonas">
          <div className="panel__cabecera">
            <h2 id="titulo-zonas">Estado por zona</h2>
            <span className="nota">Empresas recolectoras</span>
          </div>
          <div className="tabla-scroll">
            <table className="tabla">
              <thead>
                <tr>
                  <th>Zona</th>
                  <th>Empresa</th>
                  <th>Avance de ruta</th>
                  <th>Prioritarios</th>
                  <th>Incidentes</th>
                  <th>Eficiencia</th>
                </tr>
              </thead>
              <tbody>
                {ZONAS.map((zona) => {
                  const camion = estado.camiones.find((c) => c.zonaId === zona.id);
                  const avance = camion ? avanceDeRuta(camion) : { hechas: 0, total: 0 };
                  const porcentaje = avance.total ? Math.round((avance.hechas / avance.total) * 100) : 0;
                  const abiertosZona = estado.incidentes.filter((i) => i.zonaId === zona.id && esAbierto(i.estado)).length;
                  return (
                    <tr key={zona.id} className={zona.id === idZona ? 'tabla__fila--activa' : undefined}>
                      <th scope="row">Zona {zona.id}</th>
                      <td>{zona.empresa}</td>
                      <td>
                        <div className="avance">
                          <div className="avance__barra">
                            <span style={{ width: `${porcentaje}%` }} />
                          </div>
                          <span>{avance.total ? `${porcentaje}%` : 'En base'}</span>
                        </div>
                      </td>
                      <td>{contenedoresPrioritarios(estado, zona.id).length}</td>
                      <td>{abiertosZona}</td>
                      <td>{Math.round(historial.eficienciaPorZona[zona.id])}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel" aria-labelledby="titulo-hora">
          <div className="panel__cabecera">
            <h2 id="titulo-hora">Llenado promedio por hora</h2>
          </div>
          <p className="nota">
            El pico es a las {picoHora} h. Promedio de los últimos 30 días (simulado); la hora actual está resaltada.
          </p>
          <div className="grafico grafico--chico">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={datosHora} margin={{ top: 8, right: 4, left: -22, bottom: 0 }}>
                <XAxis dataKey="hora" tickFormatter={(h) => `${h} h`} interval={5} tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} unit="%" domain={[0, 100]} />
                <Tooltip formatter={(valor) => [`${valor}%`, 'Llenado']} labelFormatter={(h) => `${h}:00 h`} />
                <Bar dataKey="llenado" radius={[3, 3, 0, 0]}>
                  {datosHora.map((d) => (
                    <Cell key={d.hora} fill={d.hora === hora ? '#a07a2c' : d.hora < hora ? '#8ba67c' : '#ddd3b9'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      </div>
    </>
  );
}
