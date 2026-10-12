-- Estado de cada sensor, deducido de sus lecturas (el sensor solo informa el nivel).
ALTER TABLE contenedor ADD COLUMN estado_sensor VARCHAR(20) NOT NULL DEFAULT 'funcionando'
    CHECK (estado_sensor IN ('funcionando', 'irregular', 'congelado', 'sin_senal'));
-- Nivel calculado cuando no se puede confiar en el sensor (sin señal o congelado)
ALTER TABLE contenedor ADD COLUMN nivel_estimado SMALLINT CHECK (nivel_estimado BETWEEN 0 AND 100);
-- Última vez que un chofer confirmó que lo vació
ALTER TABLE contenedor ADD COLUMN ultima_recoleccion_at TIMESTAMPTZ;

-- Una sola fila con la hora del sistema. Mientras no haya sensores reales la hora
-- es simulada: la adelanta el emulador y se puede pausar o acelerar para la demo.
CREATE TABLE estado_sistema (
    id         SMALLINT PRIMARY KEY CHECK (id = 1),
    reloj_at   TIMESTAMPTZ, -- vacío = se usa la hora real
    en_pausa   BOOLEAN NOT NULL DEFAULT FALSE,
    velocidad  SMALLINT NOT NULL DEFAULT 1 CHECK (velocidad BETWEEN 1 AND 20)
);
INSERT INTO estado_sistema (id) VALUES (1);
