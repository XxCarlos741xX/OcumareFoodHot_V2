/*
 * Backend de mentira, en memoria, para ver y probar la interfaz sin Spring Boot.
 * Se activa con "npm run dev:demo". Imita las reglas principales del backend real.
 * Usuarios: admin / admin123 (Admin), gerente / gerente123 (Gerente), ana / ana12345 (Mesero).
 */
const r2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100
const espera = () => new Promise((ok) => setTimeout(ok, 120))

class Falla extends Error {
  constructor(status, mensaje) { super(mensaje); this.status = status }
}

const hoy = (d = 0) => {
  const x = new Date(Date.now() - d * 864e5)
  return new Date(x.getTime() - x.getTimezoneOffset() * 6e4).toISOString().slice(0, 10)
}

/** Recaudo de días anteriores para que la gráfica tenga algo que mostrar. */
function historico() {
  const dias = []
  for (let d = 30; d >= 1; d--) {
    const base = 180 + ((d * 37) % 90) + (new Date(Date.now() - d * 864e5).getDay() >= 5 ? 120 : 0)
    const usd = r2(base * 0.55)
    const bs = r2(base * 0.45 * 40)
    dias.push({ fecha: hoy(d), recaudadoUsd: usd, recaudadoBs: bs, totalEquivUsd: r2(usd + bs / 40) })
  }
  return dias
}

const db = crearDatos()
db.turnos.push(db.turno)

function crearDatos() {
  const usuarios = [
    { id: 1, nombre: 'Administrador', username: 'admin', password: 'admin123', rol: 'ADMIN', activo: true },
    { id: 2, nombre: 'María Gerente', username: 'gerente', password: 'gerente123', rol: 'GERENTE', activo: true },
    { id: 3, nombre: 'Ana Pérez', username: 'ana', password: 'ana12345', rol: 'MESERO', activo: true },
  ]
  const categorias = [
    { id: 1, nombre: 'Hamburguesas', orden: 1 },
    { id: 2, nombre: 'Perros calientes', orden: 2 },
    { id: 3, nombre: 'Pepitos', orden: 3 },
    { id: 4, nombre: 'Bebidas', orden: 4 },
  ]
  const p = (id, nombre, precioUsd, categoriaId, descripcion = '', disponible = true) =>
    ({ id, nombre, precioUsd, categoriaId, descripcion, disponible, activo: true })
  const productos = [
    p(1, 'Hamburguesa clásica', 5.5, 1, 'Carne, queso, tomate, lechuga y salsas de la casa'),
    p(2, 'Hamburguesa doble', 8, 1, 'Doble carne, doble queso y tocineta'),
    p(3, 'Hamburguesa de pollo', 6, 1, 'Pechuga a la plancha con queso'),
    p(4, 'Perro sencillo', 2.5, 2, 'Salchicha, papitas, repollo y salsas'),
    p(5, 'Perro especial', 3.5, 2, 'Con queso rallado y tocineta'),
    p(6, 'Pepito de carne', 9, 3, 'Pan campesino, carne en tiras, papitas y queso'),
    p(7, 'Pepito mixto', 10, 3, 'Carne y pollo', false),
    p(8, 'Refresco 355 ml', 1.25, 4),
    p(9, 'Malta', 1.5, 4),
    p(10, 'Jugo natural', 2, 4, 'Parchita, mango o fresa'),
  ]
  const mesas = Array.from({ length: 9 }, (_, i) => ({
    id: i + 1, numero: i + 1, nombre: null, capacidad: i === 8 ? 8 : 4,
    zona: i < 6 ? 'Salón' : 'Terraza', orden: i + 1, activa: true,
  }))
  return {
    usuarios, categorias, productos, mesas,
    tasa: { id: 1, bsPorUsd: 40, fuente: 'BCV', vigenteDesde: new Date().toISOString(), registradaPor: 'María Gerente' },
    turno: { id: 1, tipo: 'DIA', estado: 'ABIERTO', fechaOperativa: hoy(), abiertoEn: new Date(Date.now() - 5 * 3600e3).toISOString(),
             abiertoPor: 'María Gerente', fondoInicialUsd: 20, fondoInicialBs: 500 },
    turnos: [],
    historico: historico(),
    pedidos: [],
    seq: { pedido: 1, detalle: 1, pago: 1, mesa: 10, numero: 1 },
  }
}

