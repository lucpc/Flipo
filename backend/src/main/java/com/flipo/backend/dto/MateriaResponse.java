package com.flipo.backend.dto;

import java.util.UUID;

/**
 * Resposta de {@code GET /api/materias} e {@code POST /api/materias} (docs/03-contrato-api.md).
 *
 * <p>{@code totalAtivos}/{@code totalArquivados} contam os cartões da matéria com
 * {@code arquivado=false}/{@code true}, respectivamente. {@code POST /api/materias} devolve uma
 * matéria recém-criada (sempre 0/0), então o controller preenche esses campos com zero nesse
 * caso em vez de consultar o repositório de cartões.
 */
public record MateriaResponse(UUID id, String nome, long totalAtivos, long totalArquivados) {
}
