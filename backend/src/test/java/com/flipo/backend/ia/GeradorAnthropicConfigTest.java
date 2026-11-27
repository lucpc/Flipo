package com.flipo.backend.ia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quando o {@link GeradorAnthropic} entra no contexto: só com {@code flipo.ia.anthropic.api-key}
 * preenchida. Contexto mínimo, sem banco; montar o cliente não chama a API.
 */
class GeradorAnthropicConfigTest {

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withUserConfiguration(GeradorAnthropicConfig.class)
			.withPropertyValues("flipo.ia.anthropic.modelo=claude-haiku-5-5");

	@Test
	void semAPropriedadeNaoHaGerador() {
		contexto.run(ctx -> assertThat(ctx).doesNotHaveBean(GeradorDeCartoes.class));
	}

	@Test
	void comAPropriedadeVaziaNaoHaGerador() {
		// O caso padrão: application.properties define a chave como ${ANTHROPIC_API_KEY:}.
		contexto.withPropertyValues("flipo.ia.anthropic.api-key=")
				.run(ctx -> assertThat(ctx).doesNotHaveBean(GeradorDeCartoes.class));
	}

	@Test
	void comAPropriedadeEmBrancoNaoHaGerador() {
		contexto.withPropertyValues("flipo.ia.anthropic.api-key=   ")
				.run(ctx -> assertThat(ctx).doesNotHaveBean(GeradorDeCartoes.class));
	}

	@Test
	void comAPropriedadePreenchidaOGeradorEOAnthropic() {
		contexto.withPropertyValues("flipo.ia.anthropic.api-key=sk-ant-teste")
				.run(ctx -> assertThat(ctx).getBean(GeradorDeCartoes.class).isInstanceOf(GeradorAnthropic.class));
	}
}
