-- Lo que necesita la app del chofer para navegar y para sumar paradas en el camino.

-- Indicaciones giro a giro de OSRM, un grupo por tramo de la ruta (mismo orden que trazado)
ALTER TABLE ruta ADD COLUMN indicaciones TEXT;

-- Al sumar una parada en el medio de una ruta se renumeran las siguientes: el orden tiene que
-- ser único recién al terminar la transacción, no fila por fila
ALTER TABLE parada DROP CONSTRAINT parada_ruta_id_orden_key;
ALTER TABLE parada ADD CONSTRAINT parada_ruta_id_orden_key UNIQUE (ruta_id, orden) DEFERRABLE INITIALLY DEFERRED;

-- Un contenedor que se volvió urgente mientras el camión está en ruta: se le propone al chofer
-- sumarlo. Si acepta (o no contesta), entra como parada; si no, espera a la próxima ruta.
CREATE TABLE parada_propuesta (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ruta_id        BIGINT NOT NULL REFERENCES ruta (id) ON DELETE CASCADE,
    contenedor_id  VARCHAR(10) NOT NULL REFERENCES contenedor (id),
    orden          INTEGER NOT NULL, -- en qué lugar de la ruta entraría
    km_extra       NUMERIC(6, 2) NOT NULL,
    minutos_extra  INTEGER NOT NULL,
    motivo         VARCHAR(20) NOT NULL, -- llenado o riesgo
    estado         VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                   CHECK (estado IN ('pendiente', 'aceptada', 'rechazada', 'vencida')),
    creada_at      TIMESTAMPTZ NOT NULL,
    decidida_at    TIMESTAMPTZ,
    UNIQUE (ruta_id, contenedor_id)
);

-- Foto que saca el chofer cuando no pudo recolectar (imagen achicada, como data URL)
ALTER TABLE incidente ADD COLUMN foto TEXT;
