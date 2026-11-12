package com.tunai.desktop.api;

import java.time.Instant;

/** Sesión que devuelve {@code POST /api/v1/auth/login}: un token y cuándo caduca. */
public record AuthSession(String username, String token, Instant expiresAt) {
}
