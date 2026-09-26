package com.flipo.backend.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta dos endpoints de {@code /api/cartoes} (docs/03-contrato-api.md). Mesmo DTO em todos
 * eles, incluindo {@code arquivar}/{@code desarquivar} (que a doc lista com um shape reduzido
 * {@code {id, arquivado}}) — reusar um único formato de cartão é mais simples do que manter um
 * schema mínimo por endpoint, mesmo critério já adotado em {@code MateriaResponse}.
 *
 * <p>{@code ultimaRevisao} é só informativo (setado pela sessão de estudo, Épico 4) — nenhum
 * endpoint deste épico escreve nele.
 */
public record CartaoResponse(
		UUID id, String pergunta, String resposta, String origem, boolean arquivado, Instant ultimaRevisao) {
}
