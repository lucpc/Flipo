package com.flipo.backend.repository;

import com.flipo.backend.model.Cartao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartaoRepository extends JpaRepository<Cartao, UUID> {

	// Escopado pelo dono da matéria (via Materia.usuario) — evita que um usuário liste cartões
	// de uma matéria de outro usuário adivinhando o materiaId.
	List<Cartao> findByMateriaIdAndMateria_Usuario_Id(UUID materiaId, UUID usuarioId);

	// Lista já filtrada por arquivado=true/false na própria query (GET .../cartoes?arquivado=),
	// em vez de trazer tudo e filtrar em memória.
	List<Cartao> findByMateriaIdAndMateria_Usuario_IdAndArquivado(
			UUID materiaId, UUID usuarioId, boolean arquivado);

	// Busca por id já escopada ao dono da matéria — usar sempre no lugar de findById(id) puro
	// (herdado de JpaRepository, não escopado) quando o acesso depende de posse do usuário
	// autenticado.
	Optional<Cartao> findByIdAndMateria_Usuario_Id(UUID id, UUID usuarioId);

	// Uma única query agregada para todas as matérias do usuário (GROUP BY materia + arquivado),
	// em vez de uma query de contagem por matéria — evita N+1 ao montar GET /api/materias.
	// Escopado por Materia.usuario, nunca por um materiaId cru vindo de fora.
	@Query("SELECT c.materia.id AS materiaId, c.arquivado AS arquivado, COUNT(c) AS total "
			+ "FROM Cartao c WHERE c.materia.usuario.id = :usuarioId "
			+ "GROUP BY c.materia.id, c.arquivado")
	List<ContagemPorMateria> contarPorMateriaEArquivadoDoUsuario(@Param("usuarioId") UUID usuarioId);

	/** Projeção do {@code GROUP BY} acima — uma linha por (matéria, arquivado) com o total. */
	interface ContagemPorMateria {
		UUID getMateriaId();

		boolean isArquivado();

		long getTotal();
	}
}
