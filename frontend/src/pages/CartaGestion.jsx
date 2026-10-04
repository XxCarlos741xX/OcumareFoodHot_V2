import { Link } from 'react-router-dom'
import { useMemo, useRef, useState } from 'react'
import { api, apiArchivo, urlImagen } from '../api/client'
import { achicarFoto } from '../lib/fotos'
import { useAviso } from '../components/Aviso'
import Hoja from '../components/Hoja'
import { leerMonto, usd } from '../lib/dinero'
import { useCarga } from '../lib/useCarga'

/** Gestión de la carta: productos por categoría, precios, agotados y categorías. */
export default function CartaGestion() {
  const aviso = useAviso()
  const { datos: productos, recargar: recargarProductos } = useCarga('/api/productos?incluirInactivos=true')
  const { datos: categorias, recargar: recargarCategorias } = useCarga('/api/categorias')
  const [editar, setEditar] = useState(null)          // producto, o {} para uno nuevo
  const [verCategorias, setVerCategorias] = useState(false)

  const grupos = useMemo(() => {
    const activos = (productos ?? []).filter((p) => p.activo)
    return (categorias ?? []).filter((c) => c.activa).map((c) => [c, activos.filter((p) => p.categoriaId === c.id)])
  }, [productos, categorias])
  const retirados = (productos ?? []).filter((p) => !p.activo)

  async function cambiarDisponible(p) {
    try {
      await api(`/api/productos/${p.id}/disponible`, { method: 'PATCH', body: { disponible: !p.disponible } })
      aviso(p.disponible ? `${p.nombre}: agotado` : `${p.nombre}: disponible`)
      recargarProductos()
    } catch (err) { aviso(err.message, 'error') }
  }

  async function reactivar(p) {
    try {
      await api(`/api/productos/${p.id}/reactivar`, { method: 'POST' })
      aviso(`${p.nombre} vuelve a la carta`)
      recargarProductos()
    } catch (err) { aviso(err.message, 'error') }
  }

  return (
    <div className="pagina">
      <div className="pagina-cabeza">
        <Link to="/gestion" className="volver" aria-label="Volver a gestión">‹</Link>
        <h1>Carta</h1>
        <p className="resumen">{(productos ?? []).filter((p) => p.activo).length} productos</p>
      </div>
      <div className="acciones-rapidas">
        <button type="button" className="btn btn-oro" onClick={() => setEditar({})} disabled={!categorias?.length}>Agregar producto</button>
        <button type="button" className="btn btn-linea" onClick={() => setVerCategorias(true)}>Categorías</button>
      </div>
      {categorias && categorias.length === 0 && <p className="vacio-texto">Crea primero una categoría (Hamburguesas, Bebidas…).</p>}

      {grupos.map(([c, lista]) => (
        <section key={c.id} className="bloque">
          <h2>{c.nombre}</h2>
          {lista.length === 0 && <p className="nota">Sin productos.</p>}
          <ul className="gestion-lista">
            {lista.map((p) => (
              <li key={p.id} className={p.disponible ? '' : 'apagado'}>
                {p.imagenUrl ? <img className="gestion-mini" src={urlImagen(p.imagenUrl)} alt="" loading="lazy" /> : <span className="gestion-mini" aria-hidden="true" />}
                <span className="gestion-info">
                  <strong>{p.nombre}</strong>
                  <small>{[usd(p.precioUsd), !p.disponible && 'agotado', p.destacado && 'destacado en la web'].filter(Boolean).join(', ')}</small>
                </span>
                <button type="button" className="btn btn-texto" onClick={() => setEditar(p)}>Editar</button>
                <label className="interruptor" title={p.disponible ? 'Disponible' : 'Agotado'}>
                  <input type="checkbox" checked={p.disponible} onChange={() => cambiarDisponible(p)} />
                  <span className="oculto">{p.nombre} disponible</span>
                </label>
              </li>
            ))}
          </ul>
        </section>
      ))}

      {retirados.length > 0 && (
        <details className="desplegable">
          <summary><span>Retirados de la carta</span><span className="resumen-cuadre pendiente">{retirados.length}</span></summary>
          <ul className="gestion-lista desplegable-cuerpo">
            {retirados.map((p) => (
              <li key={p.id}>
                <span className="gestion-info"><strong>{p.nombre}</strong><small>{p.categoria}, {usd(p.precioUsd)}</small></span>
                <button type="button" className="btn btn-texto" onClick={() => reactivar(p)}>Devolver a la carta</button>
              </li>
            ))}
          </ul>
        </details>
      )}

      {editar && (
        <HojaProducto producto={editar} categorias={(categorias ?? []).filter((c) => c.activa)} onCerrar={() => setEditar(null)}
                      onListo={() => { setEditar(null); recargarProductos() }} />
      )}
      {verCategorias && (
        <HojaCategorias categorias={categorias ?? []} onCerrar={() => setVerCategorias(false)}
                        onCambio={() => { recargarCategorias(); recargarProductos() }} />
      )}
    </div>
  )
}

