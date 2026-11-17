package com.flipo.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/materias} e {@code PATCH /api/materias/{id}} (docs/03-contrato-api.md). */
public record MateriaRequest(

		@NotBlank
		@Size(max = 255)
		String nome) {
}
