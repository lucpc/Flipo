package com.flipo.backend.service;

import com.flipo.backend.model.Materia;
import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.MateriaRepository;
import com.flipo.backend.repository.UsuarioRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Busca/escrita de {@link Materia} sempre escopada ao usuário autenticado. {@code usuarioId} é
 * sempre recebido explicitamente do chamador (extraído do JWT pelo controller) — este serviço
 * nunca confia num id de path/body para decidir de quem é o dado (docs/04-arquitetura-tecnica.md,
 * seção Segurança).
 */
@Service
public class MateriaService {

	private final MateriaRepository materiaRepository;
	private final UsuarioRepository usuarioRepository;

	public MateriaService(MateriaRepository materiaRepository, UsuarioRepository usuarioRepository) {
		this.materiaRepository = materiaRepository;
		this.usuarioRepository = usuarioRepository;
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

	/** Lista todas as matérias de {@code usuarioId}. */
	public List<Materia> listarPorUsuario(UUID usuarioId) {
		return materiaRepository.findByUsuarioId(usuarioId);
	}

	/**
	 * Cria uma matéria para {@code usuarioId}. O id vem sempre do JWT (nunca de path/body); o
	 * filtro de autenticação, mais abaixo na cadeia, já garantiu que o usuário existe antes de
	 * autenticar a requisição, então usa-se uma referência (sem SELECT extra) para montar a
	 * associação.
	 */
	public Materia criar(UUID usuarioId, String nome) {
		Usuario usuario = usuarioRepository.getReferenceById(usuarioId);
		return materiaRepository.save(new Materia(usuario, nome));
	}

	/**
	 * Remove a matéria de id {@code materiaId} pertencente a {@code usuarioId}. Reusa
	 * {@link #buscarPorIdEUsuario} para garantir posse antes de deletar — lança
	 * {@link RecursoNaoEncontradoException} se a matéria não existir ou pertencer a outro usuário.
	 * Os cartões da matéria são removidos em cascata pelo banco (migration V3, {@code ON DELETE
	 * CASCADE}).
	 */
	public void remover(UUID materiaId, UUID usuarioId) {
		Materia materia = buscarPorIdEUsuario(materiaId, usuarioId);
		materiaRepository.delete(materia);
	}
}