function HojaProducto({ producto, categorias, onCerrar, onListo }) {
  const aviso = useAviso()
  const nuevo = !producto.id
  const [f, setF] = useState({
    nombre: producto.nombre ?? '', descripcion: producto.descripcion ?? '',
    precio: producto.precioUsd != null ? String(producto.precioUsd).replace('.', ',') : '',
    categoriaId: producto.categoriaId ?? categorias[0]?.id ?? '', imagenUrl: producto.imagenUrl ?? '',
    destacado: producto.destacado ?? false,
  })
  const [enviando, setEnviando] = useState(false)
  const [subiendoFoto, setSubiendoFoto] = useState(false)
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })
  const precio = leerMonto(f.precio)

  async function guardar(e) {
    e.preventDefault()
    setEnviando(true)
    const body = { nombre: f.nombre.trim(), descripcion: f.descripcion.trim() || null, precioUsd: precio,
      categoriaId: Number(f.categoriaId), imagenUrl: f.imagenUrl.trim() || null, destacado: f.destacado }
    try {
      await api(nuevo ? '/api/productos' : `/api/productos/${producto.id}`, { method: nuevo ? 'POST' : 'PUT', body })
      aviso(nuevo ? 'Producto agregado' : 'Producto actualizado')
      onListo()
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  async function retirar() {
    setEnviando(true)
    try {
      await api(`/api/productos/${producto.id}`, { method: 'DELETE' })
      aviso(`${producto.nombre} retirado de la carta`)
      onListo()
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <Hoja titulo={nuevo ? 'Agregar producto' : producto.nombre} onCerrar={onCerrar}>
      <form className="form" onSubmit={guardar}>
        <label>Nombre<input {...campo('nombre')} required maxLength={120} /></label>
        <label>Descripción<textarea rows={2} {...campo('descripcion')} maxLength={500} placeholder="Ingredientes o detalles" /></label>
        <div className="fila-2">
          <label>Precio en $<input inputMode="decimal" {...campo('precio')} required placeholder="0,00" /></label>
          <label>Categoría
            <select {...campo('categoriaId')} required>
              {categorias.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
            </select>
          </label>
        </div>
        <CampoFoto valor={f.imagenUrl} nombre={f.nombre} onCambiar={(url) => setF((x) => ({ ...x, imagenUrl: url }))}
                   onSubiendo={setSubiendoFoto} />
        <label className="casilla">
          <input type="checkbox" checked={f.destacado} onChange={(e) => setF({ ...f, destacado: e.target.checked })} />
          Mostrar en "Lo más pedido" de la web
        </label>
        {!nuevo && <p className="nota">Cambiar el precio solo afecta los pedidos nuevos.</p>}
        <button className="btn btn-oro btn-grande" disabled={!f.nombre.trim() || !(precio > 0) || enviando || subiendoFoto}>
          {subiendoFoto ? 'Subiendo foto…' : nuevo ? 'Agregar producto' : 'Guardar cambios'}
        </button>
        {!nuevo && (
          <button type="button" className="btn btn-texto btn-peligro" disabled={enviando} onClick={retirar}>Retirar de la carta</button>
        )}
      </form>
    </Hoja>
  )
}

/** Foto del producto: tomar o elegir una imagen, verla antes de guardar, cambiarla o quitarla. */
function CampoFoto({ valor, nombre, onCambiar, onSubiendo }) {
  const aviso = useAviso()
  const entrada = useRef(null)
  const [estado, setEstado] = useState(null)   // null | 'subiendo' | mensaje de error
  const src = urlImagen(valor)

  async function elegir(e) {
    const archivo = e.target.files?.[0]
    e.target.value = ''
    if (!archivo) return
    setEstado('subiendo')
    onSubiendo(true)
    try {
      const foto = await achicarFoto(archivo)
      const datos = new FormData()
      datos.append('archivo', foto, 'foto.jpg')
      const r = await apiArchivo('/api/imagenes', datos)
      onCambiar(r.url)
      setEstado(null)
    } catch (err) {
      setEstado(err.message)
      aviso(err.message, 'error')
    } finally {
      onSubiendo(false)
    }
  }

  return (
    <div className="campo-foto">
      <span className="campo-foto-titulo">Foto</span>
      <div className="campo-foto-cuerpo">
        <div className="campo-foto-vista">
          {src ? <img src={src} alt={`Foto de ${nombre || 'el producto'}`} /> : <span>Sin foto</span>}
          {estado === 'subiendo' && <span className="campo-foto-cargando">Subiendo…</span>}
        </div>
        <div className="campo-foto-acciones">
          <input ref={entrada} type="file" accept="image/jpeg,image/png,image/webp" onChange={elegir} hidden />
          <button type="button" className="btn btn-linea" disabled={estado === 'subiendo'} onClick={() => entrada.current?.click()}>
            {src ? 'Cambiar foto' : 'Subir foto'}
          </button>
          {src && <button type="button" className="btn btn-texto btn-peligro" onClick={() => onCambiar('')}>Quitar foto</button>}
        </div>
      </div>
      {estado && estado !== 'subiendo' && <p className="error">{estado}</p>}
      <p className="nota">Desde el celular puedes tomarla con la cámara. Se achica sola antes de subirla.</p>
    </div>
  )
}

function HojaCategorias({ categorias, onCerrar, onCambio }) {
  const aviso = useAviso()
  const [nueva, setNueva] = useState('')
  const [enviando, setEnviando] = useState(false)
  const ordenadas = [...categorias].sort((a, b) => a.orden - b.orden)

  async function ejecutar(fn, mensaje) {
    setEnviando(true)
    try { await fn(); aviso(mensaje); onCambio() } catch (err) { aviso(err.message, 'error') } finally { setEnviando(false) }
  }

  const agregar = (e) => {
    e.preventDefault()
    const nombre = nueva.trim()
    ejecutar(async () => {
      await api('/api/categorias', { method: 'POST', body: { nombre, orden: (ordenadas.at(-1)?.orden ?? 0) + 1 } })
      setNueva('')
    }, `Categoría ${nombre} creada`)
  }

  // Subir o bajar: intercambia el orden con la vecina.
  const mover = (i, dir) => {
    const a = ordenadas[i]
    const b = ordenadas[i + dir]
    ejecutar(async () => {
      await api(`/api/categorias/${a.id}`, { method: 'PUT', body: { nombre: a.nombre, orden: b.orden === a.orden ? a.orden + dir : b.orden, activa: a.activa } })
      await api(`/api/categorias/${b.id}`, { method: 'PUT', body: { nombre: b.nombre, orden: a.orden, activa: b.activa } })
    }, 'Orden actualizado')
  }

  const alternar = (c) => ejecutar(
    () => api(`/api/categorias/${c.id}`, { method: 'PUT', body: { nombre: c.nombre, orden: c.orden, activa: !c.activa } }),
    c.activa ? `${c.nombre} oculta de la carta` : `${c.nombre} visible en la carta`)

  return (
    <Hoja titulo="Categorías" onCerrar={onCerrar}>
      <p className="nota">El orden es el mismo en que el mesero y los clientes ven la carta.</p>
      <ul className="gestion-lista">
        {ordenadas.map((c, i) => (
          <li key={c.id} className={c.activa ? '' : 'apagado'}>
            <span className="gestion-info"><strong>{c.nombre}</strong>{!c.activa && <small>oculta</small>}</span>
            <button type="button" className="btn-icono" disabled={enviando || i === 0} onClick={() => mover(i, -1)} aria-label={`Subir ${c.nombre}`}>↑</button>
            <button type="button" className="btn-icono" disabled={enviando || i === ordenadas.length - 1} onClick={() => mover(i, 1)} aria-label={`Bajar ${c.nombre}`}>↓</button>
            <label className="interruptor" title={c.activa ? 'Visible' : 'Oculta'}>
              <input type="checkbox" checked={c.activa} disabled={enviando} onChange={() => alternar(c)} />
              <span className="oculto">{c.nombre} visible</span>
            </label>
          </li>
        ))}
      </ul>
      <form className="form fila-agregar" onSubmit={agregar}>
        <label><span className="oculto">Nueva categoría</span><input value={nueva} onChange={(e) => setNueva(e.target.value)} placeholder="Nueva categoría" maxLength={60} /></label>
        <button className="btn btn-oro" disabled={!nueva.trim() || enviando}>Agregar</button>
      </form>
    </Hoja>
  )
}
