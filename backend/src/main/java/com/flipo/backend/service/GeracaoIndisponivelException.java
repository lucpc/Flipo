package com.flipo.backend.service;

/**
 * Lançada pelo {@code gerar-ia} quando o servidor não tem nenhum {@code GeradorDeCartoes}
 * configurado. Vira 503 no {@code GlobalExceptionHandler}.
 */
public class GeracaoIndisponivelException extends RuntimeException {

	public GeracaoIndisponivelException() {
		super("A geração de cartões por IA não está configurada neste servidor.");
	}
}
