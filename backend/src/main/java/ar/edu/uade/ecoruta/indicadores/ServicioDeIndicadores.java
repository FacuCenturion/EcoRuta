package ar.edu.uade.ecoruta.indicadores;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.Parametros;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.indicadores.RepositorioDeResumenes.Resumen;
import ar.edu.uade.ecoruta.indicadores.SimulacionRutaFija.Lectura;
import ar.edu.uade.ecoruta.rutas.PlanificadorDeRutas;
import ar.edu.uade.ecoruta.rutas.PlanificadorDeRutas.Punto;
import ar.edu.uade.ecoruta.sectores.Sector;
import ar.edu.uade.ecoruta.sectores.SectorRepositorio;
import ar.edu.uade.ecoruta.zonas.Zona;
import ar.edu.uade.ecoruta.zonas.ZonaRepositorio;

/**
 * Resume cada día por sector y arma los indicadores. Los días se cierran solos: cuando entran
 * lecturas de un día nuevo, se resume el anterior.
 */
@Service
public class ServicioDeIndicadores {

    private final NamedParameterJdbcTemplate jdbc;
    private final RepositorioDeResumenes resumenes;
    private final SectorRepositorio sectores;
    private final ZonaRepositorio zonas;
    private final ContenedorRepositorio contenedores;
    private volatile LocalDate ultimoDiaCerrado;

    ServicioDeIndicadores(NamedParameterJdbcTemplate jdbc, RepositorioDeResumenes resumenes, SectorRepositorio sectores,
            ZonaRepositorio zonas, ContenedorRepositorio contenedores) {
        this.jdbc = jdbc;
        this.resumenes = resumenes;
        this.sectores = sectores;
        this.zonas = zonas;
        this.contenedores = contenedores;
    }

    /** Olvida qué días cerró (después de reiniciar la demo). */
    public void olvidar() {
        ultimoDiaCerrado = null;
    }

    /** Resume los días que terminaron y todavía no tienen resumen. Barato si no hay nada pendiente. */
    @Transactional
    public void cerrarDiasPendientes(Instant ahora) {
        LocalDate hoy = ahora.atZone(Fechas.BUENOS_AIRES).toLocalDate();
        if (ultimoDiaCerrado != null && !ultimoDiaCerrado.isBefore(hoy.minusDays(1))) {
            return;
        }
        LocalDate ultimo = resumenes.ultimaFecha();
        if (ultimo == null) {
            ultimo = primerDiaConLecturas();
            if (ultimo == null) {
                return;
            }
            ultimo = ultimo.minusDays(1);
        }
        for (LocalDate dia = ultimo.plusDays(1); dia.isBefore(hoy); dia = dia.plusDays(1)) {
            resumenes.guardar(resumirDia(dia));
        }
        ultimoDiaCerrado = hoy.minusDays(1);
    }

    private LocalDate primerDiaConLecturas() {
        OffsetDateTime primera = jdbc.getJdbcTemplate().queryForObject("SELECT min(medido_at) FROM lectura_sensor",
                OffsetDateTime.class);
        return primera == null ? null : primera.atZoneSameInstant(Fechas.BUENOS_AIRES).toLocalDate();
    }

