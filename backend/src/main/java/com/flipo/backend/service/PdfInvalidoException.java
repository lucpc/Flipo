package com.flipo.backend.service;

/**
 * Lançada pelo {@code POST /api/pdf/extrair-texto} quando o arquivo não chega, está vazio, não é um
 * PDF, está corrompido ou tem senha. A mensagem é escrita aqui, para o usuário, e nunca carrega o
 * erro do PDFBox. Vira 400 no {@code GlobalExceptionHandler}.
 */
public class PdfInvalidoException extends RuntimeException {

	public PdfInvalidoException(String mensagem) {
		super(mensagem);
	}
}
