# Prueba de carga

Simula clientes de la web pidiendo al mismo tiempo mientras la caja revisa y acepta pedidos (como el POS):
cada cliente abre la página, carga el menú, manda su pedido (10 % repite el envío para probar que no se
duplica) y consulta el estado 3 veces. Corre una oleada de 100 y otra de 200 clientes simultáneos.

**En el PC:** doble clic en `prueba-carga.bat`. Reinicia el servidor de prueba con los límites por IP altos
(todos los clientes salen del mismo PC), corre `carga.mjs` con Node 18+ y deja el informe en `carga.log`.
Al terminar, usa `reiniciar.bat` para volver a los límites normales.

**Contra el servidor:** `node carga.mjs` cambiando `BASE` por la dirección del servidor. Allá los límites por
IP están activos: sube `LIMITE_PEDIDOS_WEB`, `LIMITE_CONSULTAS_WEB` y `LIMITE_API` solo mientras dure la prueba.

El resultado es OK si no hay errores ni pedidos perdidos o duplicados, y la caja ve todos los pedidos.
