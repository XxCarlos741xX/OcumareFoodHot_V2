import { Link } from 'react-router-dom'
import { DndContext, KeyboardSensor, PointerSensor, TouchSensor, closestCenter, useSensor, useSensors } from '@dnd-kit/core'
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'
import { useAviso } from '../components/Aviso'
import Hoja from '../components/Hoja'
import { useCarga } from '../lib/useCarga'

/**
 * Organizar el salón (gerente y admin): agregar, editar, desactivar, reordenar arrastrando y renumerar.
 * Las mesas ocupadas no se pueden desactivar ni cambiar de número.
 */
export default function Salon() {
  const aviso = useAviso()
  const { datos, recargar, error } = useCarga('/api/mesas?incluirInactivas=true')
  const [lista, setLista] = useState([])
  const [ordenCambiado, setOrdenCambiado] = useState(false)
  const [editar, setEditar] = useState(null)          // mesa a editar, o {} para una nueva
  const [renumerar, setRenumerar] = useState(false)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => { if (datos) { setLista(datos); setOrdenCambiado(false) } }, [datos])

  const sensores = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )
  const zonas = useMemo(() => [...new Set(lista.map((m) => m.zona).filter(Boolean))], [lista])
  const activas = lista.filter((m) => m.activa).length
  const hayOcupadas = lista.some((m) => m.pedidoAbiertoId)

  function alSoltar({ active, over }) {
    if (!over || active.id === over.id) return
    setLista((l) => arrayMove(l, l.findIndex((m) => m.id === active.id), l.findIndex((m) => m.id === over.id)))
    setOrdenCambiado(true)
  }

  async function ejecutar(fn, mensaje) {
    setOcupado(true)
    try {
      const r = await fn()
      if (Array.isArray(r)) { setLista(r); setOrdenCambiado(false) } else await recargar()
      aviso(mensaje)
      return true
    } catch (err) {
      aviso(err.message, 'error')
      return false
    } finally {
      setOcupado(false)
    }
  }

  const guardarOrden = () => ejecutar(() => api('/api/mesas/orden', { method: 'PUT', body: { ids: lista.map((m) => m.id) } }), 'Orden guardado')
  const alternar = (m) => ejecutar(
    () => api(`/api/mesas/${m.id}`, { method: 'PUT', body: { nombre: m.nombre, zona: m.zona, activa: !m.activa } }),
    m.activa ? `Mesa ${m.numero} retirada del salón` : `Mesa ${m.numero} de vuelta en el salón`)

  return (
    <div className="pagina">
      <div className="pagina-cabeza">
        <Link to="/gestion" className="volver" aria-label="Volver a gestión">‹</Link>
        <h1>Salón</h1>
        <p className="resumen">{activas} mesas en uso</p>
      </div>
      <p className="nota">Arrastra las mesas para dejarlas en el orden en que están en el local. Así las verán los meseros.</p>

      <div className="acciones-rapidas">
        <button type="button" className="btn btn-oro" onClick={() => setEditar({})}>Agregar mesa</button>
        <button type="button" className="btn btn-linea" disabled={ocupado || hayOcupadas} onClick={() => setRenumerar(true)}
                title={hayOcupadas ? 'Se puede renumerar cuando todas las mesas estén libres' : undefined}>Renumerar</button>
      </div>

      {error && <p className="error">{error.message}</p>}

      <DndContext sensors={sensores} collisionDetection={closestCenter} onDragEnd={alSoltar}>
        <SortableContext items={lista.map((m) => m.id)} strategy={verticalListSortingStrategy}>
          <ul className="salon">
            {lista.map((m) => <FilaMesa key={m.id} mesa={m} ocupado={ocupado} onEditar={() => setEditar(m)} onAlternar={() => alternar(m)} />)}
          </ul>
        </SortableContext>
      </DndContext>

      {ordenCambiado && (
        <div className="barra-guardar">
          <span>Cambiaste el orden</span>
          <button type="button" className="btn btn-linea" onClick={() => { setLista(datos); setOrdenCambiado(false) }}>Deshacer</button>
          <button type="button" className="btn btn-oro" disabled={ocupado} onClick={guardarOrden}>Guardar orden</button>
        </div>
      )}

      {editar && <HojaMesa mesa={editar} zonas={zonas} onCerrar={() => setEditar(null)}
                           onGuardar={async (body) => {
                             const ok = await ejecutar(
                               () => (editar.id ? api(`/api/mesas/${editar.id}`, { method: 'PUT', body })
                                                : api('/api/mesas', { method: 'POST', body })),
                               editar.id ? 'Mesa actualizada' : 'Mesa agregada')
                             if (ok) setEditar(null)
                           }} />}

      {renumerar && (
        <Hoja titulo="Renumerar mesas" onCerrar={() => setRenumerar(false)}>
          <p>Las {activas} mesas en uso quedarán numeradas del 1 al {activas}, en el orden de la lista. Las retiradas pasan al final.</p>
          <p className="nota">Avisa a los meseros: los números de mesa van a cambiar.</p>
          <button type="button" className="btn btn-oro btn-grande" disabled={ocupado}
                  onClick={async () => { if (await ejecutar(() => api('/api/mesas/renumerar', { method: 'POST' }), 'Mesas renumeradas')) setRenumerar(false) }}>
            Renumerar del 1 al {activas}
          </button>
        </Hoja>
      )}
    </div>
  )
}

