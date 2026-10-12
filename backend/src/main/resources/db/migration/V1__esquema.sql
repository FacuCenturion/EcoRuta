-- Esquema inicial de EcoRuta. Sigue el modelo de datos de la guía de backend.
-- Los valores posibles de cada estado coinciden con los tipos de src/types/index.ts del front.

CREATE TABLE zona (
    id          INTEGER PRIMARY KEY,
    empresa     VARCHAR(100) NOT NULL,
    centro_lat  DOUBLE PRECISION NOT NULL,
    centro_lng  DOUBLE PRECISION NOT NULL,
    base_lat    DOUBLE PRECISION NOT NULL, -- playón desde el que salen los camiones
    base_lng    DOUBLE PRECISION NOT NULL
);

CREATE TABLE usuario (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    nombre         VARCHAR(100) NOT NULL,
    cargo          VARCHAR(100) NOT NULL,
    rol            VARCHAR(20)  NOT NULL CHECK (rol IN ('director', 'operador', 'chofer'))
);

CREATE TABLE contenedor (
    id                 VARCHAR(10) PRIMARY KEY, -- por ejemplo Z2-04
    direccion          VARCHAR(150) NOT NULL,
    zona_id            INTEGER NOT NULL REFERENCES zona (id),
    lat                DOUBLE PRECISION NOT NULL,
    lng                DOUBLE PRECISION NOT NULL,
    velocidad_llenado  NUMERIC(4, 1) NOT NULL, -- % por hora en condiciones normales
    llenado            SMALLINT NOT NULL CHECK (llenado BETWEEN 0 AND 100), -- última lectura
    ultima_lectura_at  TIMESTAMPTZ,
    anomalia           BOOLEAN NOT NULL DEFAULT FALSE,
    activo             BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_contenedor_zona ON contenedor (zona_id);

CREATE TABLE lectura_sensor (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contenedor_id  VARCHAR(10) NOT NULL REFERENCES contenedor (id),
    llenado        SMALLINT NOT NULL CHECK (llenado BETWEEN 0 AND 100),
    medido_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_lectura_contenedor_fecha ON lectura_sensor (contenedor_id, medido_at DESC);

CREATE TABLE camion (
    id                 VARCHAR(10) PRIMARY KEY, -- por ejemplo C22
    numero             INTEGER NOT NULL UNIQUE,
    zona_id            INTEGER NOT NULL REFERENCES zona (id),
    chofer_id          BIGINT REFERENCES usuario (id),
    estado             VARCHAR(20) NOT NULL DEFAULT 'en_base'
                       CHECK (estado IN ('en_ruta', 'en_descarga', 'en_base', 'demorado')),
    carga_pct          SMALLINT NOT NULL DEFAULT 0 CHECK (carga_pct BETWEEN 0 AND 100),
    lat                DOUBLE PRECISION NOT NULL,
    lng                DOUBLE PRECISION NOT NULL,
    km_recorridos_hoy  NUMERIC(7, 2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_camion_zona ON camion (zona_id);

CREATE TABLE ruta (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    camion_id              VARCHAR(10) NOT NULL REFERENCES camion (id),
    creada_at              TIMESTAMPTZ NOT NULL,
    salida_programada      TIMESTAMPTZ NOT NULL,
    salida_real            TIMESTAMPTZ,
    origen                 VARCHAR(20) NOT NULL CHECK (origen IN ('automatico', 'manual')),
    km_planificados        NUMERIC(7, 2) NOT NULL,
    km_ruta_fija           NUMERIC(7, 2) NOT NULL, -- km si se visitaran todos los contenedores de la zona
    duracion_estimada_min  INTEGER NOT NULL,
    estado                 VARCHAR(20) NOT NULL DEFAULT 'programada'
                           CHECK (estado IN ('programada', 'en_curso', 'terminada'))
);
CREATE INDEX idx_ruta_camion_estado ON ruta (camion_id, estado);

CREATE TABLE parada (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ruta_id        BIGINT NOT NULL REFERENCES ruta (id) ON DELETE CASCADE,
    contenedor_id  VARCHAR(10) NOT NULL REFERENCES contenedor (id),
    orden          INTEGER NOT NULL,
    estado         VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                   CHECK (estado IN ('pendiente', 'recolectada', 'no_se_pudo')),
    eta            TIMESTAMPTZ,
    hora_real      TIMESTAMPTZ,
    motivo         VARCHAR(200), -- por qué no se pudo recolectar
    UNIQUE (ruta_id, orden)
);

CREATE TABLE incidente (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(30) NOT NULL
                   CHECK (tipo IN ('llenado_irregular', 'no_se_pudo', 'reporte_vecinal', 'ruta_no_iniciada')),
    estado         VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                   CHECK (estado IN ('pendiente', 'confirmado', 'descartado', 'resuelto')),
    contenedor_id  VARCHAR(10) REFERENCES contenedor (id), -- vacío en ruta_no_iniciada
    camion_id      VARCHAR(10) REFERENCES camion (id),
    descripcion    VARCHAR(500) NOT NULL,
    sugerencia     VARCHAR(200),
    origen         VARCHAR(50), -- por ejemplo Boti en los reportes vecinales
    creado_at      TIMESTAMPTZ NOT NULL,
    decidido_por   BIGINT REFERENCES usuario (id),
    decidido_at    TIMESTAMPTZ
);
CREATE INDEX idx_incidente_estado ON incidente (estado);
CREATE INDEX idx_incidente_contenedor ON incidente (contenedor_id);

CREATE TABLE dispositivo (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id     BIGINT NOT NULL REFERENCES usuario (id),
    token_fcm      VARCHAR(500) NOT NULL UNIQUE,
    plataforma     VARCHAR(10) NOT NULL CHECK (plataforma IN ('android', 'ios', 'web')),
    registrado_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE notificacion (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuario (id),
    ruta_id     BIGINT REFERENCES ruta (id),
    tipo        VARCHAR(20) NOT NULL CHECK (tipo IN ('ruta_nueva', 'parada_nueva')),
    estado      VARCHAR(10) NOT NULL CHECK (estado IN ('enviada', 'fallida')),
    enviada_at  TIMESTAMPTZ NOT NULL,
    abierta_at  TIMESTAMPTZ
);
