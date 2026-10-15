-- Patrón de llenado aprendido de cada contenedor: cuánto se llenó, y durante cuántas horas
-- se lo observó, en cada hora de cada día de la semana. El ritmo es suma_delta / suma_horas.
-- Se recalcula una vez por día con las lecturas de las últimas dos semanas.
CREATE TABLE patron_llenado (
    contenedor_id  VARCHAR(10) NOT NULL REFERENCES contenedor (id),
    dia_semana     SMALLINT NOT NULL CHECK (dia_semana BETWEEN 0 AND 6), -- lunes = 0
    hora           SMALLINT NOT NULL CHECK (hora BETWEEN 0 AND 23),
    suma_delta     NUMERIC(10, 2) NOT NULL, -- puntos de llenado que sumó
    suma_horas     NUMERIC(10, 4) NOT NULL, -- horas observadas
    PRIMARY KEY (contenedor_id, dia_semana, hora)
);

-- Por qué el camión pasa por cada parada: llenado, riesgo, reporte, tiempo o de_paso
ALTER TABLE parada ADD COLUMN motivo_visita VARCHAR(20) NOT NULL DEFAULT 'llenado'
    CHECK (motivo_visita IN ('llenado', 'riesgo', 'reporte', 'tiempo', 'de_paso'));

-- Ahora se guardan dos semanas de lecturas: hace falta buscar por fecha sin recorrer todo
CREATE INDEX idx_lectura_fecha ON lectura_sensor (medido_at);
