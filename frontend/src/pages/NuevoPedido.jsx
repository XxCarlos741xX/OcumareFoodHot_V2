import { useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAviso } from '../components/Aviso'
import Carta from '../components/Carta'
import Hoja from '../components/Hoja'
import { TIPOS, leerMonto } from '../lib/dinero'

/** Pedido nuevo (mesa, para llevar, delivery) o agregar productos a uno existente (/pedido/:id/agregar). */
export default function NuevoPedido() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const navegar = useNavigate()
  const aviso = useAviso()
  const tipo = params.get('tipo') ?? 'MESA'
  const [enviando, setEnviando] = useState(false)
  const [pendientes, setPendientes] = useState(null)   // items a la espera de los datos del cliente

  const titulo = id ? 'Agregar productos'
    : tipo === 'MESA' ? `Mesa ${params.get('numero') ?? ''}` : TIPOS[tipo]

  async function enviar(items, datosCliente = {}) {
    setEnviando(true)
    try {
      if (id) {
        await api(`/api/pedidos/${id}/items`, { method: 'POST', body: items })
        aviso('Productos agregados')
        navegar(`/pedido/${id}`, { replace: true })
      } else {
        const p = await api('/api/pedidos', { method: 'POST', body: {
          tipo, mesaId: tipo === 'MESA' ? Number(params.get('mesa')) : null, items, ...datosCliente,
        } })
        aviso(`Pedido #${p.numeroDiario} creado`)
        navegar(`/pedido/${p.id}`, { replace: true })
      }
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  function confirmar(items) {
    if (!id && tipo !== 'MESA') setPendientes(items)   // para llevar y delivery piden datos del cliente
    else enviar(items)
  }

  return (
    <div className="pagina pagina-carta">
      <div className="pagina-cabeza">
        <button type="button" className="volver" onClick={() => navegar(-1)} aria-label="Volver">‹</button>
        <h1>{titulo}</h1>
      </div>
      <Carta etiquetaConfirmar={id ? 'Agregar al pedido' : tipo === 'MESA' ? 'Abrir mesa' : 'Continuar'}
             onConfirmar={confirmar} enviando={enviando} />
      {pendientes && (
        <DatosCliente tipo={tipo} enviando={enviando} onCerrar={() => setPendientes(null)}
                      onEnviar={(datos) => enviar(pendientes, datos)} />
      )}
    </div>
  )
}

function DatosCliente({ tipo, enviando, onCerrar, onEnviar }) {
  const delivery = tipo === 'DELIVERY'
  const [f, setF] = useState({ clienteNombre: '', clienteTelefono: '', direccionEntrega: '', costoDeliveryUsd: '', notas: '' })
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })
  const costo = f.costoDeliveryUsd ? leerMonto(f.costoDeliveryUsd) : 0
  const valido = !delivery || (f.clienteTelefono.trim() && f.direccionEntrega.trim() && !Number.isNaN(costo))

  function enviar(e) {
    e.preventDefault()
    onEnviar({ ...f, costoDeliveryUsd: delivery ? costo : null })
  }

  return (
    <Hoja titulo={delivery ? 'Datos del delivery' : 'Datos del cliente'} onCerrar={onCerrar}>
      <form className="form" onSubmit={enviar}>
        <label>Nombre<input {...campo('clienteNombre')} autoComplete="off" /></label>
        <label>Teléfono{delivery && ' *'}<input type="tel" inputMode="tel" {...campo('clienteTelefono')} required={delivery} /></label>
        {delivery && (
          <>
            <label>Dirección *<textarea rows={2} {...campo('direccionEntrega')} required /></label>
            <label>Costo del envío en $<input inputMode="decimal" {...campo('costoDeliveryUsd')} placeholder="0" /></label>
          </>
        )}
        <label>Notas<input {...campo('notas')} placeholder="Sin cebolla, salsa aparte…" /></label>
        <button className="btn btn-oro btn-grande" disabled={!valido || enviando}>Crear pedido</button>
      </form>
    </Hoja>
  )
}
