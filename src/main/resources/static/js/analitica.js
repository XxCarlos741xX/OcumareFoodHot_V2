/* =====================================================================
   ✏️ GOOGLE ANALYTICS
   Pega aquí el ID de medición de Google Analytics 4 (empieza por G-).
   Se saca en analytics.google.com > Administrar > Flujos de datos > Web.
   Vacío ('') = Analytics apagado y no sale el aviso de cookies.
   ===================================================================== */
const ANALITICA = {
  ID: '',
};


/* A partir de aquí no hace falta cambiar nada.
 *
 * Analytics solo se carga si el cliente toca "Aceptar" en el aviso. Si rechaza o no responde,
 * la página no le pide nada a Google Analytics. La respuesta se guarda en el navegador
 * ('cookies-ocumare') y se puede cambiar con el enlace "Configurar cookies" del pie.
 * Nunca se envían a Google el nombre, el teléfono, la dirección ni las notas del cliente.
 */
(function () {
  const CLAVE = 'cookies-ocumare';
  const id = String(ANALITICA.ID || '').trim();
  const activa = /^G-[A-Z0-9]{4,}$/i.test(id);
  let cargada = false;

  const leer = () => { try { return localStorage.getItem(CLAVE); } catch { return null; } };
  const guardar = (v) => { try { localStorage.setItem(CLAVE, v); } catch {} };

  function cargar() {
    if (cargada) return;
    cargada = true;
    window.dataLayer = window.dataLayer || [];
    window.gtag = function () { window.dataLayer.push(arguments); };
    window.gtag('js', new Date());
    // Sin señales de publicidad ni personalización de anuncios.
    window.gtag('config', id, { allow_google_signals: false, allow_ad_personalization_signals: false });
    const s = document.createElement('script');
    s.async = true;
    s.src = 'https://www.googletagmanager.com/gtag/js?id=' + encodeURIComponent(id);
    document.head.appendChild(s);
  }

  // Si el cliente retira el permiso, se borran las cookies de Analytics de este sitio.
  function borrarCookiesGa() {
    const dominios = ['', location.hostname, '.' + location.hostname.replace(/^www\./, '')];
    document.cookie.split(';').map((c) => c.split('=')[0].trim()).filter((n) => /^_ga/.test(n)).forEach((n) => {
      dominios.forEach((d) => {
        document.cookie = n + '=; Max-Age=0; path=/' + (d ? '; domain=' + d : '');
      });
    });
  }

  const ESTILO = `
.aviso-cookies{position:fixed;left:12px;right:12px;bottom:12px;z-index:9999;max-width:560px;margin:0 auto;background:#1A1612;color:#FBF6EE;
  border:1px solid #2F2820;border-radius:16px;padding:14px 16px;box-shadow:0 10px 30px rgba(0,0,0,.5);font:500 .85rem/1.45 'Montserrat',system-ui,sans-serif}
.aviso-cookies p{margin:0 0 10px}
.aviso-cookies a{color:#E9B949;font-weight:700}
.aviso-cookies .botones{display:flex;gap:8px;flex-wrap:wrap}
.aviso-cookies button{flex:1 1 120px;min-height:44px;border-radius:999px;font:800 .85rem 'Montserrat',system-ui,sans-serif;cursor:pointer}
.aviso-cookies .si{background:linear-gradient(95deg,#E8811E,#F5A623);color:#1A1209;border:0}
.aviso-cookies .no{background:transparent;color:#FBF6EE;border:1px solid #5A4E40}`;

  function mostrarAviso() {
    if (document.querySelector('.aviso-cookies')) return;
    if (!document.getElementById('estilo-aviso-cookies')) {
      const st = document.createElement('style');
      st.id = 'estilo-aviso-cookies';
      st.textContent = ESTILO;
      document.head.appendChild(st);
    }
    const enLegal = /\/legal\//.test(location.pathname);
    const div = document.createElement('div');
    div.className = 'aviso-cookies';
    div.setAttribute('role', 'dialog');
    div.setAttribute('aria-label', 'Aviso de cookies');
    div.innerHTML = '<p>Usamos Google Analytics para saber cuántas personas visitan la página y mejorarla. '
      + 'Solo se activa si aceptas. <a href="' + (enLegal ? '' : 'legal/') + 'cookies.html">Política de cookies</a></p>'
      + '<div class="botones"><button type="button" class="no">Rechazar</button><button type="button" class="si">Aceptar</button></div>';
    div.querySelector('.si').addEventListener('click', () => { guardar('si'); div.remove(); cargar(); });
    div.querySelector('.no').addEventListener('click', () => {
      const antes = leer();
      guardar('no');
      div.remove();
      borrarCookiesGa();
      if (antes === 'si' && cargada) location.reload();   // para que Analytics deje de correr ya
    });
    document.body.appendChild(div);
  }

  // Enlaces "Configurar cookies" (data-config-cookies): vuelven a mostrar el aviso.
  function enlazar() {
    document.querySelectorAll('[data-config-cookies]').forEach((a) => {
      if (!activa) { a.hidden = true; return; }
      a.addEventListener('click', (e) => { e.preventDefault(); mostrarAviso(); });
    });
  }

  /** Para web.js: registra un evento sin datos personales. No hace nada sin permiso. */
  window.analitica = {
    evento(nombre, datos) {
      if (cargada && typeof window.gtag === 'function') window.gtag('event', nombre, datos || {});
    },
  };

  function iniciar() {
    enlazar();
    if (!activa) return;
    const r = leer();
    if (r === 'si') cargar();
    else if (r !== 'no') mostrarAviso();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar); else iniciar();
})();
