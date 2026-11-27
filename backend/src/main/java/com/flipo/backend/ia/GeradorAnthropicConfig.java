package com.flipo.backend.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Registra o {@link GeradorAnthropic} só quando {@code flipo.ia.anthropic.api-key} (variável de
 * ambiente {@code ANTHROPIC_API_KEY}) tem valor. Sem chave, não há {@link GeradorDeCartoes} e o
 * {@code gerar-ia} responde 503.
 *
 * <p>Condição própria em vez de {@code @ConditionalOnProperty}, que considera presente uma
 * propriedade vazia (o padrão {@code ${ANTHROPIC_API_KEY:}} sempre a define), e em vez de
 * {@code @ConditionalOnExpression}, que embutiria a chave no texto de uma expressão SpEL: um erro de
 * parse a mostraria em log.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(GeradorAnthropicConfig.ChaveAnthropicConfigurada.class)
public class GeradorAnthropicConfig {

	static final String PROPRIEDADE_CHAVE = "flipo.ia.anthropic.api-key";

	/** O {@code close()} do gerador (que fecha o cliente HTTP) é chamado no desligamento do contexto. */
	@Bean
	GeradorAnthropic geradorAnthropic(
			@Value("${" + PROPRIEDADE_CHAVE + "}") String apiKey,
			@Value("${flipo.ia.anthropic.modelo}") String modelo) {
		return new GeradorAnthropic(GeradorAnthropic.cliente(apiKey.strip()), modelo);
	}

	static class ChaveAnthropicConfigurada implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return StringUtils.hasText(context.getEnvironment().getProperty(PROPRIEDADE_CHAVE));
		}
	}
}
