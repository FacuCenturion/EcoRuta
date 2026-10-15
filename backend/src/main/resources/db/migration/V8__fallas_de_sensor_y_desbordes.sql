-- Dos tipos de incidente nuevos que el sistema abre solo:
--   falla_sensor: un sensor dejó de funcionar (sin señal, congelado o trabado)
--   desborde:     un contenedor llegó al tope
ALTER TABLE incidente DROP CONSTRAINT incidente_tipo_check;
ALTER TABLE incidente ADD CONSTRAINT incidente_tipo_check CHECK (tipo IN (
    'llenado_irregular', 'no_se_pudo', 'reporte_vecinal', 'ruta_no_iniciada', 'falla_sensor', 'desborde'));

-- Estado nuevo del sensor: trabado = no bajó después de que el camión vació el contenedor
ALTER TABLE contenedor DROP CONSTRAINT contenedor_estado_sensor_check;
ALTER TABLE contenedor ADD CONSTRAINT contenedor_estado_sensor_check CHECK (estado_sensor IN (
    'funcionando', 'irregular', 'congelado', 'sin_senal', 'trabado'));

-- Desde cuándo está lleno (vacío si no lo está). Se borra cuando un camión lo vacía.
ALTER TABLE contenedor ADD COLUMN desborde_desde TIMESTAMPTZ;
