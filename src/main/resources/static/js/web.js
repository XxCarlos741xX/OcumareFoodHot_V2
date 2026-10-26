/* Página de pedidos de Ocumare Food Hot. La configuración del local está justo abajo (✏️ 3). */
/* =====================================================================
   ✏️ 3. CONFIGURACIÓN DEL LOCAL
   ===================================================================== */
const CONFIG = {
  // Dirección del sistema Ocumare Food. Vacía = la web y el sistema están en el mismo servidor (lo normal).
  // Solo si la web se publica aparte (p. ej. GitHub Pages), pon aquí la dirección del servidor: 'https://midominio.com'
  API_URL: '',

  NOMBRE: 'Ocumare Food Hot',
  WHATSAPP: '584243157134',          // número del local, sin + ni espacios
  MONEDA: '$',
  NOTA_DELIVERY: 'El costo del delivery depende de tu zona y te lo confirmamos por WhatsApp.',
  METODOS_PAGO: ['Pago Móvil', 'Efectivo ($)', 'Efectivo (Bs)', 'Punto de venta al recibir', 'Zelle'],
};


/* =====================================================================
   A partir de aquí no hace falta cambiar nada.
   ===================================================================== */
const EMOJI = {'Hamburguesas':'🍔','Perros y pepitos':'🌭','Pollo':'🍗','Parrillas':'🥩','Pescados y mariscos':'🐟','Pastas y risottos':'🍝','Ensaladas':'🥗','Sopas':'🍲','Almuerzos':'🍛'};
let MENU = [];
let carrito = leerCarrito();   // { idProducto: cantidad }

