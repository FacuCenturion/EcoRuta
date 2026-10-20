-- El camión lo maneja un chofer real con la app (true) o el emulador, que hace de chofer (false).
-- Pasa a true cuando el chofer empieza el turno en la app y vuelve a false cuando lo termina.
ALTER TABLE camion ADD COLUMN manejado_por_app BOOLEAN NOT NULL DEFAULT FALSE;
