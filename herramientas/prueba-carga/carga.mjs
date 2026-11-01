// Prueba de carga de Ocumare Food: clientes de la web pidiendo al mismo tiempo,
// mientras la caja revisa y acepta pedidos como lo hace el POS.
const BASE = 'http://localhost:8080';
const OLEADAS = [100, 200];          // clientes simultáneos por oleada
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const stats = {};                     // endpoint -> { tiempos: [], estados: {} }

async function llamar(nombre, url, opciones = {}) {
  const t0 = performance.now();
  let estado = 'ERROR_RED', cuerpo = null, headers = null;
  try {
    const r = await fetch(BASE + url, { ...opciones, signal: AbortSignal.timeout(30000) });
    estado = r.status; headers = r.headers;
    const texto = await r.text();
    try { cuerpo = JSON.parse(texto); } catch { cuerpo = texto; }
  } catch (e) {
    estado = e.name === 'TimeoutError' ? 'TIMEOUT_30s' : 'ERROR_RED';
  }
  const ms = performance.now() - t0;
  const s = (stats[nombre] ??= { tiempos: [], estados: {} });
  s.tiempos.push(ms); s.estados[estado] = (s.estados[estado] ?? 0) + 1;
  return { estado, cuerpo, headers, ms };
}

const json = (cuerpo, cookie) => ({
  headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
  body: JSON.stringify(cuerpo),
});
const pct = (arr, p) => { const a = [...arr].sort((x, y) => x - y); return a.length ? a[Math.min(a.length - 1, Math.floor(p / 100 * a.length))] : 0; };

async function esperarServidor() {
  for (let i = 0; i < 90; i++) {
    try { const r = await fetch(BASE + '/api/menu'); if (r.ok) return; } catch {}
    await sleep(2000);
  }
  throw new Error('El servidor no arrancó en 3 minutos');
}

async function entrarCaja() {
  const r = await fetch(BASE + '/api/auth/login', { method: 'POST', ...json({ username: 'caja', password: 'caja12345' }) });
  if (r.status !== 200) throw new Error('No se pudo entrar como caja: ' + r.status);
  return r.headers.get('set-cookie').split(';')[0];
}

async function cliente(n, productos, resultados) {
  const clave = 'carga-' + Date.now() + '-' + n + '-' + Math.random().toString(36).slice(2, 8);
  await llamar('GET / (página)', '/');
  await llamar('GET /api/menu', '/api/menu');
  await sleep(Math.random() * 2000);               // el cliente arma su carrito
  const items = Array.from({ length: 1 + Math.floor(Math.random() * 3) }, () => ({
    productoId: productos[Math.floor(Math.random() * productos.length)], cantidad: 1 + Math.floor(Math.random() * 2) }));
  const pedido = { claveExterna: clave, aceptaTerminos: true, versionPoliticas: '2026-10-25',
    clienteNombre: 'Cliente ' + n, clienteTelefono: '0414' + String(1000000 + n).slice(-7),
    entrega: n % 2 ? 'DELIVERY' : 'RETIRO', direccionEntrega: n % 2 ? 'Calle ' + n + ', El Limón' : null,
    pago: 'Pago Móvil', items };
  const r = await llamar('POST /api/web/pedidos', '/api/web/pedidos', { method: 'POST', ...json(pedido) });
  resultados.push({ clave, estado: r.estado, numero: r.cuerpo?.numero });
  if (r.estado !== 201) return;
  if (n % 10 === 0) {                                 // 10 %: doble clic / reintento con la misma clave
    const otra = await llamar('POST /api/web/pedidos (reintento)', '/api/web/pedidos', { method: 'POST', ...json(pedido) });
    if (otra.cuerpo?.numero !== r.cuerpo?.numero) resultados.push({ clave, duplicado: true });
  }
  for (let i = 0; i < 3; i++) {                      // la página de seguimiento consulta el estado
    await sleep(1500);
    await llamar('GET /api/web/pedidos/{clave}', '/api/web/pedidos/' + clave);
  }
}

