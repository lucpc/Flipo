package com.flipo.backend.service;

/**
 * Lançada quando um {@code Materia}/{@code Cartao} buscado por id não existe, ou existe mas
 * pertence a outro usuário. Os dois casos são tratados de forma idêntica (mesma exceção, mesma
 * mensagem genérica) para não revelar a um usuário se um id pertence a outra pessoa — um JWT
 * válido do usuário A nunca deve conseguir diferenciar "id inexistente" de "id de B" adivinhando
 * ids (docs/04-arquitetura-tecnica.md, seção Segurança).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

	public RecursoNaoEncontradoException() {
		super("Recurso não encontrado.");
	}
}
