-- Nivel del contenedor cuando llegó el camión: sirve para medir si se lo vació en el momento
-- justo (50% o más y antes de desbordar) o demasiado temprano.
ALTER TABLE parada ADD COLUMN nivel_al_llegar SMALLINT CHECK (nivel_al_llegar BETWEEN 0 AND 100);

-- Un resumen por día y por sector, para los indicadores. Compara lo que pasó (ruta dinámica)
-- con lo que habría pasado con rondas fijas a las 8, 14 y 20 visitando todos los contenedores,
-- calculado con las mismas lecturas del día.
CREATE TABLE resumen_diario (
    fecha               DATE NOT NULL,
    sector_id           INTEGER NOT NULL REFERENCES sector (id),
    origen              VARCHAR(20) NOT NULL CHECK (origen IN ('calculado', 'simulado')), -- simulado = historial previo
    km_dinamica         NUMERIC(8, 2) NOT NULL,
    km_fija             NUMERIC(8, 2) NOT NULL,
    visitas             INTEGER NOT NULL,      -- contenedores vaciados
    visitas_bajas       INTEGER NOT NULL,      -- vaciados con menos de 50%
    visitas_justas      INTEGER NOT NULL,      -- vaciados con 50% o más y antes de desbordar
    desbordes           INTEGER NOT NULL,
    visitas_fija        INTEGER NOT NULL,
    visitas_bajas_fija  INTEGER NOT NULL,
    desbordes_fija      INTEGER NOT NULL,
    llenado_por_hora    NUMERIC(5, 1)[] NOT NULL, -- llenado promedio del sector en cada hora (24 valores)
    PRIMARY KEY (fecha, sector_id)
);