const $ = (id) => document.getElementById(id);
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const dinero = (n) => CONFIG.MONEDA + (Number(n) % 1 ? Number(n).toFixed(2) : Number(n));
const bolivares = (n) => 'Bs ' + Number(n).toLocaleString('es-VE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const slug = (t) => 'cat-' + String(t).toLowerCase().normalize('NFD').replace(/[^a-z0-9]+/g, '-');

function leerCarrito(){ try { return JSON.parse(localStorage.getItem('carrito-ocumare') || '{}'); } catch { return {}; } }
function guardarCarrito(){ try { localStorage.setItem('carrito-ocumare', JSON.stringify(carrito)); } catch {} }

const API = CONFIG.API_URL.replace(/\/$/, '') + '/api';
let TASA = null;   // Bs por dólar, si el local la cargó

// La carta viene del sistema del local: lo que el gerente cambia (precios, agotados, destacados) se ve aquí.
async function cargarMenu(){
  $('link-wa').href = 'https://wa.me/' + CONFIG.WHATSAPP;
  try {
    const r = await fetch(API + '/menu');
    if (!r.ok) throw new Error('menu ' + r.status);
    const datos = await r.json();
    TASA = datos.tasaBsPorUsd;
    MENU = datos.categorias.flatMap(c => c.productos.map(p => ({
      id: String(p.id), categoria: c.nombre, nombre: p.nombre, descripcion: p.descripcion || '',
      precio: Number(p.precioUsd), precioBs: p.precioBs, imagen: p.imagenUrl || '',
      destacado: p.destacado, disponible: p.disponible,
    })));
    pintarMenu();
  } catch (e) {
    $('menu').innerHTML = '<p class="error" style="margin-top:24px">No pudimos cargar el menú. Revisa tu conexión o pide directo por <a href="https://wa.me/' + CONFIG.WHATSAPP + '">WhatsApp</a>.</p>';
  }
}

function foto(p, clase){
  return p.imagen
    ? `<img src="${esc(p.imagen)}" alt="${esc(p.nombre)}" loading="lazy" width="640" height="640" data-foto>`
    : `<div class="sin-foto"><div><span>${EMOJI[p.categoria] || '🍽️'}</span>${esc(p.nombre)}</div></div>`;
}

// Si una foto no carga (falta el archivo en img/), se muestra el plato con su ícono.
function sinFoto(img){
  const p = MENU.find(x => x.nombre === img.alt);
  const div = document.createElement('div');
  div.className = 'sin-foto';
  div.innerHTML = `<div><span>${EMOJI[p && p.categoria] || '🍽️'}</span>${esc(img.alt)}</div>`;
  img.replaceWith(div);
}

function pintarMenu(){
  Object.keys(carrito).forEach(id => { const p = MENU.find(x => String(x.id) === id); if (!p || !p.disponible) delete carrito[id]; });

  const top = MENU.filter(p => p.destacado);
  $('destacados').innerHTML = top.map(p => `
    <article class="top">
      ${foto(p)}
      <div class="info">
        <div><h3>${esc(p.nombre)}</h3><span class="precio">${dinero(p.precio)}</span>${p.precioBs != null ? `<span class="precio-bs">${bolivares(p.precioBs)}</span>` : ''}</div>
        <div class="contador" data-ctr="${esc(p.id)}"></div>
      </div>
    </article>`).join('');
  $('destacados').closest('main').style.display = top.length ? '' : 'none';
  prepararCarrusel();

  const categorias = [...new Set(MENU.map(p => p.categoria || 'Otros'))];
  $('nav').innerHTML = categorias.map(c => `<a href="#${slug(c)}">${EMOJI[c] || ''} ${esc(c)}</a>`).join('');
  $('menu').innerHTML = categorias.map(c => `
    <section id="${slug(c)}">
      <h2 class="seccion-titulo">${esc(c)}</h2>
      <div class="grilla">
        ${MENU.filter(p => (p.categoria || 'Otros') === c).map(p => `
          <article class="producto${p.disponible ? '' : ' agotado'}" data-prod="${esc(p.id)}">
            <div class="foto">${foto(p)}${p.destacado ? '<span class="badge">🔥 Top</span>' : ''}</div>
            <div class="info">
              <h3>${esc(p.nombre)}</h3>
              <p>${esc(p.descripcion)}</p>
              <div class="fila">
                <span><span class="precio">${dinero(p.precio)}</span>${p.precioBs != null ? `<span class="precio-bs">${bolivares(p.precioBs)}</span>` : ''}</span>
                <div class="contador" data-ctr="${esc(p.id)}"></div>
              </div>
            </div>
          </article>`).join('')}
      </div>
    </section>`).join('');
  MENU.forEach(p => pintarContador(p.id));
  actualizarBarra(false);
  observarCategorias();
}


/* ---------- Carrusel "Lo más pedido" ----------
   Celular: se desliza con el dedo. Computador: flechas, puntos o arrastrar con el mouse.
   Avanza solo cada 4 s y se detiene en cuanto la persona lo toca.
   Mientras se está moviendo solo, un toque en "+" no cuenta: el plato bajo el dedo podría
   ser otro del que la persona vio, así que se detiene y se ignora ese toque. */
const car = { listo: false, auto: null, pausado: false, moviendoHasta: 0 };

function tarjetasCarrusel(){ return [...$('destacados').querySelectorAll('.top')]; }
function pasoCarrusel(){ const t = tarjetasCarrusel(); return t.length > 1 ? t[1].offsetLeft - t[0].offsetLeft : $('destacados').clientWidth; }
function indiceCarrusel(){ return Math.round($('destacados').scrollLeft / pasoCarrusel()); }
function irACarrusel(i){
  const c = $('destacados'), max = c.scrollWidth - c.clientWidth;
  c.scrollTo({ left: Math.max(0, Math.min(max, i * pasoCarrusel())), behavior: 'smooth' });
}
function actualizarCarrusel(){
  const c = $('destacados'), max = c.scrollWidth - c.clientWidth - 4;
  $('flecha-izq').disabled = c.scrollLeft <= 4;
  $('flecha-der').disabled = c.scrollLeft >= max;
  const total = tarjetasCarrusel().length, visibles = Math.max(1, Math.round(c.clientWidth / pasoCarrusel()));
  const paginas = Math.max(1, total - visibles + 1);
  const puntos = $('puntos');
  if (puntos.children.length !== paginas) {
    puntos.innerHTML = Array.from({ length: paginas }, (_, i) => `<button type="button" aria-label="Ir al plato ${i + 1}" data-punto="${i}"></button>`).join('');
  }
  puntos.style.display = paginas > 1 ? '' : 'none';
  const actual = c.scrollLeft >= max ? paginas - 1 : Math.min(indiceCarrusel(), paginas - 1);
  [...puntos.children].forEach((b, i) => b.classList.toggle('activo', i === actual));
}
function pararAuto(){ car.pausado = true; clearInterval(car.auto); }
function iniciarAuto(){
  clearInterval(car.auto);
  if (car.pausado || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  car.auto = setInterval(() => {
    const c = $('destacados');
    car.moviendoHasta = Date.now() + 900;
    if (c.scrollLeft >= c.scrollWidth - c.clientWidth - 4) irACarrusel(0); else irACarrusel(indiceCarrusel() + 1);
  }, 4000);
}

function prepararCarrusel(){
  const c = $('destacados');
  if (!car.listo) {
    car.listo = true;
    $('flecha-izq').addEventListener('click', () => { pararAuto(); irACarrusel(indiceCarrusel() - 1); });
    $('flecha-der').addEventListener('click', () => { pararAuto(); irACarrusel(indiceCarrusel() + 1); });
    $('puntos').addEventListener('click', e => { const b = e.target.closest('[data-punto]'); if (b) { pararAuto(); irACarrusel(Number(b.dataset.punto)); } });
    c.addEventListener('scroll', () => requestAnimationFrame(actualizarCarrusel), { passive: true });
    c.addEventListener('touchstart', pararAuto, { passive: true });
    c.addEventListener('pointerdown', e => { if (e.pointerType !== 'mouse') pararAuto(); });
    c.addEventListener('focusin', pararAuto);
    c.addEventListener('mouseenter', () => clearInterval(car.auto));
    c.addEventListener('mouseleave', iniciarAuto);
    window.addEventListener('resize', actualizarCarrusel);

    // Arrastrar con el mouse (en celular el dedo ya funciona de forma nativa)
    let x0 = 0, s0 = 0, movio = false, activo = false;
    c.addEventListener('pointerdown', e => {
      if (e.pointerType !== 'mouse' || e.button !== 0) return;
      activo = true; movio = false; x0 = e.clientX; s0 = c.scrollLeft; pararAuto();
    });
    window.addEventListener('pointermove', e => {
      if (!activo) return;
      const dx = e.clientX - x0;
      if (!movio && Math.abs(dx) > 5) { movio = true; c.classList.add('arrastrando'); }
      if (movio) c.scrollLeft = s0 - dx;
    });
    window.addEventListener('pointerup', () => {
      if (!activo) return;
      activo = false;
      if (movio) { c.classList.remove('arrastrando'); irACarrusel(indiceCarrusel()); }
    });
    // Si fue un arrastre, que no cuente como toque en el botón "+"
    c.addEventListener('click', e => { if (movio) { e.stopPropagation(); e.preventDefault(); movio = false; } }, true);
    // Toque en "+" o "−" justo mientras el carrusel avanzaba solo: se detiene y no se agrega nada
    c.addEventListener('click', e => {
      if (e.target.closest('[data-d]') && Date.now() < car.moviendoHasta) {
        e.stopPropagation(); e.preventDefault(); pararAuto();
      }
    }, true);
    iniciarAuto();
  }
  requestAnimationFrame(actualizarCarrusel);
}

function pintarContador(id){
  const prod = MENU.find(p => String(p.id) === String(id));
  if (prod && !prod.disponible) {
    delete carrito[id];
    document.querySelectorAll(`[data-ctr="${CSS.escape(id)}"]`).forEach(c => { c.innerHTML = '<span class="agotado-txt">Agotado</span>'; });
    return;
  }
  const cant = carrito[id] || 0;
  document.querySelectorAll(`[data-ctr="${CSS.escape(id)}"]`).forEach(cont => {
    cont.innerHTML = cant
      ? `<button class="menos" type="button" aria-label="Quitar uno" data-id="${esc(id)}" data-d="-1">−</button><span>${cant}</span><button type="button" aria-label="Agregar uno" data-id="${esc(id)}" data-d="1">+</button>`
      : `<button type="button" aria-label="Agregar" data-id="${esc(id)}" data-d="1">+</button>`;
  });
  const card = document.querySelector(`[data-prod="${CSS.escape(id)}"]`);
  if (card) card.classList.toggle('en-carrito', cant > 0);
}

document.addEventListener('click', (e) => {
  const quitar = e.target.closest('button[data-quitar]');
  if (quitar) {
    const id = quitar.dataset.quitar;
    delete carrito[id]; claveEnvio = null;
    guardarCarrito(); pintarContador(id); actualizarBarra(false); pintarResumen();
    return;
  }
  if (e.target.closest('button[data-vaciar]')) {
    const ids = Object.keys(carrito);
    carrito = {};
    guardarCarrito(); ids.forEach(pintarContador); actualizarBarra(false); pintarResumen();
    return;
  }
  const b = e.target.closest('button[data-id]');
  if (!b) return;
  const id = b.dataset.id, delta = Number(b.dataset.d);
  const nueva = Math.max(0, Math.min(30, (carrito[id] || 0) + delta));
  if (nueva) carrito[id] = nueva; else delete carrito[id];
  claveEnvio = null;
  guardarCarrito(); pintarContador(id); actualizarBarra(delta > 0);
  if ($('panel-fondo').classList.contains('abierto')) pintarResumen();
});

function lineasCarrito(){
  return Object.entries(carrito).map(([id, cant]) => {
    const p = MENU.find(x => String(x.id) === id);
    return p ? { ...p, cantidad: cant, subtotal: p.precio * cant } : null;
  }).filter(Boolean);
}

function actualizarBarra(animar){
  const lineas = lineasCarrito();
  const unidades = lineas.reduce((s, l) => s + l.cantidad, 0);
  const total = lineas.reduce((s, l) => s + l.subtotal, 0);
  $('barra').classList.toggle('visible', unidades > 0);
  $('barra-cant').textContent = `🛒 Ver pedido (${unidades})`;
  $('barra-total').textContent = dinero(total.toFixed(2)) + (TASA ? ' · ' + bolivares(total * TASA) : '');
  if (animar) { const b = $('btn-barra'); b.classList.remove('pulso'); void b.offsetWidth; b.classList.add('pulso'); }
}

/* ---------- Navegación entre categorías ----------
   - Al tocar una categoría se hace UN solo desplazamiento de la página hasta la sección.
   - Mientras ese desplazamiento ocurre, el resaltado no se recalcula (así no lo interrumpe).
   - La barra de categorías solo se mueve de lado (nunca mueve la página). */
let navegando = false, finNavegacion = null;
const altoNav = () => document.querySelector('nav.categorias').offsetHeight;

function marcarCategoria(id){
  const cont = $('nav');
  cont.querySelectorAll('a').forEach(a => {
    const on = a.getAttribute('href') === '#' + id;
    if (on && !a.classList.contains('activa')) {
      cont.scrollTo({ left: a.offsetLeft - (cont.clientWidth - a.offsetWidth) / 2, behavior: 'smooth' });
    }
    a.classList.toggle('activa', on);
  });
}

function seccionActual(){
  const limite = altoNav() + 24;
  let actual = null;
  document.querySelectorAll('#menu section').forEach(s => { if (s.getBoundingClientRect().top <= limite) actual = s; });
  return actual;
}

function terminarNavegacion(){ navegando = false; clearTimeout(finNavegacion); actualizarResaltado(); }

function actualizarResaltado(){
  if (navegando) return;
  const s = seccionActual();
  if (s) marcarCategoria(s.id);
  else $('nav').querySelectorAll('a').forEach(a => a.classList.remove('activa'));
}

function observarCategorias(){
  $('nav').querySelectorAll('a').forEach(a => a.addEventListener('click', (e) => {
    e.preventDefault();
    const id = a.getAttribute('href').slice(1);
    const destino = document.getElementById(id);
    if (!destino) return;
    navegando = true;
    marcarCategoria(id);
    const y = destino.getBoundingClientRect().top + window.scrollY - altoNav() - 8;
    window.scrollTo({ top: y, behavior: 'smooth' });
    clearTimeout(finNavegacion);
    finNavegacion = setTimeout(terminarNavegacion, 1500);   // respaldo si el navegador no avisa 'scrollend'
  }));
  actualizarResaltado();
}

let pendiente = false;
window.addEventListener('scroll', () => {
  if (pendiente) return;
  pendiente = true;
  requestAnimationFrame(() => { pendiente = false; actualizarResaltado(); });
}, { passive: true });
window.addEventListener('scrollend', () => { if (navegando) terminarNavegacion(); });
// Si la persona toca la pantalla mientras la página se mueve sola, le devolvemos el control
['touchstart', 'wheel'].forEach(ev => window.addEventListener(ev, () => { if (navegando) terminarNavegacion(); }, { passive: true }));

const ICONO_BASURA = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v6M14 11v6"/></svg>';

// Lista editable del carrito dentro del panel: cambiar cantidades o quitar platos
// sin salir del formulario (lo que la persona ya escribió no se pierde).
function pintarResumen(){
  const cont = $('resumen-pedido');
  if (!cont) return;
  const lineas = lineasCarrito();
  if (!lineas.length) { cerrarPanel(); return; }
  const total = lineas.reduce((s, l) => s + l.subtotal, 0);
  cont.innerHTML = `
    <ul class="resumen">${lineas.map(l => `
      <li data-linea="${esc(l.id)}">
        ${l.imagen ? `<img class="mini" src="${esc(l.imagen)}" alt="">` : `<span class="mini">${EMOJI[l.categoria] || '🍽️'}</span>`}
        <div><span class="nom">${esc(l.nombre)}</span><span class="sub">${dinero(l.subtotal.toFixed(2))}</span></div>
        <div class="acciones">
          <div class="contador" data-ctr="${esc(l.id)}"></div>
          <button class="quitar" type="button" data-quitar="${esc(l.id)}" aria-label="Quitar ${esc(l.nombre)} del pedido" title="Quitar">${ICONO_BASURA}</button>
        </div>
      </li>`).join('')}</ul>
    <button class="vaciar" type="button" data-vaciar>Vaciar pedido</button>
    <div class="total"><span>Total</span><span>${dinero(total.toFixed(2))}</span></div>
    ${TASA ? `<p class="nota-chica total-bs">${bolivares(total * TASA)}</p>
    <p class="aviso-tasa">${esc(avisoTasa())}</p>` : ''}`;
  lineas.forEach(l => pintarContador(l.id));
}

// La tasa la carga el local en el sistema (tasa oficial BCV del día).
function avisoTasa(){
  return `Monto en Bs a la tasa BCV de hoy (Bs ${Number(TASA).toLocaleString('es-VE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} por $1). Puede variar si pagas otro día.`;
}

function abrirPanel(){
  $('panel-contenido').innerHTML = `
    <h2 id="titulo-panel">Tu pedido</h2>
    <div id="resumen-pedido"></div>
    <p class="nota-chica">${esc(CONFIG.NOTA_DELIVERY)}</p>
    <form id="form">
      <label>Nombre<input name="nombre" required maxlength="60" autocomplete="name"></label>
      <label>Teléfono (WhatsApp)<input name="telefono" required inputmode="tel" minlength="7" maxlength="20" placeholder="Ej: 0424 1234567 · otro país: +57 300…" autocomplete="tel"></label>
      <div class="opciones">
        <label><input type="radio" name="entrega" value="delivery" checked> 🛵 Delivery</label>
        <label><input type="radio" name="entrega" value="retiro"> 🏪 Retiro</label>
      </div>
      <label id="campo-direccion">Dirección y punto de referencia<textarea name="direccion" maxlength="200" required placeholder="Sector, calle, casa y una referencia"></textarea></label>
      <label>Forma de pago<select name="pago" required>${CONFIG.METODOS_PAGO.map(m => `<option>${esc(m)}</option>`).join('')}</select></label>
      <label>Notas (opcional)<textarea name="notas" maxlength="300" placeholder="Sin cebolla, salsa aparte, carne o pollo…"></textarea></label>
      <label class="acepto"><input type="checkbox" name="acepto" required>
        <span>Acepto los <a href="legal/terminos.html" target="_blank" rel="noopener">Términos y Condiciones</a> y autorizo el tratamiento de mis datos según la <a href="legal/privacidad.html" target="_blank" rel="noopener">Política de Privacidad</a>.
        <small>Usamos tu nombre, teléfono y dirección solo para preparar, cobrar y entregar tu pedido. Puedes pedir verlos, corregirlos o borrarlos por WhatsApp.</small></span></label>
      <input class="oculto" name="website" tabindex="-1" autocomplete="off" aria-hidden="true">
      <p class="error" id="error"></p>
      <button class="btn" id="btn-enviar" type="submit">Enviar pedido 🔥</button>
    </form>`;
  pintarResumen();
  $('panel-fondo').classList.add('abierto');
}
function cerrarPanel(){ $('panel-fondo').classList.remove('abierto'); }

function toggleDireccion(){
  const esDelivery = document.querySelector('input[name=entrega]:checked').value === 'delivery';
  $('campo-direccion').style.display = esDelivery ? '' : 'none';
  $('campo-direccion').querySelector('textarea').required = esDelivery;
}

let enviando = false;      // evita pedidos duplicados por doble clic o Enter repetido
let claveEnvio = null;     // misma clave en los reintentos: si el primer intento sí llegó, no se duplica

async function enviarPedido(ev){
  ev.preventDefault();
  if (enviando) return;
  enviando = true;
  const f = new FormData(ev.target);
  if (!claveEnvio) claveEnvio = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : Date.now() + '-' + Math.random().toString(36).slice(2);
  const entrega = f.get('entrega');
  const pedido = {
    claveExterna: claveEnvio,
    clienteNombre: f.get('nombre'), clienteTelefono: f.get('telefono'),
    entrega: entrega === 'delivery' ? 'DELIVERY' : 'RETIRO',
    direccionEntrega: entrega === 'delivery' ? f.get('direccion') : null,
    pago: f.get('pago'), notas: f.get('notas'), website: f.get('website'),
    aceptaTerminos: f.get('acepto') === 'on', versionPoliticas: typeof LEGAL !== 'undefined' ? LEGAL.VERSION : null,
    items: Object.entries(carrito).map(([id, cantidad]) => ({ productoId: Number(id), cantidad })),
  };
  const lineas = lineasCarrito();
  const btn = $('btn-enviar');
  btn.disabled = true; btn.textContent = 'Enviando…'; $('error').textContent = '';

  try {
    let resp, r;
    const corte = new AbortController();
    const reloj = setTimeout(() => corte.abort(), 30000);
    try {
      // La misma clave en cada intento: si el primero sí llegó, el sistema no lo duplica.
      resp = await fetch(API + '/web/pedidos', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(pedido), signal: corte.signal,
      });
      r = await resp.json().catch(() => null);
    } catch (x) {
      throw new Error('No pudimos conectarnos. Revisa tu internet e intenta de nuevo (tu pedido no se duplicará).');
    } finally { clearTimeout(reloj); }
    if (!resp.ok) throw new Error((r && r.mensaje) || 'No se pudo registrar el pedido.');
    mostrarExito(r, pedido, lineas);
    carrito = {}; claveEnvio = null; guardarCarrito(); pintarMenu();
  } catch (e) {
    const msg = e.message.trim();
    $('error').textContent = msg + (/[.!?]$/.test(msg) ? '' : '.') + ' Si el problema sigue, pide por WhatsApp.';
    btn.disabled = false; btn.textContent = 'Enviar pedido 🔥';
  } finally {
    enviando = false;
  }
}

// Escape cierra el panel del pedido (teclado / computador)
document.addEventListener('keydown', e => { if (e.key === 'Escape' && $('panel-fondo').classList.contains('abierto')) cerrarPanel(); });

const ESTADO_CLIENTE = {
  ABIERTO: 'Recibido, en cola',
  EN_PREPARACION: 'En la cocina 🔥',
  LISTO: entregaTxt => entregaTxt === 'DELIVERY' ? 'Listo, saliendo pronto 🛵' : 'Listo para retirar 🏪',
  ENTREGADO: 'Entregado',
  PAGADO: 'Entregado y pagado. ¡Buen provecho!',
  ANULADO: 'Anulado. Escríbenos por WhatsApp',
};
let seguimiento = null;

function mostrarExito(r, pedido, lineas){
  const detalle = lineas.map(l => `${l.cantidad} x ${l.nombre}`).join('\n');
  const totalTxt = dinero(Number(r.totalUsd).toFixed(2)) + (r.totalBs != null ? ` (${bolivares(r.totalBs)})` : '');
  const texto = `Hola ${CONFIG.NOMBRE}! 👋 Acabo de hacer el pedido #${r.numero} a nombre de ${pedido.clienteNombre}.\n${detalle}\nTotal: ${totalTxt} · Pago: ${pedido.pago}`;
  $('panel-contenido').innerHTML = `
    <div class="exito">
      <p class="nota-chica">Tu número de pedido</p>
      <p class="grande">#${esc(r.numero)}</p>
      <h2>¡Pedido recibido! 🌴🔥</h2>
      <p>Total: <strong class="oro">${esc(totalTxt)}</strong></p>
      ${r.totalBs != null ? '<p class="nota-chica">El monto en Bs es a la tasa BCV de hoy y puede variar si pagas otro día. El delivery se confirma aparte.</p>' : ''}
      <p class="estado-pedido" aria-live="polite">Estado: <strong id="estado-pedido">${esc(textoEstado(r.estado, pedido.entrega))}</strong></p>
      <p class="nota-chica" style="margin-top:8px">Ya le llegó al equipo. Te escribimos al WhatsApp que dejaste para confirmar ${pedido.entrega === 'DELIVERY' ? 'el delivery y ' : ''}el pago.</p>
      <a class="btn btn-wa" target="_blank" rel="noopener" href="https://wa.me/${CONFIG.WHATSAPP}?text=${encodeURIComponent(texto)}">Enviar comprobante por WhatsApp</a>
      <button class="btn btn-sec" type="button" data-cerrar>Seguir viendo el menú</button>
    </div>`;
  seguirPedido(pedido.claveExterna, pedido.entrega);
}

function textoEstado(estado, entrega){
  const t = ESTADO_CLIENTE[estado];
  return typeof t === 'function' ? t(entrega) : (t || estado);
}

// Consulta cada 20 s cómo va el pedido mientras la pantalla de éxito está abierta.
// Al volver a la pestaña también se actualiza enseguida.
let consultarEstado = null;
function seguirPedido(clave, entrega){
  clearInterval(seguimiento);
  consultarEstado = async () => {
    const el = $('estado-pedido');
    if (!el) { clearInterval(seguimiento); return; }
    if (document.visibilityState !== 'visible') return;
    try {
      const r = await fetch(API + '/web/pedidos/' + encodeURIComponent(clave));
      if (!r.ok) return;
      const d = await r.json();
      el.textContent = textoEstado(d.estado, entrega);
      if (d.estado === 'PAGADO' || d.estado === 'ANULADO') clearInterval(seguimiento);
    } catch { /* sin conexión: se reintenta en el próximo ciclo */ }
  };
  seguimiento = setInterval(consultarEstado, 20000);
}
document.addEventListener('visibilitychange', () => { if (consultarEstado) consultarEstado(); });

// Eventos de la página. No se usan atributos onclick/onerror en el HTML: la política de seguridad (CSP)
// solo deja correr este archivo, así que un texto inyectado nunca puede ejecutar código.
$('btn-barra').addEventListener('click', abrirPanel);
$('panel-fondo').addEventListener('click', e => {
  if (e.target === e.currentTarget || e.target.closest('[data-cerrar]')) cerrarPanel();
});
$('panel-fondo').addEventListener('submit', e => { if (e.target.id === 'form') enviarPedido(e); });
$('panel-fondo').addEventListener('change', e => { if (e.target.name === 'entrega') toggleDireccion(); });
// Si una foto no carga, se cambia por el ícono del plato (el evento "error" no burbujea: se escucha en captura).
document.addEventListener('error', e => { if (e.target.matches && e.target.matches('img[data-foto]')) sinFoto(e.target); }, true);

cargarMenu();
