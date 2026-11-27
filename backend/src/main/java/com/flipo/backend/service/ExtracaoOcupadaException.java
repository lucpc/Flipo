package com.flipo.backend.service;

/**
 * Lançada pelo {@code POST /api/pdf/extrair-texto} quando todas as vagas de extração estão ocupadas
 * e nenhuma abriu a tempo. Vira 503 no {@code GlobalExceptionHandler}.
 */
public class ExtracaoOcupadaException extends RuntimeException {

	public ExtracaoOcupadaException() {
		super("O servidor está ocupado lendo outros PDFs. Tente de novo em instantes.");
	}
}
