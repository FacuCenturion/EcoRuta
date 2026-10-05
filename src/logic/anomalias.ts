import { LECTURAS_PARA_ANOMALIA, SALTO_MINIMO_ANOMALIA } from '../config';

// Un contenedor se llena de a poco, así que las lecturas casi siempre suben.
// Si alguien revuelve la basura, el sensor marca valores que suben y bajan
// de golpe. Contamos cuántas veces cambia el sentido con saltos grandes.
export function detectarAnomalia(lecturas: number[]): boolean {
  const ventana = lecturas.slice(-LECTURAS_PARA_ANOMALIA);
  if (ventana.length < LECTURAS_PARA_ANOMALIA) return false;

  let cambiosDeSentido = 0;
  for (let i = 2; i < ventana.length; i++) {
    const anterior = ventana[i - 1] - ventana[i - 2];
    const actual = ventana[i] - ventana[i - 1];
    const saltoGrande =
      Math.abs(anterior) >= SALTO_MINIMO_ANOMALIA && Math.abs(actual) >= SALTO_MINIMO_ANOMALIA;
    if (saltoGrande && Math.sign(anterior) !== Math.sign(actual)) cambiosDeSentido++;
  }
  return cambiosDeSentido >= 3;
}
