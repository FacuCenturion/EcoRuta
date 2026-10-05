import { useState } from 'react';
import Encabezado from '../components/Encabezado';
import { ETIQUETA_ESTADO } from '../components/ParaRevisar';
import { useSimulacion } from '../context/SimulacionContext';
import { ZONAS } from '../data/zonas';
import { formatearFechaCorta, formatearHora } from '../utils/formato';

export default function ReportesVecinales() {
  const { estado, zonaFiltro, reportarVecino } = useSimulacion();
  const [zonaForm, setZonaForm] = useState(2);
  const [contenedorId, setContenedorId] = useState('');
  const [comentario, setComentario] = useState('');
  const [enviado, setEnviado] = useState(false);

  const reportes = estado.incidentes.filter(
    (i) => i.tipo === 'reporte_vecinal' && (zonaFiltro === 'todas' || i.zonaId === zonaFiltro),
  );
  const opciones = estado.contenedores.filter((c) => c.zonaId === zonaForm);
  const idElegido = opciones.some((c) => c.id === contenedorId) ? contenedorId : opciones[0]?.id;

  function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    if (!idElegido) return;
    reportarVecino(idElegido, comentario);
    setComentario('');
    setEnviado(true);
  }

  return (
    <>
      <Encabezado
        titulo="Reportes vecinales"
        subtitulo="Avisos que llegan por Boti, el bot de la Ciudad en WhatsApp."
      />
      <div className="reportes">
        <section className="panel" aria-labelledby="titulo-simular">
          <div className="panel__cabecera">
            <h2 id="titulo-simular">Simular un reporte de Boti</h2>
          </div>
          <p className="nota">
            Mientras no esté conectado el bot real, este formulario permite probar cómo llega un reporte al sistema.
          </p>
          <form className="formulario" onSubmit={enviar}>
            <label>
              Zona
              <select value={zonaForm} onChange={(e) => setZonaForm(Number(e.target.value))}>
                {ZONAS.map((z) => (
                  <option key={z.id} value={z.id}>
                    Zona {z.id}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Contenedor
              <select value={idElegido} onChange={(e) => setContenedorId(e.target.value)}>
                {opciones.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.direccion}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Comentario
              <textarea
                rows={3}
                value={comentario}
                onChange={(e) => {
                  setComentario(e.target.value);
                  setEnviado(false);
                }}
                placeholder="Por ejemplo: hay bolsas fuera del contenedor"
              />
            </label>
            <button type="submit" className="boton boton--primario">
              Enviar reporte
            </button>
            {enviado && <p className="nota" role="status">Reporte enviado. Ya aparece en la lista y en Para revisar.</p>}
          </form>
        </section>

        <section className="panel" aria-labelledby="titulo-reportes">
          <div className="panel__cabecera">
            <h2 id="titulo-reportes">Reportes recibidos</h2>
            <span className="etiqueta">{reportes.length}</span>
          </div>
          {reportes.length === 0 ? (
            <p className="vacio">Todavía no llegaron reportes.</p>
          ) : (
            <ul className="tarjetas-reporte">
              {reportes.map((r) => (
                <li key={r.id} className="reporte">
                  <div className="reporte__foto" aria-hidden="true">
                    Foto
                  </div>
                  <div className="reporte__texto">
                    <strong>{r.direccion}</strong>
                    <span>
                      Zona {r.zonaId} · vía {r.origen} · {formatearFechaCorta(r.creadoMs)} {formatearHora(r.creadoMs)}
                    </span>
                    <span>{r.descripcion}</span>
                    <span className={`estado estado--${r.estado}`}>{ETIQUETA_ESTADO[r.estado]}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
