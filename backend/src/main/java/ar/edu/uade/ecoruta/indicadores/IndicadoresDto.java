package ar.edu.uade.ecoruta.indicadores;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Lo que muestra la pantalla Indicadores. "Fija" es lo que habría pasado con rondas a las 8, 14 y 20
 * visitando todos los contenedores; "dinámica" es lo que pasó con el despacho a demanda.
 *
 * @param porcentajeVisitasBajasFija     de cada 100 contenedores vaciados con la ruta fija, cuántos tenían menos de 50%
 * @param porcentajeVisitasBajasDinamica lo mismo con la ruta dinámica
 * @param diasSimulados                  cuántos días del período son del historial simulado previo
 * @param eficienciaPorSector            de cada 100 vaciados, cuántos en el momento justo (50% o más, antes de desbordar)
 * @param llenadoPorHora                 llenado promedio en cada hora del día (24 valores)
 * @param mapaCalor                      por sector: llenado promedio por día de la semana (lunes = 0) y hora
 */
public record IndicadoresDto(LocalDate desde, LocalDate hasta, Integer sectorId, double kmFija, double kmDinamica,
        double kmEvitados, double co2EvitadoKg, double porcentajeVisitasBajasFija, double porcentajeVisitasBajasDinamica,
        int desbordesFija, int desbordesDinamica, int diasSimulados, List<Dia> dias, List<Semana> semanas,
        Map<Integer, Double> kmEvitadosPorSector, Map<Integer, Double> eficienciaPorSector, double[] llenadoPorHora,
        Map<Integer, double[][]> mapaCalor) {

    static final int SEMANAS = 8;

    /** @param simulado el día es del historial previo, no de la operación del sistema */
    public record Dia(LocalDate fecha, double kmFija, double kmDinamica, int desbordesFija, int desbordesDinamica,
            boolean simulado) {
    }

    public record Semana(String etiqueta, LocalDate desde, LocalDate hasta, int desbordesFija, int desbordesDinamica) {
    }
}
