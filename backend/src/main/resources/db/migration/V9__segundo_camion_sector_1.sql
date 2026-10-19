-- El sector 1 (Retiro y San Nicolás) tiene 40 contenedores chicos de oficinas que se llenan
-- todos a la vez al mediodía: un solo camión no llega a vaciarlos antes de que desborden.
-- Se suma un segundo camión, como pasa en la operación real del microcentro.
-- La contraseña de prueba es la misma de los otros choferes (chofer123).
INSERT INTO usuario (usuario, password_hash, nombre, cargo, rol) VALUES
    ('chofer40', '$2a$10$YOayJxiM.hfloMti9u48COOH9rZRUQA8EvpOSY/fJK3dhofXTnTCK', 'Marcos Villalba',
     'Chofer del camión 40', 'chofer');

INSERT INTO camion (id, numero, zona_id, sector_id, chofer_id, lat, lng)
SELECT 'C40', 40, 1, 1, u.id, z.base_lat, z.base_lng
FROM usuario u, zona z
WHERE u.usuario = 'chofer40' AND z.id = 1;
