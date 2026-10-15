-- Nivel con el que arranca cada contenedor en la demo. Lo usa el reinicio de la demo
-- (POST /api/v1/simulacion/reiniciar) para volver al punto de partida sin recrear la base.
ALTER TABLE contenedor ADD COLUMN llenado_inicial SMALLINT CHECK (llenado_inicial BETWEEN 0 AND 100);
UPDATE contenedor SET llenado_inicial = llenado;
ALTER TABLE contenedor ALTER COLUMN llenado_inicial SET NOT NULL;
