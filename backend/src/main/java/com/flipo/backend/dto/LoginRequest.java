package com.flipo.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /api/auth/login} (docs/03-contrato-api.md). */
public record LoginRequest(@NotBlank String email, @NotBlank String senha) {
}
