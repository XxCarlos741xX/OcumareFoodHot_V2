# Ocumare Food — API (Pasos 2 y 3)

Todas las rutas empiezan con `/api`. La sesión viaja en la cookie `ocumare_sesion` (HttpOnly, Secure,
SameSite=Strict, Path=/api) que pone `/auth/login`; el navegador la manda sola. Para pruebas o
herramientas también sirve la cabecera `Authorization: Bearer <token>`.
Si se pasa un límite por IP la respuesta es `429` con la cabecera `Retry-After` (segundos).
Errores: `{ "status", "error", "mensaje", "campos" }`.

## Público (sin login)
| Método | Ruta | Uso |
|---|---|---|
| POST | /auth/login | `{username, password}` → cookie de sesión + `{expiraEn, usuario}` (el token no va en el cuerpo) |
| POST | /auth/salir | Borra la cookie de sesión de este equipo |
| GET | /menu | Menú visible + precio en Bs si hay tasa |
| POST | /web/pedidos | Pedido de la web (idempotente por `claveExterna`) |
| GET | /web/pedidos/{claveExterna} | Estado del pedido web |

## Cualquier usuario con sesión
| Método | Ruta | Uso |
|---|---|---|
| GET | /auth/yo | Mis datos |
| PUT | /auth/password | `{passwordActual, passwordNueva}` → cookie nueva; cierra la sesión en los demás equipos |
| GET | /productos, /productos/{id}, /categorias, /mesas | Consultas (mesas trae `pedidoAbiertoId` si está ocupada) |
| GET | /tasas/vigente, /turnos/actual | Tasa y turno en curso |

## Roles

| Rol | Puede |
|---|---|
| MESERO | Tomar pedidos, agregar platos, enviarlos a cocina, quitar lo que todavía no salió a cocina, reimprimir. Su lista de pedidos muestra solo los que tomó él. |
| CAJERO | Todo lo del mesero, más: cobrar, aceptar pedidos web, quitar platos ya enviados a cocina, ver todos los pedidos y la bitácora, abrir y cerrar el turno con su conteo. |
| GERENTE | Todo lo del cajero, más: anular, carta, mesas, tasa, personal (meseros y cajeros), ventas, historial de turnos, corregir conteos, impresoras. |
| ADMIN | Todo, incluso crear gerentes y administradores. |

## Pedidos (todo el personal)
| Método | Ruta | Uso |
|---|---|---|
| GET | /pedidos?soloAbiertos=&turnoId= | Pedidos del turno (el mesero solo ve los suyos). Los web traen `entrega`: DELIVERY o RETIRO |
| GET | /pedidos/{id} | Detalle con líneas (cada una con `enviadoCocina`) y pagos |
| POST | /pedidos | `{tipo: MESA\|PARA_LLEVAR\|DELIVERY, mesaId?, cliente…, items:[{productoId, cantidad, notas}]}` |
| POST | /pedidos/{id}/items | Agregar líneas `[{productoId, cantidad}]`. Si el pedido ya está en cocina, sale una comanda ADICIONAL |
| DELETE | /pedidos/{id}/items/{detalleId} | Quitar línea. Si ya salió a cocina: solo caja o gerencia, y sale una comanda de CANCELADO |
| PATCH | /pedidos/{id}/estado | `{estado}` un paso: ABIERTO→EN_PREPARACION→LISTO→ENTREGADO. Al pasar a EN_PREPARACION sale la comanda. Un pedido WEB solo lo pasa la caja (aceptar) |
| POST | /pedidos/{id}/reimprimir | Copia de lo enviado a cocina (comanda REIMPRESIÓN) |
| POST | /pedidos/{id}/pagos | **Cajero, Gerente, Admin.** `{metodo, monto, referencia?}` (monto en la moneda del método) |
| POST | /pedidos/{id}/anular | `{motivo}` — solo Gerente/Admin y sin pagos. Si estaba en cocina, sale una comanda de ANULADO |
| GET | /pedidos/{id}/bitacora | **Cajero, Gerente, Admin.** Quién hizo qué y cuándo |
| GET | /pedidos/bitacora?turnoId=&soloAlertas= | **Cajero, Gerente, Admin.** Bitácora del turno (por defecto el abierto). Alertas: platos quitados después de ir a cocina, anulaciones y reimpresiones |

