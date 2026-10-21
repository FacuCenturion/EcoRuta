import { useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useSimulacion, type CasoDeDemo } from '../context/SimulacionContext';

// Casos para mostrar en la presentación sin esperar a que pasen solos. Es lo mismo que
// POST /api/v1/simulacion/casos en Swagger. El sistema no sabe qué se rompió: lo deduce de las
// lecturas, por eso algunos casos tardan unas horas simuladas en verse.
type Tipo = CasoDeDemo['tipo'] | 'reiniciar';

interface Opcion {
  tipo: Tipo;
  titulo: string;
  explicacion: string;
  dondeMirar: { ruta: string; texto: string };
}

const OPCIONES: Opcion[] = [
  {
    tipo: 'falla_sensor',
    titulo: 'Romper un sensor',
    explicacion:
      'Sin señal se detecta a las 2 horas simuladas y congelado a las 4. Trabado se ve la próxima vez que lo vacían.',
    dondeMirar: { ruta: '/sensores', texto: 'Estado de sensores' },
  },
  {
    tipo: 'reparar_sensor',
    titulo: 'Reparar un sensor',
    explicacion: 'Vuelve a mandar lecturas normales y en unos pasos queda "Funcionando".',
    dondeMirar: { ruta: '/sensores', texto: 'Estado de sensores' },
  },
  {
    tipo: 'revolver',
    titulo: 'Alguien revuelve la basura',
    explicacion: 'El nivel sube y baja de golpe: aparece un incidente de llenado irregular para revisar.',
    dondeMirar: { ruta: '/incidentes', texto: 'Incidentes' },
  },
  {
    tipo: 'llenar',
    titulo: 'Llenar un contenedor',
    explicacion: 'El sensor informa el nivel nuevo en el próximo paso. Desde 80% pasa a prioritario y sale un camión.',
    dondeMirar: { ruta: '/mapa', texto: 'Mapa en vivo' },
  },
  {
    tipo: 'evento_masivo',
    titulo: 'Evento masivo',
    explicacion: 'Una feria, un recital o un partido: el sector genera el doble de basura durante unas horas.',
    dondeMirar: { ruta: '/mapa', texto: 'Mapa en vivo' },
  },
  {
    tipo: 'reporte_vecinal',
    titulo: 'Reporte de un vecino',
    explicacion: 'Llega como si viniera de Boti y el contenedor entra en la próxima ruta.',
    dondeMirar: { ruta: '/reportes', texto: 'Reportes vecinales' },
  },
  {
    tipo: 'reiniciar',
    titulo: 'Reiniciar la demo',
    explicacion:
      'Borra rutas, lecturas e incidentes y vuelve al punto de partida. Sale siempre igual. Los turnos abiertos en la app del chofer siguen abiertos.',
    dondeMirar: { ruta: '/', texto: 'Panel general' },
  },
];

const NECESITA_CONTENEDOR: Tipo[] = ['falla_sensor', 'reparar_sensor', 'revolver', 'llenar', 'reporte_vecinal'];

