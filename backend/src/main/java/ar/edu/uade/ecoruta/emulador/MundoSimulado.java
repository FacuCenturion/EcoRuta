package ar.edu.uade.ecoruta.emulador;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.contenedores.Contenedor;

/**
 * La "verdad" de la simulación: cuánta basura genera cada contenedor y cuándo. El sistema no
 * la ve nunca; le llegan solo las lecturas. Todo se deriva de una semilla y del id de cada
 * contenedor, así que es igual en cada arranque y en cada reinicio de la demo.
 */
@Component
@ConditionalOnProperty(name = "ecoruta.emulador.activo", havingValue = "true")
class MundoSimulado {

    private static final long SEMILLA = 2026;
    private static final double PROPORCION_POCO_USO = 0.06; // casi no se llenan: los cubre la regla de 48 h

    /** Lo que se sabe de un contenedor en el mundo simulado. */
    record Verdad(PerfilDeBarrio perfil, double litrosPorHora, boolean pocoUso) {
    }

    /** Un evento masivo (feria, recital, partido) que hace que un sector genere más basura un rato. */
    record Evento(Instant hasta, double factor) {
    }

    private final Map<String, Verdad> verdades = new HashMap<>();
    private final Map<Integer, Evento> eventos = new HashMap<>();

    Verdad verdad(Contenedor contenedor) {
        return verdades.computeIfAbsent(contenedor.getId(), id -> crear(contenedor));
    }

    private static Verdad crear(Contenedor contenedor) {
        Random azar = new Random(SEMILLA * 31 + contenedor.getId().hashCode());
        PerfilDeBarrio perfil = switch (contenedor.getBarrio()) {
            case "Retiro", "San Nicolás" -> PerfilDeBarrio.OFICINAS;
            case "San Telmo" -> PerfilDeBarrio.GASTRONOMICO;
            // Puerto Madero mezcla oficinas y restaurantes; Monserrat, oficinas y viviendas
            case "Puerto Madero" -> azar.nextBoolean() ? PerfilDeBarrio.OFICINAS : PerfilDeBarrio.GASTRONOMICO;
            case "Monserrat" -> azar.nextDouble() < 0.4 ? PerfilDeBarrio.OFICINAS : PerfilDeBarrio.RESIDENCIAL;
            default -> PerfilDeBarrio.RESIDENCIAL;
        };
        boolean pocoUso = azar.nextDouble() < PROPORCION_POCO_USO;
        double litros = pocoUso ? 8 + azar.nextDouble() * 7 : 50 + azar.nextDouble() * 60;
        return new Verdad(perfil, litros, pocoUso);
    }

    /** Cuántos puntos de llenado por hora suma el contenedor en ese momento (sin ruido). */
    double ritmoPorHora(Contenedor contenedor, Instant momento) {
        Verdad verdad = verdad(contenedor);
        double factor = verdad.perfil().factor(momento) * factorDeEvento(contenedor.getSectorId(), momento);
        // La feria de San Telmo: los domingos de 10 a 18 el barrio se llena de gente
        ZonedDateTime local = momento.atZone(Fechas.BUENOS_AIRES);
        if (contenedor.getBarrio().equals("San Telmo") && local.getDayOfWeek() == DayOfWeek.SUNDAY
                && local.getHour() >= 10 && local.getHour() < 18) {
            factor *= 2;
        }
        return verdad.litrosPorHora() * factor / contenedor.getCapacidadLitros() * 100;
    }

    double factorDeEvento(int sectorId, Instant momento) {
        Evento evento = eventos.get(sectorId);
        return evento != null && momento.isBefore(evento.hasta()) ? evento.factor() : 1;
    }

    void iniciarEvento(int sectorId, Instant desde, int horas, double factor) {
        eventos.put(sectorId, new Evento(desde.plus(Duration.ofHours(horas)), factor));
    }

    boolean hayEventoEn(int sectorId, Instant momento) {
        return factorDeEvento(sectorId, momento) > 1;
    }

    void olvidarEventos() {
        eventos.clear();
    }
}
