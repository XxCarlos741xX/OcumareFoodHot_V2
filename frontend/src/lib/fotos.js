/**
 * Achica una foto en el navegador antes de subirla: máximo 1000 px por lado, JPEG al 82 %.
 * Una foto de celular de 4 MB queda en ~150 KB, carga rápido en la web y no llena la base.
 */
export async function achicarFoto(archivo, maximo = 1000, calidad = 0.82) {
  if (!archivo.type.startsWith('image/')) throw new Error('Elige un archivo de imagen (JPG, PNG o WebP).')
  const bitmap = await createImageBitmap(archivo)
  const escala = Math.min(1, maximo / Math.max(bitmap.width, bitmap.height))
  const ancho = Math.round(bitmap.width * escala)
  const alto = Math.round(bitmap.height * escala)
  const lienzo = document.createElement('canvas')
  lienzo.width = ancho
  lienzo.height = alto
  const ctx = lienzo.getContext('2d')
  ctx.fillStyle = '#ffffff'            // fondo blanco para PNG con transparencia
  ctx.fillRect(0, 0, ancho, alto)
  ctx.drawImage(bitmap, 0, 0, ancho, alto)
  bitmap.close?.()
  const blob = await new Promise((ok) => lienzo.toBlob(ok, 'image/jpeg', calidad))
  if (!blob) throw new Error('No se pudo procesar la foto.')
  return blob
}