// Algunos pedidos de ejemplo
;(function sembrar() {
  const yo = db.usuarios[2]
  crearPedido({ tipo: 'MESA', mesaId: 2, items: [{ productoId: 1, cantidad: 2 }, { productoId: 8, cantidad: 2 }] }, yo)
  const p = crearPedido({ tipo: 'MESA', mesaId: 5, items: [{ productoId: 6, cantidad: 1 }, { productoId: 10, cantidad: 1 }] }, yo)
  p.estado = 'LISTO'
  crearPedido({ tipo: 'PARA_LLEVAR', clienteNombre: 'Luis', items: [{ productoId: 4, cantidad: 3 }] }, yo)
  const cerrado = crearPedido({ tipo: 'MESA', mesaId: 7, items: [{ productoId: 2, cantidad: 2 }, { productoId: 9, cantidad: 2 }] }, yo)
  cerrado.pagos.push({ id: db.seq.pago++, turnoId: 1, metodo: 'EFECTIVO_USD', moneda: 'USD', monto: 10, tasaBsPorUsd: 40, equivalenteUsd: 10, referencia: null, registradoEn: new Date().toISOString() })
  cerrado.pagos.push({ id: db.seq.pago++, turnoId: 1, metodo: 'PAGO_MOVIL', moneda: 'BS', monto: 280, tasaBsPorUsd: 40, equivalenteUsd: 7, referencia: '4471', registradoEn: new Date().toISOString() })
  totales(cerrado)
  cerrado.estado = 'PAGADO'
})()

function totales(p) {
  p.subtotalUsd = r2(p.detalles.reduce((s, d) => s + d.subtotalUsd, 0))
  p.totalUsd = r2(p.subtotalUsd + (p.costoDeliveryUsd || 0))
  p.totalBs = p.tasaBsPorUsd == null ? null : r2(p.totalUsd * p.tasaBsPorUsd)
  p.pagadoUsd = r2(p.pagos.reduce((s, x) => s + x.equivalenteUsd, 0))
  p.saldoPendienteUsd = r2(p.totalUsd - p.pagadoUsd)
  p.saldoPendienteBs = p.tasaBsPorUsd == null ? null : r2(p.saldoPendienteUsd * p.tasaBsPorUsd)
  return p
}

function agregar(p, items) {
  for (const it of items) {
    const pr = db.productos.find((x) => x.id === Number(it.productoId) && x.activo)
    if (!pr) throw new Falla(400, `El producto ${it.productoId} no existe`)
    if (!pr.disponible) throw new Falla(409, `"${pr.nombre}" está agotado`)
    p.detalles.push({ id: db.seq.detalle++, productoId: pr.id, nombre: pr.nombre, cantidad: it.cantidad,
      precioUnitarioUsd: pr.precioUsd, subtotalUsd: r2(pr.precioUsd * it.cantidad), notas: it.notas || null })
  }
}

const abiertos = ['ABIERTO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO']

function crearPedido(req, actor) {
  if (!db.turno || db.turno.estado !== 'ABIERTO') throw new Falla(409, 'No hay un turno abierto. Pide al gerente que abra el turno.')
  let mesa = null
  if (req.tipo === 'MESA') {
    mesa = db.mesas.find((m) => m.id === Number(req.mesaId))
    if (!mesa) throw new Falla(404, 'Mesa no encontrada')
    if (db.pedidos.some((p) => p.mesaId === mesa.id && abiertos.includes(p.estado)))
      throw new Falla(409, `La mesa ${mesa.numero} ya tiene un pedido abierto; agrégale productos a ese`)
  }
  if (req.tipo === 'DELIVERY' && (!req.direccionEntrega || !req.clienteTelefono))
    throw new Falla(400, 'Un delivery necesita dirección y teléfono del cliente')
  const p = {
    id: db.seq.pedido++, numeroDiario: db.seq.numero++, tipo: req.tipo, estado: 'ABIERTO', turnoId: db.turno.id,
    mesaId: mesa?.id ?? null, mesaNumero: mesa?.numero ?? null, mesero: actor.nombre,
    clienteNombre: req.clienteNombre || null, clienteTelefono: req.clienteTelefono || null,
    direccionEntrega: req.tipo === 'DELIVERY' ? req.direccionEntrega : null, notas: req.notas || null,
    motivoAnulacion: null, tasaBsPorUsd: db.tasa?.bsPorUsd ?? null,
    costoDeliveryUsd: req.tipo === 'DELIVERY' ? Number(req.costoDeliveryUsd || 0) : 0,
    creadoEn: new Date().toISOString(), cerradoEn: null, detalles: [], pagos: [],
  }
  agregar(p, req.items)
  db.pedidos.push(totales(p))
  return p
}

