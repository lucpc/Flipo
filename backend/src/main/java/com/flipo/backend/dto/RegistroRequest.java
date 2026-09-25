package com.flipo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/auth/registro} (docs/03-contrato-api.md). */
public record RegistroRequest(

		@NotBlank
		@Size(max = 255)
		String nome,

		@NotBlank
		@Email
		@Size(max = 255)
		String email,

		// Máximo 72: além disso o BCrypt trunca silenciosamente o valor.
		@NotBlank
		@Size(min = 8, max = 72)
		String senha) {
}
