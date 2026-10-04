package com.project.ocumare_food.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de redondeo en un solo sitio. Nunca se usa double para dinero.
 * Los montos se guardan con 2 decimales y la tasa con 4.
 */
public final class Dinero {

    public static final int DECIMALES_MONTO = 2;
    public static final int DECIMALES_TASA = 4;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private Dinero() {
    }

    public static BigDecimal monto(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO.setScale(DECIMALES_MONTO)
                             : valor.setScale(DECIMALES_MONTO, REDONDEO);
    }

    /** Dólares → bolívares con la tasa (Bs por cada USD). */
    public static BigDecimal usdABs(BigDecimal usd, BigDecimal tasaBsPorUsd) {
        return monto(usd.multiply(tasaBsPorUsd));
    }

    /** Bolívares → dólares con la tasa (Bs por cada USD). */
    public static BigDecimal bsAUsd(BigDecimal bs, BigDecimal tasaBsPorUsd) {
        if (tasaBsPorUsd == null || tasaBsPorUsd.signum() <= 0) {
            throw new IllegalArgumentException("La tasa de cambio debe ser mayor que cero");
        }
        return bs.divide(tasaBsPorUsd, DECIMALES_MONTO, REDONDEO);
    }
}
