package ar.edu.uade.ecoruta.emulador;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.Parametros;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.indicadores.RepositorioDeResumenes;
import ar.edu.uade.ecoruta.indicadores.RepositorioDeResumenes.Resumen;
import ar.edu.uade.ecoruta.indicadores.ServicioDeIndicadores;
import ar.edu.uade.ecoruta.rutas.PlanificadorDeRutas;
import ar.edu.uade.ecoruta.rutas.PlanificadorDeRutas.Punto;
import ar.edu.uade.ecoruta.sectores.Sector;
import ar.edu.uade.ecoruta.sectores.SectorRepositorio;
import ar.edu.uade.ecoruta.zonas.Zona;
import ar.edu.uade.ecoruta.zonas.ZonaRepositorio;

/**
 * La primera vez que arranca la demo, genera 8 semanas de resúmenes diarios para que los
 * indicadores no empiecen vacíos. Se marcan como "simulado": no son la operación del sistema.
 *
 * Hora por hora, los contenedores se llenan con la misma verdad del emulador, y se aplican las dos
 * políticas sobre los mismos datos: la ruta fija (rondas a las 8, 14 y 20 que vacían todo) y una
 * versión simplificada del despacho a demanda (cada hora, los prioritarios del sector y las
 * paradas de paso, sin límite de camiones).
 */
@Component
@ConditionalOnProperty(name = "ecoruta.emulador.activo", havingValue = "true")
class HistorialDeIndicadores {

    private static final int DIAS = 56;
    private static final int DIAS_DE_ARRANQUE = 2; // se simulan pero no se guardan: el modelo se acomoda

    private final MundoSimulado mundo;
    private final ContenedorRepositorio contenedores;
    private final SectorRepositorio sectores;
    private final ZonaRepositorio zonas;
    private final RepositorioDeResumenes resumenes;
    private final ServicioDeIndicadores indicadores;

    HistorialDeIndicadores(MundoSimulado mundo, ContenedorRepositorio contenedores, SectorRepositorio sectores,
            ZonaRepositorio zonas, RepositorioDeResumenes resumenes, ServicioDeIndicadores indicadores) {
        this.mundo = mundo;
        this.contenedores = contenedores;
        this.sectores = sectores;
        this.zonas = zonas;
        this.resumenes = resumenes;
        this.indicadores = indicadores;
    }

    /** Lo genera solo si todavía no hay ningún resumen guardado. */
    void generarSiFalta(Instant ahora) {
        if (resumenes.ultimaFecha() != null) {
            return;
        }
        LocalDate hoy = ahora.atZone(Fechas.BUENOS_AIRES).toLocalDate();
        Random azar = new Random(2026 + 7);
        List<Resumen> todos = new ArrayList<>();
        for (Sector sector : sectores.findAll()) {
            todos.addAll(simularSector(sector, hoy, azar));
        }
        resumenes.guardar(todos);
    }