    /** Un resumen por sector para ese día, con lo que pasó y lo que habría pasado con la ruta fija. */
    @Transactional(readOnly = true)
    public List<Resumen> resumirDia(LocalDate dia) {
        Instant inicio = dia.atStartOfDay(Fechas.BUENOS_AIRES).toInstant();
        Instant fin = dia.plusDays(1).atStartOfDay(Fechas.BUENOS_AIRES).toInstant();
        MapSqlParameterSource datos = new MapSqlParameterSource()
                .addValue("inicio", Fechas.paraLaBase(inicio))
                .addValue("fin", Fechas.paraLaBase(fin))
                .addValue("desdeRondaAnterior", Fechas.paraLaBase(inicio.minusSeconds(4 * 3600)))
                .addValue("zona", Fechas.BUENOS_AIRES.getId());

        Map<Integer, Double> kmDinamica = porSector("""
                SELECT k.sector_id, sum(r.km_planificados) AS valor FROM ruta r JOIN camion k ON k.id = r.camion_id
                WHERE r.creada_at >= :inicio AND r.creada_at < :fin GROUP BY k.sector_id
                """, datos);
        Map<Integer, Double> visitas = porSector("""
                SELECT c.sector_id, count(*) AS valor FROM parada p JOIN contenedor c ON c.id = p.contenedor_id
                WHERE p.estado = 'recolectada' AND p.hora_real >= :inicio AND p.hora_real < :fin GROUP BY c.sector_id
                """, datos);
        Map<Integer, Double> bajas = porSector("""
                SELECT c.sector_id, count(*) AS valor FROM parada p JOIN contenedor c ON c.id = p.contenedor_id
                WHERE p.estado = 'recolectada' AND p.hora_real >= :inicio AND p.hora_real < :fin
                  AND p.nivel_al_llegar < 50 GROUP BY c.sector_id
                """, datos);
        Map<Integer, Double> justas = porSector("""
                SELECT c.sector_id, count(*) AS valor FROM parada p JOIN contenedor c ON c.id = p.contenedor_id
                WHERE p.estado = 'recolectada' AND p.hora_real >= :inicio AND p.hora_real < :fin
                  AND p.nivel_al_llegar >= 50 AND p.nivel_al_llegar < 98 GROUP BY c.sector_id
                """, datos);
        Map<Integer, Double> desbordes = porSector("""
                SELECT c.sector_id, count(*) AS valor FROM incidente i JOIN contenedor c ON c.id = i.contenedor_id
                WHERE i.tipo = 'desborde' AND i.creado_at >= :inicio AND i.creado_at < :fin GROUP BY c.sector_id
                """, datos);
        Map<Integer, double[]> llenadoPorHora = llenadoPorHora(datos);
        Map<String, List<Lectura>> lecturas = lecturasParaLaRutaFija(datos);

        List<Resumen> resultado = new ArrayList<>();
        for (Sector sector : sectores.findAll()) {
            int id = sector.getId();
            int visitasFija = 0;
            int bajasFija = 0;
            int desbordesFija = 0;
            for (Contenedor c : contenedores.findBySectorIdAndActivoTrueOrderById(id)) {
                List<Lectura> suyas = lecturas.getOrDefault(c.getId(), List.of());
                SimulacionRutaFija.Resultado fija = SimulacionRutaFija.simular(suyas, inicio);
                visitasFija += fija.visitas();
                bajasFija += fija.visitasBajas();
                desbordesFija += fija.desbordes();
            }
            resultado.add(new Resumen(dia, id, "calculado", kmDinamica.getOrDefault(id, 0.0),
                    Parametros.RONDAS_RUTA_FIJA.length * kmRutaFija(sector), visitas.getOrDefault(id, 0.0).intValue(),
                    bajas.getOrDefault(id, 0.0).intValue(), justas.getOrDefault(id, 0.0).intValue(),
                    desbordes.getOrDefault(id, 0.0).intValue(), visitasFija, bajasFija, desbordesFija,
                    llenadoPorHora.getOrDefault(id, new double[24])));
        }
        return resultado;
    }

    /** Lo que recorre una vuelta de la ruta fija: todos los contenedores del sector, saliendo de la base. */
    public double kmRutaFija(Sector sector) {
        Zona zona = zonas.findById(sector.getZonaId()).orElseThrow();
        List<Punto> todos = contenedores.findBySectorIdAndActivoTrueOrderById(sector.getId()).stream()
                .map(c -> new Punto(c.getId(), c.posicion())).toList();
        return PlanificadorDeRutas.planificar(new Coordenada(zona.getBaseLat(), zona.getBaseLng()), todos).km();
    }

    private Map<Integer, Double> porSector(String sql, MapSqlParameterSource datos) {
        Map<Integer, Double> resultado = new HashMap<>();
        jdbc.query(sql, datos, fila -> {
            resultado.put(fila.getInt("sector_id"), fila.getDouble("valor"));
        });
        return resultado;
    }

