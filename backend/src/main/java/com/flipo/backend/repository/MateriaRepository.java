package com.flipo.backend.repository;

import com.flipo.backend.model.Materia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MateriaRepository extends JpaRepository<Materia, UUID> {

	// Toda listagem de matérias é escopada ao usuário autenticado (JWT) — nunca a um id
	// arbitrário vindo do path/body.
	List<Materia> findByUsuarioId(UUID usuarioId);

	// Busca por id já escopada ao dono — usar sempre no lugar de findById(id) puro (herdado de
	// JpaRepository, não escopado) quando o acesso depende de posse do usuário autenticado.
	Optional<Materia> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}
