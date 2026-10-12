package ar.edu.uade.ecoruta.comun;

/**
 * Parámetros generales del sistema, concentrados acá para ajustarlos sin buscarlos
 * por todo el código. Son los mismos valores que src/config.ts del front.
 */
public final class Parametros {

    // Umbrales de llenado (en %)
    public static final int UMBRAL_BAJO = 50;
    public static final int UMBRAL_PRIORITARIO = 80;
    public static final int UMBRAL_DESBORDE = 95;
    // Un contenedor está en riesgo si se proyecta que llega a este nivel dentro de HORAS_DE_PROYECCION
    public static final int UMBRAL_RIESGO_PROYECTADO = 95;
    public static final int HORAS_DE_PROYECCION = 3;

    // Camiones y rutas
    public static final int MAX_PARADAS_POR_RUTA = 14;
    public static final double VELOCIDAD_CAMION_KMH = 20;
    public static final int MINUTOS_POR_PARADA = 5;
    public static final int CARGA_POR_PARADA = 5; // % de carga del camión que suma cada contenedor
    // Un camión sale cuando hay al menos esta cantidad de contenedores para recolectar,
    // o cuando alguno ya está desbordado.
    public static final int MIN_PARADAS_PARA_SALIR = 3;

    // Anomalías de sensor
    public static final int SALTO_MINIMO_ANOMALIA = 4; // variación (en %) para considerarla un salto
    public static final int LECTURAS_PARA_ANOMALIA = 6;

    // Estado de los sensores: las fallas se deducen de las lecturas
    public static final int HORAS_SIN_SENAL = 2; // sin lecturas en este tiempo, el sensor se considera caído
    public static final int HORAS_VALOR_CONGELADO = 4; // misma lectura durante este tiempo
    public static final int NIVEL_LLENO = 98; // un contenedor lleno marca siempre lo mismo y no es una falla
    public static final int NIVEL_TRAS_RECOLECCION = 4; // nivel que se asume apenas el chofer lo vacía

    private Parametros() {
    }
}
