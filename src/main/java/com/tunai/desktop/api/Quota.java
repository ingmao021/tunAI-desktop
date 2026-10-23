package com.tunai.desktop.api;

import java.time.Instant;

/**
 * Cuota del backend ({@code GET /api/v1/quota} o headers {@code X-Quota-*} y {@code X-Credits-*}).
 * {@code limit} y {@code remaining} cuentan modelos: el límite más restrictivo entre los créditos y el límite diario
 * de la red. {@code credits} es el saldo de la instalación y {@code creditsPerModel} lo que cuesta cada modelo; ambos
 * nulos si el backend no los informa.
 */
public record Quota(int limit, int remaining, Instant resetsAt, Integer credits, Integer creditsPerModel) {

    public Quota(int limit, int remaining, Instant resetsAt) {
        this(limit, remaining, resetsAt, null, null);
    }

    public Quota(int limit, int remaining, Instant resetsAt, Integer credits) {
        this(limit, remaining, resetsAt, credits, null);
    }

    public int used() {
        return Math.max(0, limit - remaining);
    }

    public boolean exhausted() {
        return remaining <= 0;
    }
}
