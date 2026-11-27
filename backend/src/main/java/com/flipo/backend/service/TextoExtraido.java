package com.flipo.backend.service;

/**
 * Resposta do {@code POST /api/pdf/extrair-texto} (docs/03-contrato-api.md): o texto normalizado,
 * o número de páginas do PDF e se o texto foi cortado no limite.
 */
public record TextoExtraido(String texto, int paginas, boolean truncado) {
}
