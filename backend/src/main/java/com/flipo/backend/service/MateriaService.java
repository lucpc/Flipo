package com.flipo.backend.service;

import com.flipo.backend.model.Materia;
import com.flipo.backend.repository.MateriaRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Busca de {@link Materia} sempre escopada ao usuário autenticado. {@code usuarioId} é sempre
 * recebido explicitamente do chamador (extraído do JWT pelo controller) — este serviço nunca
 * confia num id de path/body para decidir de quem é o dado (docs/04-arquitetura-tecnica.md,
 * seção Segurança).
 */
@Service
public class MateriaService {

	private final MateriaRepository materiaRepository;

	public MateriaService(MateriaRepository materiaRepository) {
		this.materiaRepository = materiaRepository;
	}

	/**
	 * Devolve a matéria de id {@code materiaId} pertencente a {@code usuarioId}. Lança
	 * {@link RecursoNaoEncontradoException} tanto se o id não existir quanto se existir mas
	 * pertencer a outro usuário — os dois casos são indistinguíveis para quem chama.
	 */
	public Materia buscarPorIdEUsuario(UUID materiaId, UUID usuarioId) {
		return materiaRepository.findByIdAndUsuarioId(materiaId, usuarioId)
				.orElseThrow(RecursoNaoEncontradoException::new);
	}
}
