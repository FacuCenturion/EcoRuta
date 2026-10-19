package ar.edu.uade.ecoruta.indicadores;

import java.math.BigDecimal;
import java.sql.Array;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso a resumen_diario. */
@Repository
public class RepositorioDeResumenes {

    /** Un día de un sector. */
    public record Resumen(LocalDate fecha, int sectorId, String origen, double kmDinamica, double kmFija, int visitas,
            int visitasBajas, int visitasJustas, int desbordes, int visitasFija, int visitasBajasFija,
            int desbordesFija, double[] llenadoPorHora) {
    }

    private final JdbcTemplate jdbc;

    RepositorioDeResumenes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void guardar(List<Resumen> resumenes) {
        jdbc.batchUpdate("""
                INSERT INTO resumen_diario (fecha, sector_id, origen, km_dinamica, km_fija, visitas, visitas_bajas,
                    visitas_justas, desbordes, visitas_fija, visitas_bajas_fija, desbordes_fija, llenado_por_hora)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (fecha, sector_id) DO UPDATE SET origen = EXCLUDED.origen,
                    km_dinamica = EXCLUDED.km_dinamica, km_fija = EXCLUDED.km_fija, visitas = EXCLUDED.visitas,
                    visitas_bajas = EXCLUDED.visitas_bajas, visitas_justas = EXCLUDED.visitas_justas,
                    desbordes = EXCLUDED.desbordes, visitas_fija = EXCLUDED.visitas_fija,
                    visitas_bajas_fija = EXCLUDED.visitas_bajas_fija, desbordes_fija = EXCLUDED.desbordes_fija,
                    llenado_por_hora = EXCLUDED.llenado_por_hora
                """, resumenes, resumenes.size(), (fila, r) -> {
            fila.setObject(1, r.fecha());
            fila.setInt(2, r.sectorId());
            fila.setString(3, r.origen());
            fila.setBigDecimal(4, BigDecimal.valueOf(r.kmDinamica()).setScale(2, java.math.RoundingMode.HALF_UP));
            fila.setBigDecimal(5, BigDecimal.valueOf(r.kmFija()).setScale(2, java.math.RoundingMode.HALF_UP));
            fila.setInt(6, r.visitas());
            fila.setInt(7, r.visitasBajas());
            fila.setInt(8, r.visitasJustas());
            fila.setInt(9, r.desbordes());
            fila.setInt(10, r.visitasFija());
            fila.setInt(11, r.visitasBajasFija());
            fila.setInt(12, r.desbordesFija());
            Double[] horas = new Double[24];
            for (int h = 0; h < 24; h++) {
                horas[h] = Math.round(r.llenadoPorHora()[h] * 10) / 10.0;
            }
            fila.setArray(13, fila.getConnection().createArrayOf("numeric", horas));
        });
    }

    public LocalDate ultimaFecha() {
        return jdbc.queryForObject("SELECT max(fecha) FROM resumen_diario", LocalDate.class);
    }

    public List<Resumen> desde(LocalDate desde, Integer sectorId) {
        return jdbc.query("""
                SELECT * FROM resumen_diario WHERE fecha >= ? AND (?::int IS NULL OR sector_id = ?)
                ORDER BY fecha, sector_id
                """, (fila, n) -> {
            Array arreglo = fila.getArray("llenado_por_hora");
            Object[] valores = (Object[]) arreglo.getArray();
            double[] horas = new double[24];
            for (int h = 0; h < Math.min(24, valores.length); h++) {
                horas[h] = ((Number) valores[h]).doubleValue();
            }
            return new Resumen(fila.getObject("fecha", LocalDate.class), fila.getInt("sector_id"),
                    fila.getString("origen"), fila.getDouble("km_dinamica"), fila.getDouble("km_fija"),
                    fila.getInt("visitas"), fila.getInt("visitas_bajas"), fila.getInt("visitas_justas"),
                    fila.getInt("desbordes"), fila.getInt("visitas_fija"), fila.getInt("visitas_bajas_fija"),
                    fila.getInt("desbordes_fija"), horas);
        }, desde, sectorId, sectorId);
    }

    public static List<Resumen> vacio() {
        return new ArrayList<>();
    }
}