    private Map<Integer, double[]> llenadoPorHora(MapSqlParameterSource datos) {
        Map<Integer, double[]> resultado = new HashMap<>();
        jdbc.query("""
                SELECT c.sector_id, extract(hour FROM l.medido_at AT TIME ZONE :zona)::int AS hora, avg(l.llenado) AS valor
                FROM lectura_sensor l JOIN contenedor c ON c.id = l.contenedor_id
                WHERE l.medido_at >= :inicio AND l.medido_at < :fin
                GROUP BY c.sector_id, hora
                """, datos, fila -> {
            resultado.computeIfAbsent(fila.getInt("sector_id"), s -> new double[24])[fila.getInt("hora")] =
                    fila.getDouble("valor");
        });
        return resultado;
    }

    private Map<String, List<Lectura>> lecturasParaLaRutaFija(MapSqlParameterSource datos) {
        Map<String, List<Lectura>> resultado = new HashMap<>();
        jdbc.query("""
                SELECT contenedor_id, medido_at, llenado FROM lectura_sensor
                WHERE medido_at >= :desdeRondaAnterior AND medido_at < :fin
                ORDER BY contenedor_id, medido_at
                """, datos, fila -> {
            resultado.computeIfAbsent(fila.getString("contenedor_id"), id -> new ArrayList<>()).add(new Lectura(
                    fila.getObject("medido_at", OffsetDateTime.class).toInstant(), fila.getInt("llenado")));
        });
        return resultado;
    }

    // ---------- Indicadores para el panel ----------

