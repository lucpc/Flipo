package com.flipo.backend.controller;

import com.flipo.backend.dto.ErrorResponse;
import com.flipo.backend.service.CredenciaisInvalidasException;
import com.flipo.backend.service.EmailJaCadastradoException;
import com.flipo.backend.service.ExtracaoOcupadaException;
import com.flipo.backend.service.FalhaNaGeracaoException;
import com.flipo.backend.service.GeracaoIndisponivelException;
import com.flipo.backend.service.PdfInvalidoException;
import com.flipo.backend.service.PdfSemTextoException;
import com.flipo.backend.service.RecursoNaoEncontradoException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.stream.Collectors;

/**
 * Traduz exceções de negócio/validação em respostas HTTP previsíveis — nenhuma delas deixa vazar
 * stacktrace/mensagem de banco para o cliente (docs/04-arquitetura-tecnica.md, seção Segurança).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(EmailJaCadastradoException.class)
	public ResponseEntity<ErrorResponse> handleEmailJaCadastrado(EmailJaCadastradoException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
	}

	@ExceptionHandler(CredenciaisInvalidasException.class)
	public ResponseEntity<ErrorResponse> handleCredenciaisInvalidas(CredenciaisInvalidasException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
	}

	@ExceptionHandler(RecursoNaoEncontradoException.class)
	public ResponseEntity<ErrorResponse> handleRecursoNaoEncontrado(RecursoNaoEncontradoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
	}

	@ExceptionHandler(GeracaoIndisponivelException.class)
	public ResponseEntity<ErrorResponse> handleGeracaoIndisponivel(GeracaoIndisponivelException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(e.getMessage()));
	}

	@ExceptionHandler(FalhaNaGeracaoException.class)
	public ResponseEntity<ErrorResponse> handleFalhaNaGeracao(FalhaNaGeracaoException e) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(e.getMessage()));
	}

	@ExceptionHandler(PdfInvalidoException.class)
	public ResponseEntity<ErrorResponse> handlePdfInvalido(PdfInvalidoException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
	}

	@ExceptionHandler(PdfSemTextoException.class)
	public ResponseEntity<ErrorResponse> handlePdfSemTexto(PdfSemTextoException e) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new ErrorResponse(e.getMessage()));
	}

	@ExceptionHandler(ExtracaoOcupadaException.class)
	public ResponseEntity<ErrorResponse> handleExtracaoOcupada(ExtracaoOcupadaException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(e.getMessage()));
	}

	/**
	 * Upload acima de {@code spring.servlet.multipart.max-file-size}/{@code max-request-size}. Vem da
	 * leitura do multipart, antes de chegar ao controller.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleUploadGrandeDemais(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
				.body(new ErrorResponse("O arquivo passa do limite de 10 MB."));
	}

	/** Corpo multipart que não pôde ser lido (ex: malformado ou interrompido). */
	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ErrorResponse> handleMultipartInvalido(MultipartException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("Não foi possível ler o arquivo enviado."));
	}

	/**
	 * JSON malformado ou valor fora do esperado (ex: {@code nivel} que não é
	 * {@code definicao}/{@code aplicacao}). Mensagem genérica: a do Jackson cita classes internas.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleCorpoIlegivel(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("Corpo da requisição inválido."));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidacao(MethodArgumentNotValidException e) {
		String mensagem = e.getBindingResult().getFieldErrors().stream()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return ResponseEntity.badRequest().body(new ErrorResponse(mensagem));
	}
}
