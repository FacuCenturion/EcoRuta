// Indicaciones en voz alta, con la voz del celular (no necesita internet ni clave)
let silencio = false;

export function silenciar(valor: boolean) {
  silencio = valor;
  if (valor) window.speechSynthesis?.cancel();
}

export function decir(texto: string) {
  if (silencio || typeof window === 'undefined' || !('speechSynthesis' in window)) return;
  const frase = new SpeechSynthesisUtterance(texto);
  frase.lang = 'es-AR';
  frase.rate = 1.05;
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(frase);
}
