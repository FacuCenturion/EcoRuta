package ar.edu.uade.ecoruta.contenedores;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;
import ar.edu.uade.ecoruta.incidentes.IncidenteRepositorio;
import ar.edu.uade.ecoruta.sensores.RepositorioDeLecturas;

/** Junta los datos que necesita {@link Prioridad} para evaluar muchos contenedores de una vez. */
@Service
public class ServicioDePrioridad {

    private final IncidenteRepositorio incidentes;
    private final RepositorioDeLecturas lecturas;

    ServicioDePrioridad(IncidenteRepositorio incidentes, RepositorioDeLecturas lecturas) {
        this.incidentes = incidentes;
        this.lecturas = lecturas;
    }

    /** Cuántos reportes vecinales abiertos tiene cada contenedor. */
    public Map<String, Long> reportesVecinales() {
        return incidentes.reportesVecinalesAbiertosPorContenedor();
    }

    public Map<String, Evaluacion> evaluar(Collection<Contenedor> contenedores, Instant ahora) {
        Map<String, Long> reportes = reportesVecinales();

        // Solo para los que tienen lecturas irregulares hace falta el promedio de las últimas tres
        List<String> irregulares = contenedores.stream().filter(Contenedor::isAnomalia).map(Contenedor::getId).toList();
        Map<String, List<Integer>> ultimasTres = lecturas.ultimas(irregulares, 3);

        Map<String, Evaluacion> resultado = new HashMap<>();
        for (Contenedor c : contenedores) {
            List<Integer> tres = ultimasTres.get(c.getId());
            Double promedio = tres == null ? null : tres.stream().mapToInt(Integer::intValue).average().orElse(c.getLlenado());
            resultado.put(c.getId(), Prioridad.evaluar(c.getLlenado(), c.getNivelEstimado(), c.isAnomalia(), promedio,
                    c.velocidad(), ahora, reportes.getOrDefault(c.getId(), 0L)));
        }
        return resultado;
    }
}
