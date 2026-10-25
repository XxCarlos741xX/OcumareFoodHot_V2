/** Nombres de los roles para mostrar. De menor a mayor. */
export const ROLES = { MESERO: 'Mesero', CAJERO: 'Cajero', GERENTE: 'Gerente', ADMIN: 'Administrador' }

/** Roles que maneja un gerente (crear, editar, cambiar clave). El administrador maneja todos. */
export const ROLES_DEL_GERENTE = ['MESERO', 'CAJERO']
