import { useEffect, useState } from 'react';
import { pedir } from '../api';
import type { Parada } from '../tipos';

interface Props {
  parada: Parada;
  cancelar: () => void;
  confirmar: (motivo: string) => Promise<void>;
}

// Por si el servidor no responde: los mismos que tiene el backend
const MOTIVOS_DE_RESERVA = [
  'Auto estacionado',
  'Obra o calle cortada',
  'Contenedor dañado',
  'Basura fuera del contenedor',
  'Sensor marca mal',
];

// Hoja que sube desde abajo para elegir por qué no se pudo vaciar el contenedor.
// El Centro de Monitoreo recibe un incidente con el motivo.
export default function NoSePudo({ parada, cancelar, confirmar }: Props) {
  const [motivos, setMotivos] = useState(MOTIVOS_DE_RESERVA);
  const [elegido, setElegido] = useState<string>();
  const [otro, setOtro] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    pedir<string[]>('/chofer/motivos')
      .then(setMotivos)
      .catch(() => {
        // quedan los de reserva
      });
  }, []);

  const motivo = elegido === 'otro' ? otro.trim() : elegido;

  async function enviar() {
    if (!motivo) return;
    setEnviando(true);
    await confirmar(motivo);
    setEnviando(false);
  }

  return (
    <div className="fondo" role="presentation" onClick={cancelar}>
      <section
        className="hoja"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-no-se-pudo"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="titulo-no-se-pudo">¿Por qué no se pudo?</h2>
        <p className="nota">{parada.direccion}</p>
        <div className="opciones" role="radiogroup">
          {[...motivos, 'otro'].map((m) => (
            <button
              key={m}
              type="button"
              role="radio"
              aria-checked={elegido === m}
              className={elegido === m ? 'opcion opcion--elegida' : 'opcion'}
              onClick={() => setElegido(m)}
            >
              {m === 'otro' ? 'Otro motivo' : m}
            </button>
          ))}
        </div>
        {elegido === 'otro' && (
          <textarea
            rows={2}
            placeholder="Contá qué pasó"
            value={otro}
            onChange={(e) => setOtro(e.target.value)}
            aria-label="Otro motivo"
          />
        )}
        <div className="acciones">
          <button type="button" className="boton boton--secundario" onClick={cancelar}>
            Volver
          </button>
          <button type="button" className="boton boton--alerta" disabled={!motivo || enviando} onClick={enviar}>
            {enviando ? 'Enviando…' : 'Confirmar'}
          </button>
        </div>
      </section>
    </div>
  );
}
