// Generador de números pseudoaleatorios con semilla (mulberry32).
// Con la misma semilla siempre da la misma secuencia, así todo el equipo
// ve los mismos datos de ejemplo.
export function crearAleatorio(semilla: number) {
  let a = semilla >>> 0;
  return function siguiente(): number {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export type Aleatorio = ReturnType<typeof crearAleatorio>;

export function entre(azar: Aleatorio, min: number, max: number): number {
  return min + azar() * (max - min);
}

export function elegir<T>(azar: Aleatorio, lista: T[]): T {
  return lista[Math.floor(azar() * lista.length)];
}
