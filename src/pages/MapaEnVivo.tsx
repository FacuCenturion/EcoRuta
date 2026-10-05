import { useState } from 'react';
import Encabezado from '../components/Encabezado';
import MapaContenedores, { type Vista } from '../components/MapaContenedores';
import { useSimulacion } from '../context/SimulacionContext';
import { contenedoresPrioritarios } from '../logic/indicadores';
import { diaDeSemana } from '../logic/llenado';
import { etiquetaMotivo, lecturaConfiable, nivelVisual } from '../logic/prioridad';
import Leyenda from '../components/Leyenda';
import { COLOR_NIVEL } from '../utils/colores';
import { vistaParaZona } from '../utils/vista';

type Filtro = 'todos' | 'prioritarios';

export default function MapaEnVivo() {
  const { estado, zonaFiltro } = useSimulacion();
  const [filtro, setFiltro] = useState<Filtro>('todos');
  const [seleccionadoId, setSeleccionadoId] = useState<string>();
  const [foco, setFoco] = useState<Vista | null>(null);

  const idZona = zonaFiltro === 'todas' ? undefined : zonaFiltro;
  const prioritarios = contenedoresPrioritarios(estado, idZona).sort(
    (a, b) => lecturaConfiable(b.contenedor) - lecturaConfiable(a.contenedor),
  );
  const idsPrioritarios = new Set(prioritarios.map((p) => p.contenedor.id));
  const visibles = estado.contenedores.filter(
    (c) =>
      (idZona === undefined || c.zonaId === idZona) && (filtro === 'todos' || idsPrioritarios.has(c.id)),
  );
  const camiones = estado.camiones.filter((c) => idZona === undefined || c.zonaId === idZona);

  const fecha = new Date(estado.relojMs);
  const hora = fecha.getHours();
  const dia = diaDeSemana(fecha);

  function elegir(id: string) {
    const contenedor = estado.contenedores.find((c) => c.id === id);
    if (!contenedor) return;
    setSeleccionadoId(id);
    setFoco({ lat: contenedor.posicion.lat, lng: contenedor.posicion.lng, zoom: 17 });
  }

  return (
    <>
      <Encabezado titulo="Mapa en vivo" subtitulo="Estado de cada contenedor según su último dato de sensor." />
      <div className="mapa-pagina">
        <section className="panel panel--mapa-grande" aria-label="Mapa">
          <div className="barra-filtros">
            <button
              type="button"
              className={filtro === 'todos' ? 'boton boton--activo' : 'boton'}
              onClick={() => setFiltro('todos')}
            >
              Todos los contenedores
            </button>
            <button
              type="button"
              className={filtro === 'prioritarios' ? 'boton boton--activo' : 'boton'}
              onClick={() => setFiltro('prioritarios')}
            >
              Solo prioritarios
            </button>
            <Leyenda conCamion />
          </div>
          <MapaContenedores
            className="mapa mapa--grande"
            contenedores={visibles}
            camiones={camiones}
            relojMs={estado.relojMs}
            vista={foco ?? vistaParaZona(zonaFiltro)}
            seleccionadoId={seleccionadoId}
          />
        </section>

        <section className="panel lista-prioritarios" aria-labelledby="titulo-prioritarios">
          <div className="panel__cabecera">
            <h2 id="titulo-prioritarios">Prioritarios</h2>
            <span className="etiqueta">{prioritarios.length}</span>
          </div>
          {prioritarios.length === 0 ? (
            <p className="vacio">Ningún contenedor necesita recolección por ahora.</p>
          ) : (
            <ul className="lista-simple">
              {prioritarios.map(({ contenedor, motivo }) => (
                <li key={contenedor.id}>
                  <button
                    type="button"
                    className={contenedor.id === seleccionadoId ? 'fila-boton fila-boton--activa' : 'fila-boton'}
                    onClick={() => elegir(contenedor.id)}
                  >
                    <span
                      className="leyenda__punto"
                      style={{ background: COLOR_NIVEL[nivelVisual(contenedor, hora, dia)] }}
                    />
                    <span className="fila-boton__texto">
                      <strong>{contenedor.direccion}</strong>
                      <small>
                        Zona {contenedor.zonaId} · {etiquetaMotivo(motivo)}
                      </small>
                    </span>
                    <span className="fila-boton__valor">{Math.round(lecturaConfiable(contenedor))}%</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
