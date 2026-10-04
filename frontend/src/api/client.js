import { demoApi } from './demo'

const BASE = import.meta.env.VITE_API_URL ?? ''
const DEMO = import.meta.env.VITE_DEMO === 'true' || import.meta.env.MODE === 'demo'

let token = null
let alExpirar = null

export const setToken = (t) => { token = t }
export const onSesionExpirada = (fn) => { alExpirar = fn }
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
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ErrorApi('No hay conexión con el servidor. Revisa el internet e intenta de nuevo.', 0)
  }
  if (res.status === 204) return null
  const datos = await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status === 401 && token && alExpirar) alExpirar()
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
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    })
  } catch {
    throw new ErrorApi('No hay conexión con el servidor. Revisa el internet e intenta de nuevo.', 0)
  }
  const datos = await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status === 401 && token && alExpirar) alExpirar()
    throw new ErrorApi(datos?.mensaje ?? `Error ${res.status}`, res.status)
  }
  return datos
}

/** Dirección de una foto de producto vista desde /pos/: "img/x.jpg" vive en la raíz del sitio. */
export function urlImagen(u) {
  if (!u) return null
  return /^(https?:|\/|blob:|data:)/.test(u) ? u : `../${u}`
}
