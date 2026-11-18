package com.flipo.backend.ia;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Gerador falso, só para testes: registra as chamadas e devolve o que o teste mandar. Sem
 * {@code @Component}, para não entrar por varredura em todo contexto de teste: quem precisa dele o
 * registra com {@code @Import(GeradorDeCartoesFake.class)}. Por padrão devolve {@code quantidade}
 * sugestões numeradas.
 */
public class GeradorDeCartoesFake implements GeradorDeCartoes {

	public record Chamada(String texto, int quantidade, NivelCartao nivel) {
	}

	private final List<Chamada> chamadas = new ArrayList<>();
	private Supplier<List<CartaoSugerido>> comportamento;

	@Override
	public List<CartaoSugerido> gerar(String texto, int quantidade, NivelCartao nivel) {
		chamadas.add(new Chamada(texto, quantidade, nivel));
		if (comportamento != null) {
			return comportamento.get();
		}
		return IntStream.rangeClosed(1, quantidade)
				.mapToObj(i -> new CartaoSugerido("Pergunta " + i, "Resposta " + i))
				.toList();
	}

	/** As próximas chamadas devolvem {@code sugestoes} (pode conter {@code null}, para simular saída malformada). */
	public void devolver(List<CartaoSugerido> sugestoes) {
		this.comportamento = () -> sugestoes;
	}

	/** As próximas chamadas lançam {@code erro}. */
	public void falharCom(RuntimeException erro) {
		this.comportamento = () -> {
			throw erro;
		};
	}

	public List<Chamada> chamadas() {
		return List.copyOf(chamadas);
	}

	/** Volta ao comportamento padrão e esquece as chamadas (o bean é compartilhado entre testes). */
	public void reiniciar() {
		chamadas.clear();
		comportamento = null;
	}
}
