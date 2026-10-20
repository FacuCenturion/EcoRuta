// Dictar la nota con el reconocimiento de voz del navegador. Anda en Chrome de Android y en
// Safari de iPhone; si el celular no lo tiene, el botón no aparece y se escribe a mano.
type Reconocedor = {
  lang: string;
  interimResults: boolean;
  onresult: (e: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void;
  onend: () => void;
  onerror: () => void;
  start: () => void;
  stop: () => void;
};

function crear(): Reconocedor | null {
  const w = window as unknown as Record<string, new () => Reconocedor>;
  const Clase = w.SpeechRecognition ?? w.webkitSpeechRecognition;
  return Clase ? new Clase() : null;
}

export function sePuedeDictar(): boolean {
  return typeof window !== 'undefined' && crear() !== null;
}

/** Escucha hasta que la persona deja de hablar y devuelve lo que entendió. */
export function dictar(alTerminar: (texto: string) => void, alFallar: () => void): () => void {
  const reconocedor = crear();
  if (!reconocedor) {
    alFallar();
    return () => {};
  }
  let texto = '';
  reconocedor.lang = 'es-AR';
  reconocedor.interimResults = false;
  reconocedor.onresult = (e) => {
    texto = Array.from(e.results).map((r) => r[0].transcript).join(' ');
  };
  reconocedor.onend = () => alTerminar(texto.trim());
  reconocedor.onerror = alFallar;
  reconocedor.start();
  return () => reconocedor.stop();
}
