import { demoApi } from './demo'

const BASE = import.meta.env.VITE_API_URL ?? ''
const DEMO = import.meta.env.VITE_DEMO === 'true' || import.meta.env.MODE === 'demo'

// La sesión real vive en una cookie HttpOnly que el navegador manda solo: este código nunca ve el token.
// "token" solo se usa en el modo demo (sin servidor).
let token = null
let haySesion = false
let alExpirar = null

export const setToken = (t) => { token = t }
export const setSesionActiva = (activa) => { haySesion = activa }
export const onSesionExpirada = (fn) => { alExpirar = fn }

function encabezadosSesion() {
  return DEMO && token ? { Authorization: `Bearer ${token}` } : {}
}

function revisarExpirada(status) {
  if (status === 401 && haySesion && alExpirar) alExpirar()
}
export const esDemo = DEMO

export class ErrorApi extends Error {
  constructor(mensaje, status, campos) {
    super(mensaje)
    this.status = status
    this.campos = campos
  }
}

/** Llama a la API. Devuelve el JSON o lanza ErrorApi con el mensaje del servidor. */
export async function api(ruta, { method = 'GET', body } = {}) {
  if (DEMO) return demoApi(ruta, method, body, token)

  let res
  try {
    res = await fetch(BASE + ruta, {
      method,
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', ...encabezadosSesion() },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ErrorApi('No hay conexión con el servidor. Revisa el internet e intenta de nuevo.', 0)
  }
  if (res.status === 204) return null
  const datos = await res.json().catch(() => null)
  if (!res.ok) {
    revisarExpirada(res.status)
    throw new ErrorApi(datos?.mensaje ?? `Error ${res.status}`, res.status, datos?.campos)
  }
  return datos
}

/** Sube un archivo (multipart). El navegador pone solo el Content-Type con el separador. */
export async function apiArchivo(ruta, formData) {
  if (DEMO) return demoApi(ruta, 'POST', formData, token)
  let res
  try {
    res = await fetch(BASE + ruta, {
      method: 'POST',
      credentials: 'same-origin',
      headers: encabezadosSesion(),
      body: formData,
    })
  } catch {
    throw new ErrorApi('No hay conexión con el servidor. Revisa el internet e intenta de nuevo.', 0)
  }
  const datos = await res.json().catch(() => null)
  if (!res.ok) {
    revisarExpirada(res.status)
    throw new ErrorApi(datos?.mensaje ?? `Error ${res.status}`, res.status)
  }
  return datos
}

/** Dirección de una foto de producto vista desde /pos/: "img/x.jpg" vive en la raíz del sitio. */
export function urlImagen(u) {
  if (!u) return null
  return /^(https?:|\/|blob:|data:)/.test(u) ? u : `../${u}`
}
