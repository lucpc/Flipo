package com.flipo.backend.dto;

import com.flipo.backend.ia.NivelCartao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/materias/{materiaId}/cartoes/gerar-ia} (docs/03-contrato-api.md).
 * {@code quantidade} e {@code nivel} são opcionais: ausentes (ou {@code null}), valem
 * {@value #QUANTIDADE_PADRAO} e {@link NivelCartao#DEFINICAO}. Os limites estão registrados no
 * contrato.
 */
public record GerarIaRequest(

		@NotBlank
		@Size(max = TEXTO_TAMANHO_MAXIMO)
		String texto,

		@Min(1)
		@Max(QUANTIDADE_MAXIMA)
		Integer quantidade,

		NivelCartao nivel) {

	public static final int QUANTIDADE_PADRAO = 10;
	public static final int QUANTIDADE_MAXIMA = 20;
	public static final int TEXTO_TAMANHO_MAXIMO = 20_000;

	public GerarIaRequest {
		if (quantidade == null) {
			quantidade = QUANTIDADE_PADRAO;
		}
		if (nivel == null) {
			nivel = NivelCartao.DEFINICAO;
		}
	}
}