function cerrarSiToca(p) {
  if (p.estado === 'ENTREGADO' && p.saldoPendienteUsd <= 0.05) { p.estado = 'PAGADO'; p.cerradoEn = new Date().toISOString() }
}

const resumen = (p) => ({ id: p.id, numeroDiario: p.numeroDiario, tipo: p.tipo, estado: p.estado, mesaNumero: p.mesaNumero,
  mesero: p.mesero, clienteNombre: p.clienteNombre, totalUsd: p.totalUsd, totalBs: p.totalBs, creadoEn: p.creadoEn })

const mesaResp = (m) => {
  const p = db.pedidos.find((x) => x.mesaId === m.id && abiertos.includes(x.estado))
  return { ...m, pedidoAbiertoId: p?.id ?? null }
}
const mesasOrdenadas = () => [...db.mesas].sort((a, b) => a.orden - b.orden || a.numero - b.numero)


const METODOS_DEMO = [['EFECTIVO_USD', 'USD'], ['ZELLE', 'USD'], ['EFECTIVO_BS', 'BS'], ['PAGO_MOVIL', 'BS'], ['PUNTO_DE_VENTA', 'BS'], ['TRANSFERENCIA_BS', 'BS']]

const turnoResp = (t) => {
  const c = calcular([t.id]).totales
  return { id: t.id, tipo: t.tipo, fechaOperativa: t.fechaOperativa, estado: t.estado, abiertoEn: t.abiertoEn, abiertoPor: t.abiertoPor,
    cerradoEn: t.cerradoEn ?? null, cerradoPor: t.cerradoPor ?? null, cantidadPedidos: c.pedidos, recaudadoUsd: c.recaudadoUsd, recaudadoBs: c.recaudadoBs }
}

function calcular(ids) {
  const peds = db.pedidos.filter((p) => ids.includes(p.turnoId))
  const validos = peds.filter((p) => p.estado !== 'ANULADO')
  const pagos = db.pedidos.flatMap((p) => p.pagos.map((pg) => ({ ...pg, pedido: p }))).filter((pg) => ids.includes(pg.turnoId))
  const sum = (arr, f) => r2(arr.reduce((s, x) => s + f(x), 0))
  const venta = sum(validos, (p) => p.totalUsd)
  const totales = {
    pedidos: validos.length, anulados: peds.length - validos.length, pendientes: validos.filter((p) => p.estado !== 'PAGADO').length,
    ventaUsd: venta, recaudadoUsd: sum(pagos.filter((p) => p.moneda === 'USD'), (p) => p.monto),
    recaudadoBs: sum(pagos.filter((p) => p.moneda === 'BS'), (p) => p.monto), recaudadoTotalEquivUsd: sum(pagos, (p) => p.equivalenteUsd),
    ticketPromedioUsd: validos.length ? r2(venta / validos.length) : 0,
  }
  const porMetodo = METODOS_DEMO.map(([metodo, moneda]) => {
    const ps = pagos.filter((p) => p.metodo === metodo)
    return { metodo, moneda, cantidad: ps.length, monto: sum(ps, (p) => p.monto), equivalenteUsd: sum(ps, (p) => p.equivalenteUsd) }
  })
  const mapa = new Map()
  const fila = (nombre) => { if (!mapa.has(nombre)) mapa.set(nombre, { meseroId: null, mesero: nombre, pedidos: 0, ventaUsd: 0, cobradoUsd: 0, cobradoBs: 0, cobradoEquivUsd: 0 }); return mapa.get(nombre) }
  for (const p of validos) { const f = fila(p.tipo === 'WEB' ? 'Web' : p.mesero); f.pedidos++; f.ventaUsd = r2(f.ventaUsd + p.totalUsd) }
  for (const pg of pagos) {
    const f = fila(pg.pedido.tipo === 'WEB' ? 'Web' : pg.pedido.mesero)
    if (pg.moneda === 'USD') f.cobradoUsd = r2(f.cobradoUsd + pg.monto); else f.cobradoBs = r2(f.cobradoBs + pg.monto)
    f.cobradoEquivUsd = r2(f.cobradoEquivUsd + pg.equivalenteUsd)
  }
  return { totales, porMetodo, porMesero: [...mapa.values()].sort((a, b) => b.ventaUsd - a.ventaUsd) }
}

