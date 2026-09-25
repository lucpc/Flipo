package com.flipo.backend.service;

import com.flipo.backend.model.Materia;
import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.CartaoRepository;
import com.flipo.backend.repository.CartaoRepository.ContagemPorMateria;
import com.flipo.backend.repository.MateriaRepository;
import com.flipo.backend.repository.UsuarioRepository;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	private final CartaoRepository cartaoRepository;

	public MateriaService(
			MateriaRepository materiaRepository,
			UsuarioRepository usuarioRepository,
			CartaoRepository cartaoRepository) {
		this.materiaRepository = materiaRepository;
		this.usuarioRepository = usuarioRepository;
		this.cartaoRepository = cartaoRepository;
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

	/**
	 * Lista todas as matérias de {@code usuarioId}, cada uma com sua contagem de cartões ativos e
	 * arquivados. As contagens vêm de uma única query agregada ({@link CartaoRepository
	 * #contarPorMateriaEArquivadoDoUsuario}) combinada em memória com a lista de matérias — custo
	 * de duas queries no total, não uma por matéria (sem N+1). Matérias sem cartão de um dos dois
	 * tipos (ou de nenhum) aparecem com contagem 0, não são omitidas.
	 */
	public List<MateriaComContagem> listarPorUsuario(UUID usuarioId) {
		List<Materia> materias = materiaRepository.findByUsuarioId(usuarioId);

		Map<UUID, long[]> contagensPorMateria = new HashMap<>();
		for (ContagemPorMateria contagem : cartaoRepository.contarPorMateriaEArquivadoDoUsuario(usuarioId)) {
			long[] par = contagensPorMateria.computeIfAbsent(contagem.getMateriaId(), id -> new long[2]);
			if (contagem.isArquivado()) {
				par[1] = contagem.getTotal();
			} else {
				par[0] = contagem.getTotal();
			}
		}

		return materias.stream()
				.map(materia -> {
					long[] par = contagensPorMateria.getOrDefault(materia.getId(), new long[2]);
					return new MateriaComContagem(materia, par[0], par[1]);
				})
				.toList();
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

	/** Uma {@link Materia} com sua contagem de cartões ativos e arquivados — ver {@link #listarPorUsuario}. */
	public record MateriaComContagem(Materia materia, long totalAtivos, long totalArquivados) {
	}
}
