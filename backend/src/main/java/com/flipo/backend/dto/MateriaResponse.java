package com.flipo.backend.dto;

import java.util.UUID;

/**
 * Resposta de {@code GET /api/materias} e {@code POST /api/materias} (docs/03-contrato-api.md).
 *
 * <p>O contrato documentado também prevê {@code totalAtivos}/{@code totalArquivados} em
 * {@code GET /api/materias} — escopo de uma issue separada (contagem de cartões), ainda não
 * implementada. Não adicionar esses campos aqui sem alinhar antes.
 */
public record MateriaResponse(UUID id, String nome) {
}
