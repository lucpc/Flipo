package com.flipo.backend.dto;

import java.util.UUID;

/** Resposta de {@code POST /api/auth/registro} — nunca inclui a senha/hash (docs/03-contrato-api.md). */
public record RegistroResponse(UUID id, String nome, String email) {
}
