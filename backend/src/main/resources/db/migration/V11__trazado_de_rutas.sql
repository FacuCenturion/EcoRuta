-- Recorrido de cada ruta por las calles, para dibujarlo en el panel (y después en la app del chofer).
-- Lo calcula OSRM la primera vez que alguien mira la ruta.
--   trazado: un tramo por cada tramo de la ruta (base -> parada 1 -> ... -> base),
--            cada uno una lista de puntos [lat, lng] en JSON
--   km_por_calle: el largo real por las calles, con las manos de cada una
ALTER TABLE ruta
    ADD COLUMN trazado TEXT,
    ADD COLUMN km_por_calle NUMERIC(8, 2);