function corte(t) {
  const c = calcular([t.id])
  const efUsd = c.porMetodo[0].monto
  const efBs = c.porMetodo[2].monto
  const espUsd = r2((t.fondoInicialUsd ?? 0) + efUsd)
  const espBs = r2((t.fondoInicialBs ?? 0) + efBs)
  const difUsd = t.contadoUsd == null ? null : r2(t.contadoUsd - espUsd)
  const difBs = t.contadoBs == null ? null : r2(t.contadoBs - espBs)
  const cuadre = (d) => (d == null ? 'SIN_CONTAR' : d === 0 ? 'CUADRA' : d > 0 ? 'SOBRA' : 'FALTA')
  return { turno: turnoResp(t), observaciones: t.observaciones ?? null, totales: c.totales, porMetodo: c.porMetodo, porMesero: c.porMesero,
    conteoCaja: { fondoInicialUsd: t.fondoInicialUsd ?? 0, fondoInicialBs: t.fondoInicialBs ?? 0, cobradoEfectivoUsd: efUsd, cobradoEfectivoBs: efBs,
      esperadoUsd: espUsd, esperadoBs: espBs, contadoUsd: t.contadoUsd ?? null, contadoBs: t.contadoBs ?? null,
      diferenciaUsd: difUsd, diferenciaBs: difBs, cuadreUsd: cuadre(difUsd), cuadreBs: cuadre(difBs),
      contadoPor: t.contadoPor ?? null, contadoEn: t.contadoEn ?? null } }
}

export async function demoApi(ruta, metodo, body, token) {
  await espera()
  try {
    return responder(ruta, metodo, body ?? {}, token)
  } catch (e) {
    const err = new Error(e.message)
    err.status = e.status ?? 500
    throw err
  }
}

