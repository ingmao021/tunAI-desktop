package com.tunai.desktop.api;

/** Respuesta exitosa de {@code POST /api/v1/generations}. */
public record GenerationResult(byte[] glb, Quota quota) {
}
