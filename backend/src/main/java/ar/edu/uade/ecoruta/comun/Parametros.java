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
    // Hasta cuánto hacia adelante se busca la hora en la que un contenedor va a desbordar
    public static final int HORAS_MAX_DE_PRONOSTICO = 48;

    // Garantía de servicio: nada queda más de este tiempo sin recolectar, aunque no se llene.
    // No aplica a un contenedor casi vacío: no vale la pena mandar un camión por eso.
    public static final int HORAS_MAX_SIN_RECOLECTAR = 48;
    public static final int NIVEL_MINIMO_POR_TIEMPO = 20;

    // Paradas de paso: cuando un camión ya sale, suma los contenedores a medio llenar
    // que le quedan cerca de alguna parada, para no tener que volver al día siguiente.
    // Se probó con 60% y 200 m y fue peor: en 3 días simulados hubo 129 salidas y 330 km,
    // contra 99 salidas y 275 km con estos valores.
    public static final int NIVEL_PARADA_DE_PASO = 50;
    public static final double RADIO_PARADA_DE_PASO_KM = 0.3;

    // Indicadores
    public static final int[] RONDAS_RUTA_FIJA = { 8, 14, 20 }; // horas a las que sale la ruta fija de la comparación
    public static final double KG_CO2_POR_KM = 1.3; // camión recolector, valor estimado

    // Patrón de llenado aprendido
    public static final int DIAS_DE_LECTURAS_PARA_EL_PATRON = 14;
    public static final double RITMO_MAXIMO_CREIBLE = 40; // % por hora; más que eso es un salto del sensor

    // Camiones y rutas
    public static final int MAX_PARADAS_POR_RUTA = 14;
    public static final double VELOCIDAD_CAMION_KMH = 20;
    public static final int MINUTOS_POR_PARADA = 5;
    public static final int CARGA_POR_PARADA = 5; // % de carga del camión que suma cada contenedor
    // Un camión sale cuando hay al menos esta cantidad de contenedores para recolectar,
    // o cuando alguno ya está desbordado.
    public static final int MIN_PARADAS_PARA_SALIR = 3;
    // Un contenedor que se pronostica que desborda antes de esto es urgente: se visita primero
    public static final int MINUTOS_DE_URGENCIA = 90;

    // Anomalías de sensor
    public static final int SALTO_MINIMO_ANOMALIA = 4; // variación (en %) para considerarla un salto
    public static final int LECTURAS_PARA_ANOMALIA = 6;

    // Estado de los sensores: las fallas se deducen de las lecturas
    public static final int HORAS_SIN_SENAL = 2; // sin lecturas en este tiempo, el sensor se considera caído
    public static final int HORAS_VALOR_CONGELADO = 4; // misma lectura durante este tiempo
    public static final int NIVEL_LLENO = 98; // un contenedor lleno marca siempre lo mismo y no es una falla
    public static final int NIVEL_TRAS_RECOLECCION = 4; // nivel que se asume apenas el chofer lo vacía
    // Sensor trabado: después de vaciarlo, sigue marcando casi lo mismo que antes (a menos de esto)
    public static final int TOLERANCIA_SENSOR_TRABADO = 10;
    public static final int NIVEL_MINIMO_PARA_TRABADO = 40; // si estaba casi vacío no se puede saber
    // Un contenedor no se vuelve a llenar en tan poco tiempo: lecturas de tope recién vaciado no son desborde
    public static final int HORAS_SIN_DESBORDE_TRAS_RECOLECCION = 2;

    private Parametros() {
    }
}
