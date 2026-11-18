package com.flipo.backend.ia;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Nível das perguntas geradas (docs/01-fluxos-de-usuario.md, seção 4b). No JSON vale
 * {@code "definicao"} ou {@code "aplicacao"} (docs/03-contrato-api.md).
 */
public enum NivelCartao {

	/** Definição simples (padrão). */
	@JsonProperty("definicao")
	DEFINICAO,

	/** Aplicação/raciocínio. */
	@JsonProperty("aplicacao")
	APLICACAO
}
