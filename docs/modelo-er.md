# Ocumare Food — Modelo entidad-relación (Paso 1)

```mermaid
erDiagram
    USUARIOS ||--o{ PEDIDOS : "atiende (mesero)"
    USUARIOS ||--o{ TURNOS : "abre / cierra"
    USUARIOS ||--o{ TASAS_CAMBIO : registra
    USUARIOS ||--o{ PAGOS : registra
    CATEGORIAS ||--o{ PRODUCTOS : agrupa
    MESAS ||--o{ PEDIDOS : "se sirve en"
    TURNOS ||--o{ PEDIDOS : contiene
    PEDIDOS ||--|{ DETALLES_PEDIDO : tiene
    PEDIDOS ||--o{ PAGOS : "se paga con"
    PRODUCTOS ||--o{ DETALLES_PEDIDO : "se vende en"

    USUARIOS { bigint id PK
        varchar username UK
        varchar password_hash
        enum rol "MESERO | GERENTE | ADMIN"
        bool activo }
    CATEGORIAS { bigint id PK
        varchar nombre UK
        int orden }
    PRODUCTOS { bigint id PK
        varchar nombre
        decimal precio_usd
        bigint categoria_id FK
        bool disponible "agotado hoy"
        bool activo "baja lógica" }
    MESAS { bigint id PK
        int numero UK
        int capacidad }
    TASAS_CAMBIO { bigint id PK
        decimal bs_por_usd
        datetime vigente_desde
        varchar fuente }
    TURNOS { bigint id PK
        enum tipo "DIA | NOCHE"
        date fecha_operativa
        enum estado
        decimal recaudado_usd "congelado al cierre"
        decimal recaudado_bs "congelado al cierre" }
    PEDIDOS { bigint id PK
        int numero_diario "UK con fecha_operativa"
        enum tipo "MESA | PARA_LLEVAR | DELIVERY | WEB"
        enum estado
        bigint turno_id FK
        bigint mesa_id FK "solo MESA"
        bigint mesero_id FK "vacío en WEB"
        varchar clave_externa UK "idempotencia web"
        decimal tasa_bs_por_usd "copia de la tasa"
        decimal total_usd
        decimal total_bs }
    DETALLES_PEDIDO { bigint id PK
        bigint pedido_id FK
        bigint producto_id FK
        varchar nombre_producto "copia"
        int cantidad
        decimal precio_unitario_usd "copia" }
    PAGOS { bigint id PK
        bigint pedido_id FK
        enum metodo
        enum moneda "USD | BS"
        decimal monto "en su moneda"
        decimal equivalente_usd }
```

## Reglas de diseño

1. **Dinero con `BigDecimal`, nunca `double`.** Montos con 2 decimales, tasa con 4 (`util/Dinero`).
2. **El precio base está en USD.** El monto en Bs se calcula con la tasa y queda guardado en el pedido (`tasa_bs_por_usd`); si la tasa cambia, los pedidos viejos no se alteran.
3. **Los detalles copian nombre y precio.** Cambiar el menú afecta solo a pedidos futuros.
4. **Pago mixto.** Un pedido tiene N pagos; cada uno en su moneda real. El corte suma `$` y `Bs` por separado (lo que hay en caja) y el `equivalente_usd` dice si el pedido ya quedó saldado.
5. **Turno día/noche** con `fecha_operativa`: la noche que cruza la medianoche cuenta para el día en que empezó. Un turno por tipo por día.
6. **Baja lógica** en usuarios y productos, para no romper el historial.
7. **Pedidos web**: tipo `WEB`, sin mesero, con `clave_externa` única para que un reintento no duplique.
