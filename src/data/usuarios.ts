// Usuarios de prueba para la demo. Las contraseñas están en el código solo
// porque todavía no hay backend: cuando exista, el login se valida en el servidor.

export type Rol = 'director' | 'operador';

export interface Usuario {
  usuario: string;
  contrasena: string;
  nombre: string;
  cargo: string;
  iniciales: string;
  rol: Rol;
}

export type UsuarioSesion = Omit<Usuario, 'contrasena'>;

export const USUARIOS_DE_PRUEBA: Usuario[] = [
  {
    usuario: 'director',
    contrasena: 'director123',
    nombre: 'Roberto Aguirre',
    cargo: 'Director de Higiene Urbana',
    iniciales: 'RA',
    rol: 'director',
  },
  {
    usuario: 'operador',
    contrasena: 'operador123',
    nombre: 'Lucía Gómez',
    cargo: 'Operadora del Centro de Monitoreo',
    iniciales: 'LG',
    rol: 'operador',
  },
];

// Qué pantallas puede ver cada rol. Para cambiar permisos se edita solo esta tabla.
export const PANTALLAS_POR_ROL: Record<Rol, string[]> = {
  director: ['/', '/mapa', '/rutas', '/incidentes', '/reportes', '/indicadores'],
  operador: ['/', '/mapa', '/rutas', '/incidentes', '/reportes'],
};

export function puedeVer(rol: Rol, ruta: string): boolean {
  return PANTALLAS_POR_ROL[rol].includes(ruta);
}

function sinContrasena({ contrasena: _contrasena, ...resto }: Usuario): UsuarioSesion {
  return resto;
}

export function validarCredenciales(usuario: string, contrasena: string): UsuarioSesion | null {
  const encontrado = USUARIOS_DE_PRUEBA.find(
    (u) => u.usuario === usuario.trim().toLowerCase() && u.contrasena === contrasena,
  );
  return encontrado ? sinContrasena(encontrado) : null;
}

export function buscarUsuario(usuario: string): UsuarioSesion | null {
  const encontrado = USUARIOS_DE_PRUEBA.find((u) => u.usuario === usuario);
  return encontrado ? sinContrasena(encontrado) : null;
}
