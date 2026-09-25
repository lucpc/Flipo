package com.flipo.backend.service;

import com.flipo.backend.model.Cartao;
import com.flipo.backend.repository.CartaoRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Busca de {@link Cartao} sempre escopada ao usuário autenticado, via o dono da
 * {@link com.flipo.backend.model.Materia} do cartão. {@code usuarioId} é sempre recebido
 * explicitamente do chamador (extraído do JWT pelo controller) — este serviço nunca confia num
 * id de path/body para decidir de quem é o dado (docs/04-arquitetura-tecnica.md, seção
 * Segurança).
 */
@Service
public class CartaoService {

	private final CartaoRepository cartaoRepository;

	public CartaoService(CartaoRepository cartaoRepository) {
		this.cartaoRepository = cartaoRepository;
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
}
