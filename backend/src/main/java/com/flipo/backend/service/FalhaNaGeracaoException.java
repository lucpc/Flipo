package com.flipo.backend.service;

/**
 * Lançada pelo {@code gerar-ia} quando o {@code GeradorDeCartoes} falha ou devolve uma saída
 * inutilizável. A mensagem é sempre a mesma, genérica: nunca carrega o erro original do provedor,
 * que pode ter detalhes internos. Vira 502 no {@code GlobalExceptionHandler}.
 */
public class FalhaNaGeracaoException extends RuntimeException {

	public FalhaNaGeracaoException() {
		super("Não foi possível gerar sugestões agora. Tente novamente.");
	}
}