export default function PanelDeDemo({ cerrar }: { cerrar: () => void }) {
  const { estado, areas, nombreArea, zonaFiltro, dispararCaso, reiniciarDemo } = useSimulacion();
  const { usuario } = useAuth();
  const esDirector = usuario?.rol === 'director';
  const [tipo, setTipo] = useState<Tipo>('falla_sensor');
  const [contenedorId, setContenedorId] = useState('');
  const [sectorId, setSectorId] = useState(zonaFiltro === 'todas' ? (areas[0]?.id ?? 1) : zonaFiltro);
  const [falla, setFalla] = useState<NonNullable<CasoDeDemo['falla']>>('sin_senal');
  const [horas, setHoras] = useState(6);
  const [nivel, setNivel] = useState(97);
  const [comentario, setComentario] = useState('Hay bolsas tiradas al lado del contenedor.');
  const [enviando, setEnviando] = useState(false);
  const [resultado, setResultado] = useState<{ bien: boolean; mensaje: string; opcion: Opcion }>();

  const opcion = OPCIONES.find((o) => o.tipo === tipo)!;
  const contenedores = useMemo(
    () =>
      estado.contenedores
        .filter((c) => zonaFiltro === 'todas' || c.areaId === zonaFiltro)
        .sort((a, b) => a.id.localeCompare(b.id)),
    [estado.contenedores, zonaFiltro],
  );
  const elegido = contenedores.some((c) => c.id === contenedorId) ? contenedorId : (contenedores[0]?.id ?? '');

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!dispararCaso || !reiniciarDemo) return;
    if (tipo === 'reiniciar' && !window.confirm('¿Reiniciar la demo? Se borra todo lo que pasó hasta ahora.')) return;
    setEnviando(true);
    let respuesta: { bien: boolean; mensaje: string };
    if (tipo === 'reiniciar') {
      respuesta = await reiniciarDemo();
    } else {
      const caso: CasoDeDemo = { tipo };
      if (NECESITA_CONTENEDOR.includes(tipo)) caso.contenedorId = elegido;
      if (tipo === 'falla_sensor') Object.assign(caso, { falla, horas });
      if (tipo === 'evento_masivo') Object.assign(caso, { sectorId, horas });
      if (tipo === 'llenar') caso.nivel = nivel;
      if (tipo === 'reporte_vecinal') caso.comentario = comentario;
      respuesta = await dispararCaso(caso);
    }
    setResultado({ ...respuesta, opcion });
    setEnviando(false);
  }

  return (
    <div className="demo" role="presentation" onClick={cerrar}>
      <section
        className="demo__ventana"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-demo"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="panel__cabecera">
          <h2 id="titulo-demo">Demo simulada</h2>
          <button type="button" className="boton" onClick={cerrar} aria-label="Cerrar">
            Cerrar
          </button>
        </div>
        <p className="nota">
          Casos para mostrar sin esperar a que pasen solos. Para verlos más rápido, acelerá la simulación a x4.
        </p>

        <div className="demo__casos" role="radiogroup" aria-label="Caso">
          {OPCIONES.filter((o) => o.tipo !== 'reiniciar' || esDirector).map((o) => (
            <button
              key={o.tipo}
              type="button"
              role="radio"
              aria-checked={tipo === o.tipo}
              className={tipo === o.tipo ? 'demo__caso demo__caso--elegido' : 'demo__caso'}
              onClick={() => {
                setTipo(o.tipo);
                setResultado(undefined);
              }}
            >
              {o.titulo}
            </button>
          ))}
        </div>

        <form className="formulario" onSubmit={enviar}>
          <p className="nota">{opcion.explicacion}</p>

          {NECESITA_CONTENEDOR.includes(tipo) && (
            <label>
              Contenedor{zonaFiltro === 'todas' ? '' : ` (${nombreArea(zonaFiltro)})`}
              <select value={elegido} onChange={(e) => setContenedorId(e.target.value)}>
                {contenedores.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.id} · {c.direccion} · {Math.round(c.nivelEstimado ?? c.llenado)}%
                  </option>
                ))}
              </select>
            </label>
          )}

          {tipo === 'falla_sensor' && (
            <label>
              Qué le pasa
              <select value={falla} onChange={(e) => setFalla(e.target.value as typeof falla)}>
                <option value="sin_senal">Deja de mandar datos (sin señal)</option>
                <option value="congelado">Repite siempre el mismo valor (congelado)</option>
                <option value="trabado">Se traba la próxima vez que lo vacían (trabado)</option>
              </select>
            </label>
          )}

          {tipo === 'evento_masivo' && (
            <label>
              Sector
              <select value={sectorId} onChange={(e) => setSectorId(Number(e.target.value))}>
                {areas.map((a) => (
                  <option key={a.id} value={a.id}>
                    {nombreArea(a.id)}: {a.nombre}
                  </option>
                ))}
              </select>
            </label>
          )}

          {(tipo === 'falla_sensor' || tipo === 'evento_masivo') && (
            <label>
              Cuántas horas simuladas dura
              <input type="number" min={1} max={48} value={horas} onChange={(e) => setHoras(Number(e.target.value))} />
            </label>
          )}

          {tipo === 'llenar' && (
            <label>
              Nivel: {nivel}%
              <input type="range" min={0} max={100} value={nivel} onChange={(e) => setNivel(Number(e.target.value))} />
            </label>
          )}

          {tipo === 'reporte_vecinal' && (
            <label>
              Comentario del vecino
              <textarea rows={2} value={comentario} onChange={(e) => setComentario(e.target.value)} />
            </label>
          )}

          <button
            type="submit"
            className="boton boton--primario"
            disabled={enviando || (NECESITA_CONTENEDOR.includes(tipo) && !elegido)}
          >
            {enviando ? 'Enviando…' : tipo === 'reiniciar' ? 'Reiniciar la demo' : 'Disparar el caso'}
          </button>
        </form>

        {resultado && (
          <div className={resultado.bien ? 'demo__resultado' : 'demo__resultado demo__resultado--error'} role="status">
            <p>{resultado.mensaje}</p>
            {resultado.bien && (
              <Link to={resultado.opcion.dondeMirar.ruta} className="boton" onClick={cerrar}>
                Ver en {resultado.opcion.dondeMirar.texto}
              </Link>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
