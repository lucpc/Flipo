package com.flipo.backend.service;

import com.flipo.backend.ia.CartaoSugerido;
import com.flipo.backend.ia.GeradorDeCartoes;
import com.flipo.backend.ia.NivelCartao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * {@code gerar-ia}: pede sugestões ao {@link GeradorDeCartoes} e as devolve, sem gravar nada.
 * Este serviço de propósito não tem acesso a {@code CartaoRepository}: a única escrita do fluxo de
 * IA é o {@code lote} ({@link CartaoService#criarLote}), depois da revisão do usuário
 * (docs/03-contrato-api.md). Também não é {@code @Transactional}: a checagem de posse usa e devolve
 * a conexão do banco antes da chamada ao provedor. Isso depende de
 * {@code spring.jpa.open-in-view=false} ({@code application.properties}); com o open-in-view ligado,
 * a conexão ficaria presa até o fim da requisição.
 */
@Service
public class GeracaoDeCartoesService {

	private static final Logger log = LoggerFactory.getLogger(GeracaoDeCartoesService.class);

	private final MateriaService materiaService;
	private final ObjectProvider<GeradorDeCartoes> geradores;

	/**
	 * Recebe o gerador por {@link ObjectProvider} porque ele é opcional: até existir um provedor
	 * configurado (#15), não há bean, e o {@code gerar-ia} responde 503.
	 */
	public GeracaoDeCartoesService(MateriaService materiaService, ObjectProvider<GeradorDeCartoes> geradores) {
		this.materiaService = materiaService;
		this.geradores = geradores;
	}

	/**
	 * Gera até {@code quantidade} sugestões a partir de {@code texto}, para a matéria
	 * {@code materiaId} de {@code usuarioId}. A posse da matéria é checada antes de tudo (404 para
	 * matéria de outro usuário, mesmo sem gerador configurado).
	 *
	 * @throws RecursoNaoEncontradoException matéria inexistente ou de outro usuário
	 * @throws GeracaoIndisponivelException nenhum {@link GeradorDeCartoes} configurado
	 * @throws FalhaNaGeracaoException o gerador falhou ou devolveu saída inutilizável
	 */
	public List<CartaoSugerido> gerar(
			UUID materiaId, UUID usuarioId, String texto, int quantidade, NivelCartao nivel) {
		materiaService.buscarPorIdEUsuario(materiaId, usuarioId);

		GeradorDeCartoes gerador = geradores.getIfAvailable();
		if (gerador == null) {
			throw new GeracaoIndisponivelException();
		}

		List<CartaoSugerido> sugestoes;
		try {
			sugestoes = gerador.gerar(texto, quantidade, nivel);
		} catch (RuntimeException e) {
			// Só o tipo da exceção: a mensagem de um provedor pode trazer o texto do usuário ou,
			// com BYOK, detalhes da requisição.
			log.warn("Gerador de cartões falhou: {}", e.getClass().getName());
			throw new FalhaNaGeracaoException();
		}
		return validarSaida(sugestoes, quantidade);
	}

	/**
	 * Corta o excedente se o gerador devolver mais do que o pedido e só então rejeita saída que a
	 * Revisão não consegue mostrar (lista vazia ou nula, item nulo, pergunta ou resposta em branco):
	 * um item ruim que seria descartado no corte não derruba a geração.
	 */
	private static List<CartaoSugerido> validarSaida(List<CartaoSugerido> sugestoes, int quantidade) {
		if (sugestoes == null || sugestoes.isEmpty()) {
			log.warn("Gerador de cartões devolveu uma lista vazia");
			throw new FalhaNaGeracaoException();
		}
		List<CartaoSugerido> pedidas = sugestoes.subList(0, Math.min(quantidade, sugestoes.size()));
		for (CartaoSugerido sugestao : pedidas) {
			if (sugestao == null || estaEmBranco(sugestao.pergunta()) || estaEmBranco(sugestao.resposta())) {
				log.warn("Gerador de cartões devolveu uma sugestão sem pergunta ou resposta");
				throw new FalhaNaGeracaoException();
			}
		}
		return List.copyOf(pedidas);
	}

	private static boolean estaEmBranco(String valor) {
		return valor == null || valor.isBlank();
	}
}
