package com.flipo.backend.controller;

import com.flipo.backend.service.ExtracaoDeTextoPdfService;
import com.flipo.backend.service.TextoExtraido;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code POST /api/pdf/extrair-texto} (docs/03-contrato-api.md). Exige JWT como o resto da API
 * ({@code SecurityConfig}), mas não lê nem grava dado de usuário: só devolve o texto do PDF.
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfController {

	private final ExtracaoDeTextoPdfService extracaoDeTextoPdfService;

	public PdfController(ExtracaoDeTextoPdfService extracaoDeTextoPdfService) {
		this.extracaoDeTextoPdfService = extracaoDeTextoPdfService;
	}

	/**
	 * {@code required = false} para que a falta do campo, inclusive numa requisição que nem é
	 * multipart, chegue ao serviço e responda o mesmo 400 com {@code { "mensagem" }}.
	 */
	@PostMapping("/extrair-texto")
	public TextoExtraido extrairTexto(@RequestParam(name = "arquivo", required = false) MultipartFile arquivo) {
		return extracaoDeTextoPdfService.extrair(arquivo);
	}
}