    /**
     * @param sectorId vacío = toda la zona
     * @param dias     cuántos días hacia atrás para los totales, el llenado por hora y la eficiencia
     */
    @Transactional(readOnly = true)
    public IndicadoresDto calcular(Integer sectorId, int dias, Instant ahora) {
        LocalDate hoy = ahora.atZone(Fechas.BUENOS_AIRES).toLocalDate();
        LocalDate desde = hoy.minusDays(dias);
        LocalDate desdeSemanas = hoy.minusWeeks(IndicadoresDto.SEMANAS);

        List<Resumen> todos = new ArrayList<>(resumenes.desde(desdeSemanas.isBefore(desde) ? desdeSemanas : desde, sectorId));
        // Hoy va calculado en el momento: todavía no tiene resumen guardado
        resumirDia(hoy).stream().filter(r -> sectorId == null || r.sectorId() == sectorId).forEach(todos::add);

        List<Resumen> periodo = todos.stream().filter(r -> !r.fecha().isBefore(desde)).toList();

        Map<LocalDate, List<Resumen>> porDia = periodo.stream()
                .collect(Collectors.groupingBy(Resumen::fecha, java.util.TreeMap::new, Collectors.toList()));
        List<IndicadoresDto.Dia> listaDias = porDia.entrySet().stream().map(e -> new IndicadoresDto.Dia(e.getKey(),
                redondear(e.getValue().stream().mapToDouble(Resumen::kmFija).sum()),
                redondear(e.getValue().stream().mapToDouble(Resumen::kmDinamica).sum()),
                e.getValue().stream().mapToInt(Resumen::desbordesFija).sum(),
                e.getValue().stream().mapToInt(Resumen::desbordes).sum(),
                e.getValue().stream().allMatch(r -> r.origen().equals("simulado")))).toList();

        List<IndicadoresDto.Semana> semanas = new ArrayList<>();
        for (int s = IndicadoresDto.SEMANAS; s >= 1; s--) {
            LocalDate inicio = hoy.minusWeeks(s).plusDays(1);
            LocalDate fin = inicio.plusDays(6);
            List<Resumen> deLaSemana = todos.stream()
                    .filter(r -> !r.fecha().isBefore(inicio) && !r.fecha().isAfter(fin)).toList();
            semanas.add(new IndicadoresDto.Semana("S" + (IndicadoresDto.SEMANAS - s + 1), inicio, fin,
                    deLaSemana.stream().mapToInt(Resumen::desbordesFija).sum(),
                    deLaSemana.stream().mapToInt(Resumen::desbordes).sum()));
        }

        double kmFija = periodo.stream().mapToDouble(Resumen::kmFija).sum();
        double kmDinamica = periodo.stream().mapToDouble(Resumen::kmDinamica).sum();
        double kmEvitados = Math.max(0, kmFija - kmDinamica);

        Map<Integer, Double> kmEvitadosPorSector = new LinkedHashMap<>();
        Map<Integer, Double> eficienciaPorSector = new LinkedHashMap<>();
        periodo.stream().collect(Collectors.groupingBy(Resumen::sectorId, java.util.TreeMap::new, Collectors.toList()))
                .forEach((sector, lista) -> {
                    kmEvitadosPorSector.put(sector, redondear(Math.max(0,
                            lista.stream().mapToDouble(Resumen::kmFija).sum() - lista.stream().mapToDouble(Resumen::kmDinamica).sum())));
                    int v = lista.stream().mapToInt(Resumen::visitas).sum();
                    eficienciaPorSector.put(sector,
                            v == 0 ? 0 : redondear(100.0 * lista.stream().mapToInt(Resumen::visitasJustas).sum() / v));
                });

        double[] llenadoPorHora = new double[24];
        Map<Integer, double[][]> sumaCalor = new HashMap<>();
        Map<Integer, int[][]> cuentaCalor = new HashMap<>();
        for (Resumen r : periodo) {
            int diaSemana = r.fecha().getDayOfWeek().getValue() - 1;
            double[][] suma = sumaCalor.computeIfAbsent(r.sectorId(), s -> new double[7][24]);
            int[][] cuenta = cuentaCalor.computeIfAbsent(r.sectorId(), s -> new int[7][24]);
            for (int h = 0; h < 24; h++) {
                if (r.llenadoPorHora()[h] > 0) {
                    suma[diaSemana][h] += r.llenadoPorHora()[h];
                    cuenta[diaSemana][h]++;
                }
            }
        }
        Map<Integer, double[][]> mapaCalor = new LinkedHashMap<>();
        int[] cuentaPorHora = new int[24];
        new java.util.TreeMap<>(sumaCalor).forEach((sector, suma) -> {
            double[][] promedio = new double[7][24];
            for (int d = 0; d < 7; d++) {
                for (int h = 0; h < 24; h++) {
                    int n = cuentaCalor.get(sector)[d][h];
                    promedio[d][h] = n == 0 ? 0 : redondear(suma[d][h] / n);
                    llenadoPorHora[h] += suma[d][h];
                    cuentaPorHora[h] += n;
                }
            }
            mapaCalor.put(sector, promedio);
        });
        for (int h = 0; h < 24; h++) {
            llenadoPorHora[h] = cuentaPorHora[h] == 0 ? 0 : redondear(llenadoPorHora[h] / cuentaPorHora[h]);
        }

        int visitas = periodo.stream().mapToInt(Resumen::visitas).sum();
        int visitasFija = periodo.stream().mapToInt(Resumen::visitasFija).sum();
        return new IndicadoresDto(desde, hoy, sectorId, redondear(kmFija), redondear(kmDinamica), redondear(kmEvitados),
                redondear(kmEvitados * Parametros.KG_CO2_POR_KM),
                visitasFija == 0 ? 0 : redondear(100.0 * periodo.stream().mapToInt(Resumen::visitasBajasFija).sum() / visitasFija),
                visitas == 0 ? 0 : redondear(100.0 * periodo.stream().mapToInt(Resumen::visitasBajas).sum() / visitas),
                periodo.stream().mapToInt(Resumen::desbordesFija).sum(), periodo.stream().mapToInt(Resumen::desbordes).sum(),
                (int) porDia.values().stream().filter(l -> l.stream().allMatch(r -> r.origen().equals("simulado"))).count(),
                listaDias, semanas, kmEvitadosPorSector, eficienciaPorSector, llenadoPorHora, mapaCalor);
    }

    private static double redondear(double valor) {
        return Math.round(valor * 10) / 10.0;
    }
}
