package com.flipo.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code POST /api/materias/{materiaId}/cartoes} e {@code PATCH /api/cartoes/{id}}
 * (docs/03-contrato-api.md). Sem {@code @Size} — {@code pergunta}/{@code resposta} são colunas
 * {@code text}, não {@code varchar(255)}.
 */
public record CartaoRequest(

		@NotBlank
		String pergunta,

		@NotBlank
		String resposta) {
}
