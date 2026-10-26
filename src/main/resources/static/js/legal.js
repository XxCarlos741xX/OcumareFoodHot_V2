/* =====================================================================
   ✏️ DATOS LEGALES DEL NEGOCIO
   Se usan en las páginas de Términos, Privacidad, Cookies y Reembolsos, y en la casilla del pedido.
   Lo que quede vacío ('') sale en las páginas como [pendiente: …] para que se note que falta.
   ===================================================================== */
const LEGAL = {
  // Cambia VERSION y FECHA cada vez que cambies el texto de las políticas.
  // Cada pedido web guarda la versión que el cliente aceptó.
  VERSION: '2026-10-25c',
  FECHA: '25 de octubre de 2026',

  RAZON_SOCIAL: 'DISTRIBUIDORA LIENDO 2424, OCUMAREFOODHOT, C.A.',
  RIF: 'J-41238529-1',
  DIRECCION: 'Coliseo, El Limón, diagonal a la pasarela, Maracay, estado Aragua, Venezuela',
  CORREO: 'ocumarefoodhot.ve@gmail.com',   // reclamos y solicitudes de datos personales
  WHATSAPP: '+58 424 315 7134',
  ENCARGADO: 'Carlos Coronel (Medellín, Colombia)',
  HOSTING: '',                      // proveedor del servidor, p. ej. OVHcloud
  BASE_DATOS: '',                   // proveedor de la base de datos, p. ej. Aiven (o "el mismo servidor")
  PLAZO_DATOS: '2 años',            // cuánto se guardan los datos de los pedidos antes de anonimizarlos
  PLAZO_REEMBOLSO: '3 días hábiles',
};


/* A partir de aquí no hace falta cambiar nada. */
(function () {
  const NOMBRES = { RAZON_SOCIAL: 'razón social', RIF: 'RIF', DIRECCION: 'dirección', CORREO: 'correo', HOSTING: 'proveedor del servidor',
    BASE_DATOS: 'proveedor de la base de datos', PLAZO_DATOS: 'plazo', PLAZO_REEMBOLSO: 'plazo' };
  const pendiente = (clave) => '[pendiente: ' + (NOMBRES[clave] || clave.toLowerCase()) + ']';
  function llenar() {
    document.querySelectorAll('[data-legal]').forEach((el) => {
      const clave = el.dataset.legal;
      const valor = clave === 'DOMINIO' ? location.host : LEGAL[clave];
      el.textContent = valor || pendiente(clave);
      if (!valor) el.classList.add('falta');
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', llenar); else llenar();
})();