    private List<Resumen> simularSector(Sector sector, LocalDate hoy, Random azar) {
        List<Contenedor> lista = contenedores.findBySectorIdAndActivoTrueOrderById(sector.getId());
        Zona zona = zonas.findById(sector.getZonaId()).orElseThrow();
        Coordenada base = new Coordenada(zona.getBaseLat(), zona.getBaseLng());
        double kmFijaPorRonda = indicadores.kmRutaFija(sector);

        Map<String, Double> nivelFija = new HashMap<>();
        Map<String, Double> nivelDinamica = new HashMap<>();
        Map<String, Integer> horasSinVaciar = new HashMap<>();
        for (Contenedor c : lista) {
            double inicial = 20 + azar.nextDouble() * 40;
            nivelFija.put(c.getId(), inicial);
            nivelDinamica.put(c.getId(), inicial);
            horasSinVaciar.put(c.getId(), 0);
        }

        List<Resumen> resultado = new ArrayList<>();
        for (int d = DIAS + DIAS_DE_ARRANQUE; d >= 1; d--) {
            LocalDate dia = hoy.minusDays(d);
            double kmDinamica = 0;
            int visitas = 0, bajas = 0, justas = 0, desbordes = 0, visitasFija = 0, bajasFija = 0, desbordesFija = 0;
            double[] llenadoPorHora = new double[24];

            for (int hora = 0; hora < 24; hora++) {
                Instant momento = dia.atTime(hora, 30).atZone(Fechas.BUENOS_AIRES).toInstant();
                for (Contenedor c : lista) {
                    double incremento = mundo.ritmoPorHora(c, momento) * (0.7 + azar.nextDouble() * 0.6);
                    double antesFija = nivelFija.get(c.getId());
                    double antesDinamica = nivelDinamica.get(c.getId());
                    nivelFija.put(c.getId(), Math.min(100, antesFija + incremento));
                    nivelDinamica.put(c.getId(), Math.min(100, antesDinamica + incremento));
                    // Un desborde se cuenta una vez, cuando cruza el tope
                    if (antesFija < Parametros.NIVEL_LLENO && antesFija + incremento >= Parametros.NIVEL_LLENO) {
                        desbordesFija++;
                    }
                    if (antesDinamica < Parametros.NIVEL_LLENO && antesDinamica + incremento >= Parametros.NIVEL_LLENO) {
                        desbordes++;
                    }
                    horasSinVaciar.merge(c.getId(), 1, Integer::sum);
                }

                // Ruta fija: a las 8, 14 y 20 se vacía todo
                for (int ronda : Parametros.RONDAS_RUTA_FIJA) {
                    if (ronda == hora) {
                        for (Contenedor c : lista) {
                            visitasFija++;
                            if (nivelFija.get(c.getId()) < Parametros.UMBRAL_BAJO) {
                                bajasFija++;
                            }
                            nivelFija.put(c.getId(), (double) Parametros.NIVEL_TRAS_RECOLECCION);
                        }
                    }
                }

                // Despacho a demanda simplificado: cada hora, si hace falta, sale con los prioritarios
                List<Contenedor> prioritarios = lista.stream().filter(c -> esPrioritario(c, nivelDinamica,
                        horasSinVaciar, momento)).toList();
                boolean hayDesbordado = lista.stream()
                        .anyMatch(c -> nivelDinamica.get(c.getId()) >= Parametros.UMBRAL_DESBORDE);
                if (prioritarios.size() >= Parametros.MIN_PARADAS_PARA_SALIR || (hayDesbordado && !prioritarios.isEmpty())) {
                    List<Contenedor> visitados = new ArrayList<>(prioritarios);
                    lista.stream()
                            .filter(c -> !prioritarios.contains(c) && nivelDinamica.get(c.getId()) >= Parametros.NIVEL_PARADA_DE_PASO)
                            .filter(c -> prioritarios.stream().anyMatch(p -> PlanificadorDeRutas.distanciaKm(c.posicion(),
                                    p.posicion()) <= Parametros.RADIO_PARADA_DE_PASO_KM))
                            .forEach(visitados::add);
                    kmDinamica += PlanificadorDeRutas.planificar(base,
                            visitados.stream().map(c -> new Punto(c.getId(), c.posicion())).toList()).km();
                    for (Contenedor c : visitados) {
                        double nivel = nivelDinamica.get(c.getId());
                        visitas++;
                        if (nivel < Parametros.UMBRAL_BAJO) {
                            bajas++;
                        } else if (nivel < Parametros.NIVEL_LLENO) {
                            justas++;
                        }
                        nivelDinamica.put(c.getId(), (double) Parametros.NIVEL_TRAS_RECOLECCION);
                        horasSinVaciar.put(c.getId(), 0);
                    }
                }
                llenadoPorHora[hora] = lista.stream().mapToDouble(c -> nivelDinamica.get(c.getId())).average().orElse(0);
            }

            if (d <= DIAS) {
                resultado.add(new Resumen(dia, sector.getId(), "simulado", kmDinamica,
                        Parametros.RONDAS_RUTA_FIJA.length * kmFijaPorRonda, visitas, bajas, justas, desbordes,
                        visitasFija, bajasFija, desbordesFija, llenadoPorHora));
            }
        }
        return resultado;
    }

    // La misma regla que el sistema: 80% o más, desborde en las próximas 3 horas, o 48 h sin vaciar
    private boolean esPrioritario(Contenedor c, Map<String, Double> niveles, Map<String, Integer> horasSinVaciar,
            Instant momento) {
        double nivel = niveles.get(c.getId());
        if (nivel >= Parametros.UMBRAL_PRIORITARIO) {
            return true;
        }
        double proyectado = nivel;
        for (int h = 1; h <= Parametros.HORAS_DE_PROYECCION; h++) {
            proyectado += mundo.ritmoPorHora(c, momento.plusSeconds(h * 3600L));
        }
        return proyectado >= Parametros.UMBRAL_RIESGO_PROYECTADO
                || (horasSinVaciar.get(c.getId()) > Parametros.HORAS_MAX_SIN_RECOLECTAR
                        && nivel >= Parametros.NIVEL_MINIMO_POR_TIEMPO);
    }
}
