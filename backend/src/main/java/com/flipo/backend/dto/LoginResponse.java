package com.flipo.backend.dto;

/** Resposta de {@code POST /api/auth/login} (docs/03-contrato-api.md). */
public record LoginResponse(String token) {
}
