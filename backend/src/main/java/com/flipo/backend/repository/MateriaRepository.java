package com.flipo.backend.repository;

import com.flipo.backend.model.Materia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MateriaRepository extends JpaRepository<Materia, UUID> {

	// Toda listagem de matérias é escopada ao usuário autenticado (JWT) — nunca a um id
	// arbitrário vindo do path/body.
	List<Materia> findByUsuarioId(UUID usuarioId);
}
