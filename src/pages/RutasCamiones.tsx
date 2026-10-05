import { useState } from 'react';
import Encabezado from '../components/Encabezado';
import MapaContenedores from '../components/MapaContenedores';
import { useSimulacion } from '../context/SimulacionContext';
import { buscarZona } from '../data/zonas';
import { avanceDeRuta } from '../logic/indicadores';
import type { Camion, Coordenada, EstadoCamion } from '../types';
import { formatearHora } from '../utils/formato';

const ETIQUETA_CAMION: Record<EstadoCamion, string> = {
  en_ruta: 'En ruta',
  en_descarga: 'En descarga',
  en_base: 'En base',
  demorado: 'Demorado',
};

const ETIQUETA_CHIP: Record<EstadoCamion, string> = {
  en_ruta: 'en ruta',
  en_descarga: 'en descarga',
  en_base: 'en base',
  demorado: 'demorado',
};

export default function RutasCamiones() {
  const { estado, zonaFiltro } = useSimulacion();
  const [busqueda, setBusqueda] = useState('');
  const [elegidoId, setElegidoId] = useState<string>();

  const flota = estado.camiones.filter((c) => {
    const coincideZona = zonaFiltro === 'todas' || c.zonaId === zonaFiltro;
    const texto = `${c.numero} ${c.chofer}`.toLowerCase();
    return coincideZona && texto.includes(busqueda.trim().toLowerCase());
  });
  const camion: Camion | undefined = flota.find((c) => c.id === elegidoId) ?? flota[0];

  return (
    <>
      <Encabezado titulo="Rutas y camiones" subtitulo="Seguimiento de la flota y del orden de paradas de cada camión." />
      <div className="rutas">
        <section className="panel" aria-labelledby="titulo-flota">
          <div className="panel__cabecera">
            <h2 id="titulo-flota">Flota</h2>
            <span className="nota">{estado.camiones.length} camiones hoy</span>
          </div>
          <ul className="chips" aria-label="Camiones por estado">
            {(['en_ruta', 'en_descarga', 'en_base', 'demorado'] as EstadoCamion[]).map((e) => (
              <li key={e} className={`chip chip--${e}`}>
                {estado.camiones.filter((c) => c.estado === e).length} {ETIQUETA_CHIP[e]}
              </li>
            ))}
          </ul>
          <input
            type="search"
            className="buscador"
            placeholder="Buscar camión o chofer"
            aria-label="Buscar camión o chofer"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
          />
          <ul className="lista-simple">
            {flota.map((c) => {
              const avance = avanceDeRuta(c);
              return (
                <li key={c.id}>
                  <button
                    type="button"
                    className={c.id === camion?.id ? 'fila-boton fila-boton--activa' : 'fila-boton'}
                    onClick={() => setElegidoId(c.id)}
                  >
                    <span className="insignia">{String(c.numero).padStart(2, '0')}</span>
                    <span className="fila-boton__texto">
                      <strong>Camión {c.numero}</strong>
                      <small>Zona {c.zonaId}, generales</small>
                      <span className="avance__barra avance__barra--fina">
                        <span style={{ width: `${avance.total ? (avance.hechas / avance.total) * 100 : 0}%` }} />
                      </span>
                    </span>
                    <span className="fila-boton__valor">
                      <span className={`estado estado--${c.estado}`}>{ETIQUETA_CAMION[c.estado]}</span>
                      <small>
                        {avance.hechas}/{avance.total}
                      </small>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </section>

        {camion ? <DetalleCamion camion={camion} /> : <p className="vacio">No hay camiones para mostrar.</p>}
      </div>
    </>
  );
}

function DetalleCamion({ camion }: { camion: Camion }) {
  const { estado } = useSimulacion();
  const zona = buscarZona(camion.zonaId);
  const avance = avanceDeRuta(camion);
  const contenedores = estado.contenedores.filter((c) => camion.paradas.some((p) => p.contenedorId === c.id));
  const proxima = camion.paradas.find((p) => p.estado === 'pendiente');
  const ultimaParada = camion.paradas[camion.paradas.length - 1];

  const posicionDe = (id: string): Coordenada => {
    const c = estado.contenedores.find((x) => x.id === id);
    return c ? c.posicion : zona.base;
  };
  const hechas = camion.paradas.filter((p) => p.estado === 'recolectada').map((p) => posicionDe(p.contenedorId));
  const pendientes = camion.paradas.filter((p) => p.estado === 'pendiente').map((p) => posicionDe(p.contenedorId));
  const recorridoHecho = [zona.base, ...hechas];
  const recorridoPendiente = pendientes.length > 0 ? [camion.posicion, ...pendientes, zona.base] : [];

  return (
    <div className="detalle-camion">
      <section className="resumen-camion" aria-label={`Camión ${camion.numero}`}>
        <div>
          <h2>
            Camión {camion.numero} <span className={`estado estado--${camion.estado}`}>{ETIQUETA_CAMION[camion.estado]}</span>
          </h2>
          <p>
            Chofer: {camion.chofer}. Zona {camion.zonaId}, residuos generales.
          </p>
        </div>
        <dl className="resumen-camion__datos">
          <div>
            <dt>Paradas</dt>
            <dd>
              {avance.hechas} de {avance.total}
            </dd>
          </div>
          <div>
            <dt>Recorrido</dt>
            <dd>{camion.kmRecorridosHoy.toFixed(1)} km</dd>
          </div>
          <div>
            <dt>Carga</dt>
            <dd>{camion.cargaPct}%</dd>
          </div>
          <div>
            <dt>Fin estimado</dt>
            <dd>{ultimaParada ? formatearHora(ultimaParada.etaMs) : '—'}</dd>
          </div>
        </dl>
      </section>

      <div className="detalle-camion__cuerpo">
        <section className="panel" aria-label="Mapa de la ruta">
          <div className="barra-filtros">
            <span className="etiqueta">Recorrido</span>
            <span className="etiqueta etiqueta--azul">Pendiente</span>
          </div>
          <MapaContenedores
            className="mapa mapa--medio"
            contenedores={contenedores}
            camiones={[camion]}
            relojMs={estado.relojMs}
            vista={{ lat: zona.centro.lat, lng: zona.centro.lng, zoom: 15 }}
            recorridoHecho={recorridoHecho}
            recorridoPendiente={recorridoPendiente}
          />
        </section>

        <section className="panel" aria-labelledby="titulo-paradas">
          <div className="panel__cabecera">
            <h2 id="titulo-paradas">Paradas</h2>
            <span className="nota">Orden optimizado</span>
          </div>
          {camion.paradas.length === 0 ? (
            <p className="vacio">El camión está en la base. Sale cuando haya contenedores para recolectar.</p>
          ) : (
            <ol className="paradas">
              {camion.paradas.map((parada, i) => {
                const contenedor = estado.contenedores.find((c) => c.id === parada.contenedorId);
                const esProxima = parada === proxima;
                return (
                  <li key={parada.contenedorId} className={`parada parada--${parada.estado}`}>
                    <span className="parada__marca" aria-hidden="true">
                      {parada.estado === 'recolectada' ? '✓' : parada.estado === 'no_se_pudo' ? '!' : ''}
                    </span>
                    <span className="parada__numero">{i + 1}</span>
                    <span className="parada__texto">
                      {contenedor?.direccion}
                      {esProxima && <span className="etiqueta etiqueta--azul">Próxima</span>}
                      {parada.estado === 'no_se_pudo' && <span className="parada__motivo">{parada.motivo}</span>}
                    </span>
                    <span className="parada__hora">
                      {esProxima ? `llega ${formatearHora(parada.etaMs)}` : formatearHora(parada.horaRealMs ?? parada.etaMs)}
                    </span>
                  </li>
                );
              })}
            </ol>
          )}
          <div className="acciones-ruta">
            <button type="button" className="boton boton--primario" disabled title="Disponible en el próximo release">
              Reasignar paradas
            </button>
            <button type="button" className="boton" disabled title="Disponible en el próximo release">
              Contactar chofer
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