Un pedido pasa solo a **PAGADO** cuando está ENTREGADO y su saldo es 0.

## Comandas de cocina e impresoras

| Método | Ruta | Quién | Uso |
|---|---|---|---|
| GET | /comandas?pedidoId= | Personal | Comandas de un pedido (o las últimas 30): `estado` PENDIENTE, IMPRESA, FALLIDA o VENCIDA |
| GET | /comandas/estado | Personal | Impresoras activas, si están en línea, comandas pendientes |
| GET/POST | /impresoras | Gerente, Admin | Listar / registrar `{nombre, columnas}` → devuelve el token una sola vez y las rutas para configurar |
| DELETE | /impresoras/{id} | Gerente, Admin | Desactivar (su dirección deja de servir) |
| POST | /impresoras/prueba | Gerente, Admin | Manda una hoja de prueba |

Rutas que usa la impresora (sin sesión, con el token de la impresora en la dirección; token falso = 404):

| Método | Ruta | Uso |
|---|---|---|
| POST | /impresion/cloudprnt/{token} | Star CloudPRNT: la impresora pregunta; responde `{jobReady, mediaTypes:["text/plain"], jobToken}` |
| GET | /impresion/cloudprnt/{token}?token= | Descarga la hoja en texto plano |
| DELETE | /impresion/cloudprnt/{token}?code=&token= | Confirma: código 2xx = impresa; otro = falló (a los 3 fallos se deja de intentar) |
| GET | /impresion/puente/{token}/siguiente | Puente: 200 con la hoja y `X-Comanda-Id`, o 204 si no hay nada |
| POST | /impresion/puente/{token}/{id}?ok= | Puente: confirma |

Las comandas de más de 2 horas que no salieron no se imprimen (para que no salga todo lo viejo al
conectar una impresora). El texto va sin tildes para que cualquier térmica lo imprima bien.

## Gerencia (Gerente, Admin)
| Método | Ruta | Uso |
|---|---|---|
| POST/PUT/DELETE | /categorias, /productos, /mesas | Crear, editar, desactivar (DELETE = baja lógica) |
| PATCH | /productos/{id}/disponible | `{disponible}` agotado / disponible |
| POST | /productos/{id}/reactivar | Volver a mostrar un producto dado de baja |
| GET/POST | /tasas | Historial / registrar `{bsPorUsd, fuente}` |
| POST | /turnos/abrir | `{tipo: DIA\|NOCHE, fechaOperativa?}` |
| GET/POST | /usuarios | Listar / crear (Gerente: solo meseros) |
| PUT | /usuarios/{id} | `{nombre?, rol?, activo?}` |
| PUT | /usuarios/{id}/password | `{passwordNueva}` |

## Reglas clave
- **Tasa opcional:** se usa la última registrada. Sin ninguna, el pedido va solo en USD; cobrar en Bs exige que exista una tasa.
- **Turno obligatorio:** sin turno abierto no se toman pedidos (la web responde 503 "no está recibiendo pedidos").
- **Precios siempre de la base:** lo que mande el cliente como precio se ignora.
- **Pago:** no se acepta más que el saldo (margen $0.05 por redondeo); el vuelto no se registra. Pago móvil, Zelle y transferencia exigen referencia.
- **Cada pago guarda el turno en que entró el dinero** (para el corte del Paso 4).

## Paso 4 — Cortes de turno y dashboard

Abrir y cerrar turno y ver el corte: Cajero, Gerente y Admin. Historial de turnos, corrección del
conteo y dashboard: solo Gerente y Admin.
| Método | Ruta | Uso |
|---|---|---|
| POST | /turnos/abrir | Ahora acepta `fondoInicialUsd` y `fondoInicialBs` (efectivo para vuelto, opcional) |
| POST | /turnos/cerrar | Cierra el turno abierto. Cuerpo opcional `{conteo: {efectivoUsd, efectivoBs}, observaciones}` |
| PUT | /turnos/{id}/conteo | Registrar o corregir el conteo de un turno cerrado |
| GET | /turnos/{id}/corte | Corte del turno (si está abierto: vista previa en vivo) |
| GET | /turnos?desde=&hasta= | Historial de turnos (por defecto últimos 30 días) |
| GET | /dashboard/dia?fecha= | Día completo: totales, por turno, por método, por mesero |
| GET | /dashboard/rango?desde=&hasta= | Serie diaria de recaudo $ / Bs (por defecto 7 días, máx. 93) |

