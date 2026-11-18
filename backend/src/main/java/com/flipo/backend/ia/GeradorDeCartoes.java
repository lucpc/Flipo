package com.flipo.backend.ia;

import java.util.List;

/**
 * Abstração de geração de cartões por IA (docs/04-arquitetura-tecnica.md, "Estratégia de IA"). O
 * provedor (Anthropic, Groq, Ollama...) fica atrás desta interface: nenhum controller ou serviço
 * chama um SDK de provedor diretamente.
 *
 * <p>Para ligar um provedor, basta registrar <b>um</b> bean que implemente esta interface (ex:
 * {@code @Component} com {@code @Profile} ou {@code @ConditionalOnProperty}). Sem nenhum bean, o
 * {@code gerar-ia} responde 503 ("não configurada"), em vez de cair num gerador falso.
 *
 * <p>Contrato para implementações:
 * <ul>
 * <li>só gera — nunca grava no banco (quem persiste é o {@code lote}, depois da revisão);</li>
 * <li>em falha, lança uma {@link RuntimeException} qualquer: o chamador a traduz numa resposta
 * genérica e nunca repassa a mensagem ao cliente. Mesmo assim, a mensagem não deve conter chave
 * de API nem o texto do usuário.</li>
 * </ul>
 */
public interface GeradorDeCartoes {

	List<CartaoSugerido> gerar(String texto, int quantidade, NivelCartao nivel);
}
