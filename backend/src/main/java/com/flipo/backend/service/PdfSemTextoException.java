package com.flipo.backend.service;

/**
 * Lançada pelo {@code POST /api/pdf/extrair-texto} quando o PDF abre, mas não tem texto selecionável
 * (ex: digitalizado, só com imagens). Vira 422 no {@code GlobalExceptionHandler}.
 */
public class PdfSemTextoException extends RuntimeException {

	public PdfSemTextoException() {
		super("Este PDF não tem texto selecionável (pode ser digitalizado). Cole o texto no campo.");
	}
}