function responder(ruta, metodo, b, token) {
  const [camino, query] = ruta.split('?')
  const q = new URLSearchParams(query)
  const yo = db.usuarios.find((u) => `demo-${u.id}` === token)
  const gerencia = yo && yo.rol !== 'MESERO'
  const exigir = (cond) => { if (!cond) throw new Falla(403, 'No tienes permiso para esta acción') }
  const m = (patron) => camino.match(new RegExp(`^${patron}$`))
  let x

  if (camino === '/api/auth/login' && metodo === 'POST') {
    const u = db.usuarios.find((u) => u.username === b.username?.trim().toLowerCase() && u.password === b.password && u.activo)
    if (!u) throw new Falla(401, 'Usuario o contraseña incorrectos')
    const { password, ...usuario } = u
    return { token: `demo-${u.id}`, expiraEn: new Date(Date.now() + 12 * 3600e3).toISOString(), usuario }
  }
  if (!yo) throw new Falla(401, 'Sesión vencida')
  if (camino === '/api/auth/yo') { const { password, ...u } = yo; return u }

  if (camino === '/api/turnos/actual') {
    if (!db.turno || db.turno.estado !== 'ABIERTO') throw new Falla(404, 'No hay ningún turno abierto')
    return db.turno
  }
  if (camino === '/api/turnos/abrir' && metodo === 'POST') {
    exigir(gerencia)
    if (db.turno?.estado === 'ABIERTO') throw new Falla(409, 'Ya hay un turno abierto; ciérralo antes de abrir otro')
    db.turno = { id: (db.turno?.id ?? 0) + 1, tipo: b.tipo, estado: 'ABIERTO', fechaOperativa: hoy(),
      abiertoEn: new Date().toISOString(), abiertoPor: yo.nombre, fondoInicialUsd: Number(b.fondoInicialUsd || 0), fondoInicialBs: Number(b.fondoInicialBs || 0) }
    db.turnos.push(db.turno)
    return turnoResp(db.turno)
  }
  if (camino === '/api/tasas/vigente') {
    if (!db.tasa) throw new Falla(404, 'Todavía no se ha registrado ninguna tasa')
    return db.tasa
  }
  if (camino === '/api/tasas/bcv' && metodo === 'POST') {
    exigir(gerencia)
    const valor = 866.5612
    if (db.tasa?.fuente === 'BCV automática' && db.tasa.bsPorUsd === valor) return { nueva: false, tasa: db.tasa }
    db.tasa = { id: (db.tasa?.id ?? 0) + 1, bsPorUsd: valor, fuente: 'BCV automática', vigenteDesde: new Date().toISOString(), registradaPor: null }
    return { nueva: true, tasa: db.tasa }
  }
  if (camino === '/api/tasas' && metodo === 'POST') {
    exigir(gerencia)
    db.tasa = { id: (db.tasa?.id ?? 0) + 1, bsPorUsd: Number(b.bsPorUsd), fuente: b.fuente || 'Manual',
      vigenteDesde: new Date().toISOString(), registradaPor: yo.nombre }
    return db.tasa
  }

  if (camino === '/api/productos' && metodo === 'GET') {
    return db.productos.filter((p) => p.activo || q.get('incluirInactivos') === 'true').map((p) => ({ ...p,
      categoria: db.categorias.find((c) => c.id === p.categoriaId).nombre }))
      .sort((a, b) => db.categorias.find((c) => c.id === a.categoriaId).orden - db.categorias.find((c) => c.id === b.categoriaId).orden)
  }

  // ---- Mesas ----
  if (camino === '/api/mesas' && metodo === 'GET') {
    return mesasOrdenadas().filter((m) => q.get('incluirInactivas') === 'true' || m.activa).map(mesaResp)
  }
  if (camino === '/api/mesas' && metodo === 'POST') {
    exigir(gerencia)
    const numero = b.numero ?? Math.max(0, ...db.mesas.map((m) => m.numero)) + 1
    if (db.mesas.some((m) => m.numero === numero)) throw new Falla(409, `Ya existe la mesa ${numero}`)
    const mesa = { id: db.seq.mesa++, numero, nombre: b.nombre || null, capacidad: b.capacidad ?? 4, zona: b.zona || null,
      orden: Math.max(0, ...db.mesas.map((m) => m.orden)) + 1, activa: true }
    db.mesas.push(mesa)
    return mesaResp(mesa)
  }
  if (camino === '/api/mesas/orden' && metodo === 'PUT') {
    exigir(gerencia)
    b.ids.forEach((id, i) => { db.mesas.find((m) => m.id === id).orden = i + 1 })
    return mesasOrdenadas().map(mesaResp)
  }
  if (camino === '/api/mesas/renumerar' && metodo === 'POST') {
    exigir(gerencia)
    if (db.mesas.some((m) => mesaResp(m).pedidoAbiertoId)) throw new Falla(409, 'Hay mesas ocupadas; renumera cuando todas estén libres')
    const lista = mesasOrdenadas().sort((a, b) => Number(!a.activa) - Number(!b.activa))
    lista.forEach((m, i) => { m.numero = i + 1; m.orden = i + 1 })
    return mesasOrdenadas().map(mesaResp)
  }
  if ((x = m('/api/mesas/(\\d+)'))) {
    exigir(gerencia)
    const mesa = db.mesas.find((mm) => mm.id === Number(x[1]))
    if (!mesa) throw new Falla(404, 'Mesa no encontrada')
    const ocupada = !!mesaResp(mesa).pedidoAbiertoId
    if (metodo === 'DELETE') {
      if (ocupada) throw new Falla(409, 'La mesa tiene un pedido abierto; ciérralo antes de desactivarla')
      mesa.activa = false
      return null
    }
    if (b.numero != null && b.numero !== mesa.numero) {
      if (ocupada) throw new Falla(409, `La mesa ${mesa.numero} está ocupada; cambia el número cuando se libere`)
      if (db.mesas.some((mm) => mm.numero === b.numero && mm.id !== mesa.id)) throw new Falla(409, `Ya existe la mesa ${b.numero}`)
      mesa.numero = b.numero
    }
    if (b.activa === false && ocupada) throw new Falla(409, 'La mesa tiene un pedido abierto; ciérralo antes de desactivarla')
    Object.assign(mesa, { nombre: b.nombre || null, zona: b.zona || null })
    if (b.capacidad != null) mesa.capacidad = b.capacidad
    if (b.activa != null) mesa.activa = b.activa
    return mesaResp(mesa)
  }

  // ---- Pedidos ----
  if (camino === '/api/pedidos' && metodo === 'GET') {
    return db.pedidos.filter((p) => p.turnoId === db.turno?.id && (q.get('soloAbiertos') !== 'true' || abiertos.includes(p.estado)))
      .sort((a, b) => b.id - a.id).map(resumen)
  }
  if (camino === '/api/pedidos' && metodo === 'POST') return crearPedido(b, yo)
  if ((x = m('/api/pedidos/(\\d+)(/.*)?'))) {
    const p = db.pedidos.find((pp) => pp.id === Number(x[1]))
    if (!p) throw new Falla(404, 'Pedido no encontrado')
    const sub = x[2] ?? ''
    const editable = () => { if (!abiertos.includes(p.estado)) throw new Falla(409, `El pedido #${p.numeroDiario} ya no se puede modificar`) }
    if (!sub && metodo === 'GET') return p
    if (sub === '/items' && metodo === 'POST') { editable(); agregar(p, b); return totales(p) }
    if (sub.startsWith('/items/') && metodo === 'DELETE') {
      editable()
      if (p.detalles.length === 1) throw new Falla(400, 'No se puede dejar el pedido vacío; si no va, anúlalo')
      p.detalles = p.detalles.filter((d) => d.id !== Number(sub.split('/')[2]))
      return totales(p)
    }
    if (sub === '/estado') {
      const orden = ['ABIERTO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO']
      if (orden[orden.indexOf(p.estado) + 1] !== b.estado) throw new Falla(409, `No se puede pasar de ${p.estado} a ${b.estado}`)
      p.estado = b.estado
      cerrarSiToca(p)
      return p
    }
    if (sub === '/pagos') {
      editable()
      const enBs = ['EFECTIVO_BS', 'PAGO_MOVIL', 'PUNTO_DE_VENTA', 'TRANSFERENCIA_BS'].includes(b.metodo)
      if (['ZELLE', 'PAGO_MOVIL', 'TRANSFERENCIA_BS'].includes(b.metodo) && !b.referencia) throw new Falla(400, `Indica la referencia del ${b.metodo}`)
      if (p.tasaBsPorUsd == null) {
        if (!db.tasa && enBs) throw new Falla(400, 'Para cobrar en bolívares primero registra una tasa de cambio')
        if (db.tasa) { p.tasaBsPorUsd = db.tasa.bsPorUsd; totales(p) }
      }
      const monto = r2(Number(b.monto))
      const equivalenteUsd = enBs ? r2(monto / p.tasaBsPorUsd) : monto
      if (equivalenteUsd > p.saldoPendienteUsd + 0.05)
        throw new Falla(400, `El pago ($${equivalenteUsd}) supera el saldo pendiente ($${p.saldoPendienteUsd}). Registra solo lo que se cobra; el vuelto no se registra.`)
      if (!db.turno || db.turno.estado !== 'ABIERTO') throw new Falla(409, 'No hay un turno abierto para registrar el cobro')
      p.pagos.push({ id: db.seq.pago++, turnoId: db.turno.id, metodo: b.metodo, moneda: enBs ? 'BS' : 'USD', monto, tasaBsPorUsd: p.tasaBsPorUsd,
        equivalenteUsd, referencia: b.referencia || null, registradoEn: new Date().toISOString() })
      totales(p)
      cerrarSiToca(p)
      return p
    }
    if (sub === '/anular') {
      exigir(gerencia)
      if (p.pagos.length) throw new Falla(409, 'El pedido tiene pagos registrados; no se puede anular')
      p.estado = 'ANULADO'
      p.motivoAnulacion = b.motivo
      return p
    }
  }

  // ---- Fotos ----
  if (camino === '/api/imagenes' && metodo === 'POST') {
    exigir(gerencia)
    const archivo = b.get('archivo')
    return { id: Date.now(), url: URL.createObjectURL(archivo) }
  }

  // ---- Carta (gestión) ----
  if (camino === '/api/categorias' && metodo === 'GET') return [...db.categorias].sort((a, b) => a.orden - b.orden).map((c) => ({ activa: true, ...c }))
  if (camino === '/api/categorias' && metodo === 'POST') {
    exigir(gerencia)
    if (db.categorias.some((c) => c.nombre.toLowerCase() === b.nombre.trim().toLowerCase())) throw new Falla(409, 'Ya existe una categoría con ese nombre')
    const c = { id: Math.max(...db.categorias.map((x) => x.id)) + 1, nombre: b.nombre.trim(), orden: b.orden ?? 0, activa: true }
    db.categorias.push(c)
    return c
  }
  if ((x = m('/api/categorias/(\\d+)'))) {
    exigir(gerencia)
    const c = db.categorias.find((cc) => cc.id === Number(x[1]))
    if (!c) throw new Falla(404, 'Categoría no encontrada')
    if (metodo === 'DELETE') { c.activa = false; return null }
    Object.assign(c, { nombre: b.nombre.trim(), orden: b.orden ?? c.orden, activa: b.activa ?? c.activa ?? true })
    return c
  }
  const prodResp = (p) => ({ ...p, categoria: db.categorias.find((c) => c.id === p.categoriaId)?.nombre })
  if (camino === '/api/productos' && metodo === 'POST') {
    exigir(gerencia)
    const p = { id: Math.max(...db.productos.map((x) => x.id)) + 1, nombre: b.nombre.trim(), descripcion: b.descripcion || '',
      precioUsd: r2(Number(b.precioUsd)), categoriaId: Number(b.categoriaId), imagenUrl: b.imagenUrl || null, disponible: b.disponible ?? true, destacado: !!b.destacado, activo: true }
    db.productos.push(p)
    return prodResp(p)
  }
  if ((x = m('/api/productos/(\\d+)(/\\w+)?'))) {
    const p = db.productos.find((pp) => pp.id === Number(x[1]))
    if (!p) throw new Falla(404, 'Producto no encontrado')
    if (metodo === 'GET') return prodResp(p)
    exigir(gerencia)
    if (x[2] === '/disponible') p.disponible = b.disponible
    else if (x[2] === '/reactivar') p.activo = true
    else if (metodo === 'DELETE') { p.activo = false; return null }
    else Object.assign(p, { nombre: b.nombre.trim(), descripcion: b.descripcion || '', precioUsd: r2(Number(b.precioUsd)),
      categoriaId: Number(b.categoriaId), imagenUrl: b.imagenUrl || null, disponible: b.disponible ?? p.disponible, destacado: b.destacado ?? p.destacado })
    return prodResp(p)
  }

  // ---- Personal ----
  const usuarioResp = ({ password, ...u }) => u
  if (camino === '/api/usuarios' && metodo === 'GET') { exigir(gerencia); return [...db.usuarios].sort((a, b) => a.nombre.localeCompare(b.nombre)).map(usuarioResp) }
  if (camino === '/api/usuarios' && metodo === 'POST') {
    exigir(gerencia)
    if (yo.rol !== 'ADMIN' && b.rol !== 'MESERO') throw new Falla(403, 'Un gerente solo puede crear meseros')
    if (db.usuarios.some((u) => u.username === b.username.trim().toLowerCase())) throw new Falla(409, 'Ya existe un usuario con ese nombre de usuario')
    const u = { id: Math.max(...db.usuarios.map((x) => x.id)) + 1, nombre: b.nombre.trim(), username: b.username.trim().toLowerCase(),
      password: b.password, rol: b.rol, activo: true }
    db.usuarios.push(u)
    return usuarioResp(u)
  }
  if ((x = m('/api/usuarios/(\\d+)(/password)?'))) {
    exigir(gerencia)
    const u = db.usuarios.find((uu) => uu.id === Number(x[1]))
    if (!u) throw new Falla(404, 'Usuario no encontrado')
    if (yo.rol !== 'ADMIN' && u.rol !== 'MESERO') throw new Falla(403, 'Un gerente solo puede modificar meseros')
    if (x[2]) { u.password = b.passwordNueva; return null }
    if (u.id === yo.id && b.activo === false) throw new Falla(400, 'No puedes desactivar tu propio usuario')
    if (b.nombre) u.nombre = b.nombre.trim()
    if (b.rol) u.rol = b.rol
    if (b.activo != null) u.activo = b.activo
    return usuarioResp(u)
  }
  if (camino === '/api/auth/password') {
    if (b.passwordActual !== yo.password) throw new Falla(400, 'La contraseña actual no es correcta')
    yo.password = b.passwordNueva
    return null
  }

  // ---- Caja y ventas ----
  if (camino === '/api/turnos' && metodo === 'GET') { exigir(gerencia); return [...db.turnos].reverse().map(turnoResp) }
  if (camino === '/api/turnos/cerrar' && metodo === 'POST') {
    exigir(gerencia)
    const t = db.turno
    if (!t || t.estado !== 'ABIERTO') throw new Falla(409, 'No hay ningún turno abierto para cerrar')
    Object.assign(t, { estado: 'CERRADO', cerradoEn: new Date().toISOString(), cerradoPor: yo.nombre, observaciones: b.observaciones || null })
    if (b.conteo) Object.assign(t, { contadoUsd: Number(b.conteo.efectivoUsd), contadoBs: Number(b.conteo.efectivoBs), contadoPor: yo.nombre, contadoEn: new Date().toISOString() })
    return corte(t)
  }
  if ((x = m('/api/turnos/(\\d+)/(corte|conteo)'))) {
    exigir(gerencia)
    const t = db.turnos.find((tt) => tt.id === Number(x[1]))
    if (!t) throw new Falla(404, 'Turno no encontrado')
    if (x[2] === 'conteo') {
      if (t.estado !== 'CERRADO') throw new Falla(409, 'El conteo se registra al cerrar el turno o después de cerrarlo')
      Object.assign(t, { contadoUsd: Number(b.efectivoUsd), contadoBs: Number(b.efectivoBs), contadoPor: yo.nombre, contadoEn: new Date().toISOString() })
    }
    return corte(t)
  }
  if (camino === '/api/dashboard/dia') {
    exigir(gerencia)
    const fecha = q.get('fecha') || hoy()
    const ts = db.turnos.filter((t) => t.fechaOperativa === fecha)
    const vacio = { pedidos: 0, anulados: 0, pendientes: 0, ventaUsd: 0, recaudadoUsd: 0, recaudadoBs: 0, recaudadoTotalEquivUsd: 0, ticketPromedioUsd: 0 }
    if (!ts.length) {
      const h = db.historico.find((d) => d.fecha === fecha)
      return { fecha, totales: h ? { ...vacio, recaudadoUsd: h.recaudadoUsd, recaudadoBs: h.recaudadoBs, recaudadoTotalEquivUsd: h.totalEquivUsd } : vacio,
        turnos: [], porMetodo: [], porMesero: [] }
    }
    const c = calcular(ts.map((t) => t.id))
    return { fecha, totales: c.totales, turnos: ts.map((t) => ({ turnoId: t.id, tipo: t.tipo, estado: t.estado, totales: calcular([t.id]).totales })),
      porMetodo: c.porMetodo, porMesero: c.porMesero }
  }
  if (camino === '/api/dashboard/rango') {
    exigir(gerencia)
    const hasta = q.get('hasta') || hoy()
    const desde = q.get('desde') || hoy(6)
    const dias = []
    for (let d = new Date(desde + 'T12:00'); d <= new Date(hasta + 'T12:00'); d = new Date(d.getTime() + 864e5)) {
      const f = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
      const ts = db.turnos.filter((t) => t.fechaOperativa === f)
      if (ts.length) {
        const c = calcular(ts.map((t) => t.id)).totales
        dias.push({ fecha: f, recaudadoUsd: c.recaudadoUsd, recaudadoBs: c.recaudadoBs, totalEquivUsd: c.recaudadoTotalEquivUsd })
      } else dias.push(db.historico.find((h) => h.fecha === f) ?? { fecha: f, recaudadoUsd: 0, recaudadoBs: 0, totalEquivUsd: 0 })
    }
    const suma = (k) => r2(dias.reduce((s, d) => s + d[k], 0))
    return { desde, hasta, recaudadoUsd: suma('recaudadoUsd'), recaudadoBs: suma('recaudadoBs'), totalEquivUsd: suma('totalEquivUsd'), dias }
  }
  throw new Falla(404, `Ruta de demo no implementada: ${metodo} ${camino}`)
}
