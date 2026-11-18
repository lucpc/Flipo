package com.flipo.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo de {@code POST /api/materias/{materiaId}/cartoes/lote} (docs/03-contrato-api.md): os
 * cartões que o usuário aceitou ou editou na Revisão. Cada um passa pela mesma validação do
 * {@code POST .../cartoes} ({@link CartaoRequest}). Sem campo {@code origem}: todo cartão do lote
 * é gravado como {@code "ia"} (um {@code origem} enviado pelo cliente é ignorado). No máximo
 * {@value GerarIaRequest#QUANTIDADE_MAXIMA} cartões, o máximo que um {@code gerar-ia} sugere.
 */
public record LoteRequest(

		@NotEmpty
		@Size(max = GerarIaRequest.QUANTIDADE_MAXIMA)
		List<@NotNull @Valid CartaoRequest> cartoes) {
}