function FilaMesa({ mesa: m, ocupado, onEditar, onAlternar }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: m.id })
  const estilo = { transform: CSS.Transform.toString(transform), transition }
  return (
    <li ref={setNodeRef} style={estilo} className={`salon-fila ${m.activa ? '' : 'retirada'} ${isDragging ? 'arrastrando' : ''}`}>
      <button type="button" className="asa" {...attributes} {...listeners} aria-label={`Mover mesa ${m.numero}`}>
        <svg viewBox="0 0 10 16" width="10" height="16" aria-hidden="true">
          {[3, 8, 13].flatMap((y) => [2, 8].map((x) => <circle key={`${x}${y}`} cx={x} cy={y} r="1.5" fill="currentColor" />))}
        </svg>
      </button>
      <span className="salon-numero">{m.numero}</span>
      <span className="salon-info">
        <strong>{m.nombre ?? m.zona ?? 'Sin zona'}</strong>
        <small>{[m.nombre && m.zona, `${m.capacidad} puestos`, m.pedidoAbiertoId && 'ocupada', !m.activa && 'retirada del salón'].filter(Boolean).join(', ')}</small>
      </span>
      <button type="button" className="btn btn-texto" onClick={onEditar}>Editar</button>
      <label className="interruptor" title={m.pedidoAbiertoId ? 'Está ocupada' : undefined}>
        <input type="checkbox" checked={m.activa} disabled={ocupado || !!m.pedidoAbiertoId} onChange={onAlternar} />
        <span className="oculto">{m.activa ? 'En uso' : 'Retirada'}</span>
      </label>
    </li>
  )
}

function HojaMesa({ mesa, zonas, onCerrar, onGuardar }) {
  const nueva = !mesa.id
  const [f, setF] = useState({
    numero: mesa.numero ?? '', nombre: mesa.nombre ?? '', zona: mesa.zona ?? '', capacidad: mesa.capacidad ?? 4,
  })
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })
  function guardar(e) {
    e.preventDefault()
    onGuardar({
      numero: f.numero === '' ? null : Number(f.numero),
      nombre: f.nombre.trim() || null,
      zona: f.zona.trim() || null,
      capacidad: Number(f.capacidad) || 4,
    })
  }
  return (
    <Hoja titulo={nueva ? 'Agregar mesa' : `Mesa ${mesa.numero}`} onCerrar={onCerrar}>
      <form className="form" onSubmit={guardar}>
        <div className="fila-2">
          <label>Número
            <input type="number" min="1" max="9999" inputMode="numeric" {...campo('numero')}
                   placeholder={nueva ? 'Automático' : ''} disabled={!!mesa.pedidoAbiertoId} />
          </label>
          <label>Puestos<input type="number" min="1" max="50" inputMode="numeric" {...campo('capacidad')} /></label>
        </div>
        <label>Nombre (opcional)<input {...campo('nombre')} placeholder="Ventana, Barra 2…" /></label>
        <label>Zona
          <input list="zonas" {...campo('zona')} placeholder="Salón, Terraza…" />
          <datalist id="zonas">{zonas.map((z) => <option key={z} value={z} />)}</datalist>
        </label>
        {mesa.pedidoAbiertoId && <p className="nota">La mesa está ocupada: el número se podrá cambiar cuando se libere.</p>}
        <button className="btn btn-oro btn-grande">{nueva ? 'Agregar mesa' : 'Guardar cambios'}</button>
      </form>
    </Hoja>
  )
}
