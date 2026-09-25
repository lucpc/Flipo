package com.flipo.backend.dto;

/** Corpo de erro genérico devolvido pelo {@code GlobalExceptionHandler}. */
public record ErrorResponse(String mensagem) {
}
