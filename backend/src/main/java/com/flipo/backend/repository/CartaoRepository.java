package com.flipo.backend.repository;

import com.flipo.backend.model.Cartao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartaoRepository extends JpaRepository<Cartao, UUID> {

	// Escopado pelo dono da matéria (via Materia.usuario) — evita que um usuário liste cartões
	// de uma matéria de outro usuário adivinhando o materiaId.
	List<Cartao> findByMateriaIdAndMateria_Usuario_Id(UUID materiaId, UUID usuarioId);

	// Busca por id já escopada ao dono da matéria — usar sempre no lugar de findById(id) puro
	// (herdado de JpaRepository, não escopado) quando o acesso depende de posse do usuário
	// autenticado.
	Optional<Cartao> findByIdAndMateria_Usuario_Id(UUID id, UUID usuarioId);
}