### Forma del corte (pensada para la pantalla)
```
{ turno, observaciones,
  totales:   { pedidos, anulados, pendientes, ventaUsd, recaudadoUsd, recaudadoBs,
               recaudadoTotalEquivUsd, ticketPromedioUsd },          ← se ve de entrada
  porMetodo: [ {metodo, moneda, cantidad, monto, equivalenteUsd} ],   ← siempre los 6 métodos
  porMesero: [ {mesero, pedidos, ventaUsd, cobradoUsd, cobradoBs, cobradoEquivUsd} ],
  conteoCaja: { fondoInicial*, cobradoEfectivo*, esperado*, contado*, diferencia*,
                cuadreUsd, cuadreBs: CUADRA | SOBRA | FALTA | SIN_CONTAR } ← desplegable cerrado }
```
- **Venta** = valor de los pedidos creados en el turno. **Recaudo** = dinero que entró en el turno (un pedido de día cobrado de noche cuenta en la noche).
- El conteo solo compara **efectivo** (fondo inicial + cobros en efectivo); pago móvil, punto, Zelle y transferencias van al banco.
- Cerrar no exige que no haya pedidos abiertos (local 24h); se informan como `pendientes`.

## Fotos de productos

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/imagenes` | GERENTE, ADMIN | Sube una foto (multipart, campo `archivo`). JPG, PNG o WebP, máximo 2 MB. Responde `{ "id": 7, "url": "/api/imagenes/7" }`. |
| GET | `/api/imagenes/{id}` | Público | Devuelve la foto. Se guarda en caché del navegador por un año. |

La `url` devuelta se guarda en `imagenUrl` del producto (`POST/PUT /api/productos`).
Las fotos de la carta inicial usan rutas relativas (`img/ocumare-gourmet.jpg`) servidas desde `static/img`.
El POS achica las fotos en el navegador (máx. 1000 px, JPEG 82 %) antes de subirlas.
Las fotos subidas viven en la tabla `imagenes` de la base, así que entran en los respaldos.

Errores: `400` si el archivo no es una foto o falta el campo, `413` si pasa del límite, `403` para meseros.

## Tasa automática del BCV

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/tasas/bcv` | GERENTE, ADMIN | Consulta ya la tasa oficial (DolarAPI) y la registra si cambió. Responde `{ "nueva": true/false, "tasa": {...} }`. `503` si el servicio no responde, `409` si el valor difiere más de 50 % de la tasa actual. |

Además, con `ocumare.tasa-bcv.activa=true` (variable `TASA_BCV_ACTIVA`, activa por defecto) el servidor la consulta cada hora.
Se guarda con fuente `BCV automática` y solo cuando el BCV publica un valor distinto al último que trajo,
así una tasa puesta a mano no se pisa hasta la siguiente publicación del BCV.

## Sesiones que se cierran solas

Desactivar a una persona, cambiarle el rol o restablecerle la contraseña cierra sus sesiones abiertas:
su siguiente petición responde `401` y el POS la devuelve a la pantalla de inicio.

## Límites por IP

| Qué | Por defecto | Variable para cambiarlo |
|---|---|---|
| `POST /auth/login` | 10 por minuto | `LIMITE_LOGIN` |
| `POST /web/pedidos` | 10 cada 10 minutos | `LIMITE_PEDIDOS_WEB` |
| `GET /web/pedidos/{clave}` | 30 por minuto | `LIMITE_CONSULTAS_WEB` |
| Cualquier otra ruta `/api` | 600 por minuto | `LIMITE_API` |

Además, un usuario queda bloqueado 5 minutos tras 5 claves malas **desde la misma IP**.
