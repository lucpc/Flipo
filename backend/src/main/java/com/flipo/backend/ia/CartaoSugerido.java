package com.flipo.backend.ia;

/**
 * Um par pergunta/resposta ainda não salvo: o que o {@link GeradorDeCartoes} devolve e o que o
 * {@code gerar-ia} manda para a tela de Revisão (docs/03-contrato-api.md).
 */
public record CartaoSugerido(String pergunta, String resposta) {
}
