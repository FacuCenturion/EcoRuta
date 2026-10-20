import { useEffect, useRef, useState, type ReactNode } from 'react';
import { pedir } from '../api';
import { dictar, sePuedeDictar } from '../dictado';
import { achicarFoto } from '../foto';
import type { Parada } from '../tipos';
import {
  IconoAuto,
  IconoBolsas,
  IconoCamara,
  IconoCheck,
  IconoMicrofono,
  IconoObra,
  IconoPuntos,
  IconoRoto,
  IconoSensor,
} from './Iconos';

interface Props {
  parada: Parada; // la dirección ya se ve en el encabezado
  cancelar: () => void;
  confirmar: (motivo: string, nota?: string, foto?: string) => Promise<boolean>;
}

// Por si el servidor no responde: los mismos que tiene el backend
const MOTIVOS_DE_RESERVA = [
  'Auto estacionado',
  'Obra o calle cortada',
  'Contenedor dañado',
  'Basura fuera del contenedor',
  'Sensor marca mal',
];

const ICONOS: Record<string, ReactNode> = {
  'Auto estacionado': <IconoAuto />,
  'Obra o calle cortada': <IconoObra />,
  'Contenedor dañado': <IconoRoto />,
  'Basura fuera del contenedor': <IconoBolsas />,
  'Sensor marca mal': <IconoSensor />,
};
const OTRO = 'Otro motivo';

// Hoja para contar por qué no se pudo vaciar el contenedor. El Centro de Monitoreo recibe un
// incidente con el motivo, la nota y la foto.
export default function NoSePudo({ cancelar, confirmar }: Props) {
  const [motivos, setMotivos] = useState(MOTIVOS_DE_RESERVA);
  const [elegido, setElegido] = useState<string>();
  const [nota, setNota] = useState('');
  const [foto, setFoto] = useState<string>();
  const [escuchando, setEscuchando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [problema, setProblema] = useState<string>();
  const archivo = useRef<HTMLInputElement>(null);
  const puedeDictar = sePuedeDictar();

  useEffect(() => {
    pedir<string[]>('/chofer/motivos')
      .then(setMotivos)
      .catch(() => {
        // quedan los de reserva
      });
  }, []);

  const motivo = elegido === OTRO ? nota.trim() : elegido;

  async function enviar() {
    if (!motivo) return;
    setEnviando(true);
    await confirmar(motivo, elegido === OTRO ? undefined : nota.trim() || undefined, foto);
    setEnviando(false);
  }

  async function sacarFoto(lista: FileList | null) {
    if (!lista?.[0]) return;
    try {
      setFoto(await achicarFoto(lista[0]));
      setProblema(undefined);
    } catch {
      setProblema('No se pudo leer la foto. Probá de nuevo.');
    }
  }

  function dictarNota() {
    setEscuchando(true);
    dictar(
      (texto) => {
        setEscuchando(false);
        if (texto) setNota((anterior) => (anterior ? `${anterior} ${texto}` : texto));
      },
      () => {
        setEscuchando(false);
        setProblema('No se pudo usar el micrófono. Escribí la nota.');
      },
    );
  }

  return (
    <section className="hoja" role="dialog" aria-modal="true" aria-labelledby="titulo-no-se-pudo">
      <span className="hoja__manija" aria-hidden="true" />
      <h2 id="titulo-no-se-pudo">¿Por qué no se pudo recolectar?</h2>
      <p className="tenue">Elegí un motivo. La parada queda pendiente para el operador.</p>
      <div className="motivos" role="radiogroup" aria-label="Motivo">
        {[...motivos, OTRO].map((m) => (
          <button
            key={m}
            type="button"
            role="radio"
            aria-checked={elegido === m}
            className={elegido === m ? 'motivo motivo--elegido' : 'motivo'}
            onClick={() => setElegido(m)}
          >
            {ICONOS[m] ?? <IconoPuntos />}
            <span>{m}</span>
          </button>
        ))}
      </div>
      {(elegido === OTRO || nota) && (
        <textarea
          rows={2}
          placeholder={elegido === OTRO ? 'Contá qué pasó' : 'Nota para el operador'}
          value={nota}
          onChange={(e) => setNota(e.target.value)}
          aria-label="Nota"
        />
      )}
      <div className="extras">
        <input
          ref={archivo}
          type="file"
          accept="image/*"
          capture="environment"
          hidden
          onChange={(e) => sacarFoto(e.target.files)}
        />
        <button type="button" className="boton boton--borde boton--medio" onClick={() => archivo.current?.click()}>
          {foto ? <IconoCheck /> : <IconoCamara />} {foto ? 'Foto lista' : 'Sacar foto'}
        </button>
        {puedeDictar ? (
          <button type="button" className="boton boton--borde boton--medio" disabled={escuchando} onClick={dictarNota}>
            <IconoMicrofono /> {escuchando ? 'Escuchando…' : 'Dictar nota'}
          </button>
        ) : (
          <button type="button" className="boton boton--borde boton--medio" onClick={() => setNota(nota || ' ')}>
            <IconoMicrofono /> Escribir nota
          </button>
        )}
      </div>
      {foto && <img className="miniatura" src={foto} alt="Foto que se va a mandar" />}
      {problema && (
        <p className="error" role="alert">
          {problema}
        </p>
      )}
      <button type="button" className="boton boton--principal" disabled={!motivo || enviando} onClick={enviar}>
        {enviando ? 'Enviando…' : 'Enviar y seguir ruta'}
      </button>
      <button type="button" className="boton boton--texto" onClick={cancelar}>
        Volver
      </button>
    </section>
  );
}
