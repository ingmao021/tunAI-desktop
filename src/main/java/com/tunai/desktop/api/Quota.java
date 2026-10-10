package com.tunai.desktop.api;

import java.time.Instant;

/** Cuota diaria de generaciones ({@code GET /api/v1/quota} o headers {@code X-Quota-*}). */
public record Quota(int limit, int remaining, Instant resetsAt) {

    public int used() {
        return Math.max(0, limit - remaining);
    }

    public boolean exhausted() {
        return remaining <= 0;
    }
}
