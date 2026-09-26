package com.flipo.backend.service;

import com.flipo.backend.model.Cartao;
import com.flipo.backend.model.Materia;
import com.flipo.backend.repository.CartaoRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Busca/escrita de {@link Cartao} sempre escopada ao usuário autenticado, via o dono da
 * {@link Materia} do cartão. {@code usuarioId} é sempre recebido explicitamente do chamador
 * (extraído do JWT pelo controller) — este serviço nunca confia num id de path/body para decidir
 * de quem é o dado (docs/04-arquitetura-tecnica.md, seção Segurança).
 */
@Service
public class CartaoService {

	private final CartaoRepository cartaoRepository;
	private final MateriaService materiaService;

	public CartaoService(CartaoRepository cartaoRepository, MateriaService materiaService) {
		this.cartaoRepository = cartaoRepository;
		this.materiaService = materiaService;
	}

	/**
	 * Devolve o cartão de id {@code cartaoId} cuja matéria pertence a {@code usuarioId}. Lança
	 * {@link RecursoNaoEncontradoException} tanto se o id não existir quanto se existir mas
	 * pertencer a outro usuário — os dois casos são indistinguíveis para quem chama.
	 */
	public Cartao buscarPorIdEUsuario(UUID cartaoId, UUID usuarioId) {
		return cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId)
				.orElseThrow(RecursoNaoEncontradoException::new);
	}

	/**
	 * Lista os cartões de {@code materiaId} pertencentes a {@code usuarioId}, filtrados por
	 * {@code arquivado} já na query (sem filtrar em memória). Reusa
	 * {@link MateriaService#buscarPorIdEUsuario} pra validar a posse da matéria antes de listar —
	 * {@code materiaId} do path nunca é confiável sozinho.
	 */
	public List<Cartao> listarPorMateriaEUsuario(UUID materiaId, UUID usuarioId, boolean arquivado) {
		materiaService.buscarPorIdEUsuario(materiaId, usuarioId);
		return cartaoRepository.findByMateriaIdAndMateria_Usuario_IdAndArquivado(materiaId, usuarioId, arquivado);
	}

	/**
	 * Cria um cartão manual em {@code materiaId}, pertencente a {@code usuarioId}. Valida a posse
	 * da matéria via {@link MateriaService} antes de criar; {@code origem} é sempre
	 * {@link Cartao#ORIGEM_MANUAL} — geração por IA (Épico 5) é um fluxo à parte que nunca passa
	 * por aqui.
	 */
	public Cartao criar(UUID materiaId, UUID usuarioId, String pergunta, String resposta) {
		Materia materia = materiaService.buscarPorIdEUsuario(materiaId, usuarioId);
		return cartaoRepository.save(new Cartao(materia, pergunta, resposta, Cartao.ORIGEM_MANUAL));
	}

	/**
	 * Atualiza pergunta/resposta do cartão de id {@code cartaoId} pertencente a
	 * {@code usuarioId}. Edição de conteúdo apenas — nunca toca {@code arquivado}.
	 */
	public Cartao editar(UUID cartaoId, UUID usuarioId, String pergunta, String resposta) {
		Cartao cartao = buscarPorIdEUsuario(cartaoId, usuarioId);
		cartao.setPergunta(pergunta);
		cartao.setResposta(resposta);
		return cartaoRepository.save(cartao);
	}

	/**
	 * Marca o cartão de id {@code cartaoId} pertencente a {@code usuarioId} como arquivado. Ação
	 * deliberada e reversível (ver {@link #desarquivar}) — nunca disparada como efeito colateral
	 * de outra operação.
	 */
	public Cartao arquivar(UUID cartaoId, UUID usuarioId) {
		Cartao cartao = buscarPorIdEUsuario(cartaoId, usuarioId);
		cartao.setArquivado(true);
		return cartaoRepository.save(cartao);
	}

	/** Reverte {@link #arquivar} — marca o cartão como não arquivado. */
	public Cartao desarquivar(UUID cartaoId, UUID usuarioId) {
		Cartao cartao = buscarPorIdEUsuario(cartaoId, usuarioId);
		cartao.setArquivado(false);
		return cartaoRepository.save(cartao);
	}

	/**
	 * Remove o cartão de id {@code cartaoId} pertencente a {@code usuarioId}. Reusa
	 * {@link #buscarPorIdEUsuario} pra garantir posse antes de deletar.
	 */
	public void remover(UUID cartaoId, UUID usuarioId) {
		Cartao cartao = buscarPorIdEUsuario(cartaoId, usuarioId);
		cartaoRepository.delete(cartao);
	}
}
