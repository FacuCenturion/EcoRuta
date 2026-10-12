// Parámetros generales del sistema. Se concentran acá para poder ajustarlos
// sin tener que buscarlos por todo el código.

// Umbrales de llenado (en %)
export const UMBRAL_BAJO = 50;
export const UMBRAL_PRIORITARIO = 80; // criterio de la HU de priorización
export const UMBRAL_DESBORDE = 95;
// Margen de seguridad: se considera en riesgo si se proyecta llegar a este nivel antes de la próxima ronda
export const UMBRAL_RIESGO_PROYECTADO = 95;

// Rondas de recolección por hora del día.
// Con ruta fija salen siempre a las mismas horas; con ruta dinámica se suma
// una ronda de refuerzo a las 17 para atender lo que se llena en el pico.
export const RONDAS_RUTA_FIJA = [8, 14, 20];
export const HORAS_DE_RONDA = [8, 14, 17, 20];

// Simulación
export const MINUTOS_POR_TICK = 5; // minutos simulados que avanza cada actualización
export const MS_POR_TICK = 4000; // milisegundos reales entre actualizaciones
export const HORA_INICIAL = 17; // hora simulada con la que arranca el sistema

// Camiones
export const MAX_PARADAS_POR_RUTA = 14;
export const VELOCIDAD_CAMION_KMH = 20;
export const MINUTOS_POR_PARADA = 5;
export const CARGA_POR_PARADA = 5; // % de carga del camión que suma cada contenedor
export const PROBABILIDAD_NO_SE_PUDO = 0.08;

// Anomalías de sensor
export const SALTO_MINIMO_ANOMALIA = 4; // variación (en %) para considerarla un salto
export const LECTURAS_PARA_ANOMALIA = 6;

// Estado de los sensores. Como los sensores solo informan el nivel de llenado,
// las fallas se deducen de las lecturas.
export const HORAS_SIN_SENAL = 2; // sin lecturas en este tiempo, el sensor se considera caído
export const HORAS_VALOR_CONGELADO = 4; // misma lectura durante este tiempo
export const NIVEL_LLENO = 98; // un contenedor lleno marca siempre lo mismo y eso no es una falla
export const NIVEL_TRAS_RECOLECCION = 4; // nivel que se asume apenas el chofer vacía el contenedor
export const HORAS_DE_HISTORIAL = 24;
export const PROBABILIDAD_FALLA_SENSOR = 0.01; // por actualización, en algún sensor de la ciudad

// Indicadores ambientales (valor estimado, se puede ajustar)
export const KG_CO2_POR_KM = 1.3;

// Un camión sale a la calle cuando hay al menos esta cantidad de contenedores
// para recolectar (o alguno ya está desbordado).
export const MIN_PARADAS_PARA_SALIR = 3;
export const TICKS_DE_DESCARGA = 2;