async function caja(cookie, parar) {
  let aceptados = 0;
  while (!parar.ya) {
    const r = await llamar('GET /api/pedidos (caja)', '/api/pedidos?soloAbiertos=true', { headers: { Cookie: cookie } });
    if (Array.isArray(r.cuerpo)) {
      for (const p of r.cuerpo.filter((x) => x.tipo === 'WEB' && x.estado === 'ABIERTO').slice(0, 5)) {
        const a = await llamar('PATCH estado (caja acepta)', '/api/pedidos/' + p.id + '/estado',
          { method: 'PATCH', ...json({ estado: 'EN_PREPARACION' }, cookie) });
        if (a.estado === 200) aceptados++;
      }
    }
    await sleep(1000);
  }
  return aceptados;
}

function informe(titulo) {
  console.log('\n' + titulo);
  console.log('Endpoint'.padEnd(36) + 'Llamadas  p50(ms)  p95(ms)  max(ms)  Códigos');
  for (const [k, s] of Object.entries(stats)) {
    console.log(k.padEnd(36) + String(s.tiempos.length).padStart(8) + String(Math.round(pct(s.tiempos, 50))).padStart(9)
      + String(Math.round(pct(s.tiempos, 95))).padStart(9) + String(Math.round(Math.max(...s.tiempos))).padStart(9)
      + '  ' + JSON.stringify(s.estados));
  }
}

const inicio = Date.now();
await esperarServidor();
console.log('Servidor arriba en ' + Math.round((Date.now() - inicio) / 1000) + ' s');
const cookie = await entrarCaja();
const turno = await llamar('abrir turno', '/api/turnos/abrir', { method: 'POST', ...json({ tipo: 'NOCHE' }, cookie) });
console.log('Abrir turno: ' + turno.estado);
const productos = (await llamar('productos', '/api/productos', { headers: { Cookie: cookie } })).cuerpo
  .filter((p) => p.disponible && p.activo).map((p) => p.id);
console.log('Productos disponibles: ' + productos.length);

let fallas = 0;
for (const n of OLEADAS) {
  for (const k of Object.keys(stats)) delete stats[k];
  const resultados = [];
  const parar = { ya: false };
  const cajaTrabajando = caja(cookie, parar);
  const t0 = Date.now();
  await Promise.all(Array.from({ length: n }, (_, i) => cliente(i + 1, productos, resultados)));
  const segundos = ((Date.now() - t0) / 1000).toFixed(1);
  parar.ya = true;
  const aceptados = await cajaTrabajando;

  const ok = resultados.filter((r) => r.estado === 201);
  const numeros = new Set(ok.map((r) => r.numero));
  const duplicados = resultados.filter((r) => r.duplicado).length;
  informe(`=== Oleada de ${n} clientes simultáneos (${segundos} s) ===`);
  console.log(`Pedidos creados: ${ok.length}/${n} | números únicos: ${numeros.size} | reintentos que duplicaron: ${duplicados} | aceptados por caja: ${aceptados}`);
  const errores = Object.values(stats).flatMap((s) => Object.keys(s.estados)).filter((c) => !/^[23]/.test(c));
  if (ok.length !== n || numeros.size !== ok.length || duplicados > 0 || errores.length) {
    fallas++;
    console.log('>>> FALLA en esta oleada. Códigos fuera de 2xx/3xx: ' + JSON.stringify([...new Set(errores)]));
  } else {
    console.log('>>> OK: ningún error, ningún pedido perdido ni duplicado');
  }
}

// Cuadre final contra lo que ve la caja
const todos = (await llamar('pedidos del turno', '/api/pedidos', { headers: { Cookie: cookie } })).cuerpo;
const web = todos.filter((p) => p.tipo === 'WEB');
console.log(`\nLa caja ve ${web.length} pedidos web en el turno (esperados ${OLEADAS.reduce((a, b) => a + b, 0)}).`);
const menu = await llamar('menú final', '/api/menu');
console.log('El servidor sigue respondiendo después de la prueba: ' + (menu.estado === 200 ? 'sí' : 'NO (' + menu.estado + ')'));
console.log(fallas ? 'RESULTADO: CON FALLAS' : 'RESULTADO: OK');
process.exit(fallas ? 1 : 0);
