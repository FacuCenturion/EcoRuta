// Genera V3__zonas_oficiales.sql: actualiza la base después de que el front pasó
// a las zonas oficiales del Servicio de Higiene Urbana (empresas, comunas, color
// y ubicaciones nuevas). V2 no se toca porque ya está en main.
//
// Uso (desde la raíz del repo):
//   npx --yes tsx backend/scripts/exportar-zonas-oficiales.ts > backend/src/main/resources/db/migration/V3__zonas_oficiales.sql
import { crearCamiones, crearContenedores } from '../../src/data/semilla';
import { ZONAS } from '../../src/data/zonas';

const SEMILLA = 2026; // la misma que usa el simulador (SEMILLA_POR_DEFECTO)

const texto = (valor: string) => `'${valor.replaceAll("'", "''")}'`;
const coord = (valor: number) => valor.toFixed(6);

const lineas: string[] = [
  '-- Generado con backend/scripts/exportar-zonas-oficiales.ts. No editar a mano.',
  '-- Pasa la base a las zonas oficiales: cada zona es la unión de sus comunas.',
  '-- Los contornos para dibujar el mapa siguen en el front (src/data/limites-zonas.json).',
  '',
  'ALTER TABLE zona ADD COLUMN comunas INTEGER[];',
  'ALTER TABLE zona ADD COLUMN color VARCHAR(7); -- mismo color que el mapa oficial de zonas',
  '',
];

for (const z of ZONAS) {
  lineas.push(
    `UPDATE zona SET empresa = ${texto(z.empresa)}, comunas = '{${z.comunas.join(',')}}', color = ${texto(z.color)}, ` +
      `centro_lat = ${coord(z.centro.lat)}, centro_lng = ${coord(z.centro.lng)}, ` +
      `base_lat = ${coord(z.base.lat)}, base_lng = ${coord(z.base.lng)} WHERE id = ${z.id};`,
  );
}

lineas.push(
  '',
  'ALTER TABLE zona ALTER COLUMN comunas SET NOT NULL;',
  'ALTER TABLE zona ALTER COLUMN color SET NOT NULL;',
  '',
  '-- Los contenedores y los camiones se mueven a las ubicaciones nuevas de cada zona',
);

for (const c of crearContenedores(SEMILLA)) {
  lineas.push(
    `UPDATE contenedor SET direccion = ${texto(c.direccion)}, lat = ${coord(c.posicion.lat)}, lng = ${coord(c.posicion.lng)}, ` +
      `velocidad_llenado = ${c.velocidad}, llenado = ${c.llenado} WHERE id = ${texto(c.id)};`,
  );
}

lineas.push('');
for (const c of crearCamiones()) {
  lineas.push(`UPDATE camion SET lat = ${coord(c.posicion.lat)}, lng = ${coord(c.posicion.lng)} WHERE id = ${texto(c.id)};`);
}

console.log(lineas.join('\n'));
