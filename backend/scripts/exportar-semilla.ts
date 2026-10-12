// Genera V2__datos_iniciales.sql a partir de los datos de ejemplo del front,
// así la base arranca con las mismas zonas, contenedores y camiones que la demo.
//
// Uso (desde la raíz del repo):
//   npx --yes tsx backend/scripts/exportar-semilla.ts > backend/src/main/resources/db/migration/V2__datos_iniciales.sql
//
// Si cambian los datos del front no se edita V2: se genera una migración nueva (V3, V4...).
import { crearCamiones, crearContenedores } from '../../src/data/semilla';
import { ZONAS } from '../../src/data/zonas';

const SEMILLA = 2026; // la misma que usa el simulador (SEMILLA_POR_DEFECTO)

// Hashes BCrypt de las contraseñas de prueba del README (director123, operador123, chofer123)
const HASH_DIRECTOR = '$2a$10$8M4sTSLmEaq9f1GWW8pEI..y4g/2kGS/wKxOSCXHISlM1oqiAqOna';
const HASH_OPERADOR = '$2a$10$MCHEKarbG5bHwBvbXlIjkeaoNfPY62HC8Jng7FM7rKQb8LRcrRXBS';
const HASH_CHOFER = '$2a$10$YOayJxiM.hfloMti9u48COOH9rZRUQA8EvpOSY/fJK3dhofXTnTCK';

const texto = (valor: string) => `'${valor.replaceAll("'", "''")}'`;
const coord = (valor: number) => valor.toFixed(6);

const lineas: string[] = [
  '-- Generado con backend/scripts/exportar-semilla.ts. No editar a mano.',
  '-- Datos de ejemplo: 7 zonas, 126 contenedores, 7 camiones, 2 usuarios del panel y 7 choferes.',
  '',
  'INSERT INTO zona (id, empresa, centro_lat, centro_lng, base_lat, base_lng) VALUES',
  ZONAS.map(
    (z) =>
      `    (${z.id}, ${texto(z.empresa)}, ${coord(z.centro.lat)}, ${coord(z.centro.lng)}, ${coord(z.base.lat)}, ${coord(z.base.lng)})`,
  ).join(',\n') + ';',
  '',
  'INSERT INTO usuario (usuario, password_hash, nombre, cargo, rol) VALUES',
];

const camiones = crearCamiones();
const usuarios = [
  `    ('director', ${texto(HASH_DIRECTOR)}, 'Roberto Aguirre', 'Director de Higiene Urbana', 'director')`,
  `    ('operador', ${texto(HASH_OPERADOR)}, 'Lucía Gómez', 'Operadora del Centro de Monitoreo', 'operador')`,
  ...camiones.map(
    (c) => `    ('chofer${c.numero}', ${texto(HASH_CHOFER)}, ${texto(c.chofer)}, 'Chofer del camión ${c.numero}', 'chofer')`,
  ),
];
lineas.push(usuarios.join(',\n') + ';', '');

lineas.push(
  'INSERT INTO contenedor (id, direccion, zona_id, lat, lng, velocidad_llenado, llenado) VALUES',
  crearContenedores(SEMILLA)
    .map(
      (c) =>
        `    (${texto(c.id)}, ${texto(c.direccion)}, ${c.zonaId}, ${coord(c.posicion.lat)}, ${coord(c.posicion.lng)}, ${c.velocidad}, ${c.llenado})`,
    )
    .join(',\n') + ';',
  '',
);

lineas.push(
  'INSERT INTO camion (id, numero, zona_id, chofer_id, lat, lng) VALUES',
  camiones
    .map(
      (c) =>
        `    (${texto(c.id)}, ${c.numero}, ${c.zonaId}, (SELECT id FROM usuario WHERE usuario = 'chofer${c.numero}'), ${coord(c.posicion.lat)}, ${coord(c.posicion.lng)})`,
    )
    .join(',\n') + ';',
);

console.log(lineas.join('\n'));
