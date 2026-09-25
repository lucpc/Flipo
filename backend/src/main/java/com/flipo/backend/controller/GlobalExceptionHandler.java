package com.flipo.backend.controller;

import com.flipo.backend.dto.ErrorResponse;
import com.flipo.backend.service.CredenciaisInvalidasException;
import com.flipo.backend.service.EmailJaCadastradoException;
import com.flipo.backend.service.RecursoNaoEncontradoException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidacao(MethodArgumentNotValidException e) {
		String mensagem = e.getBindingResult().getFieldErrors().stream()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return ResponseEntity.badRequest().body(new ErrorResponse(mensagem));
	}
}
