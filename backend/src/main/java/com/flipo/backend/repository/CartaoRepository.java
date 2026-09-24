package com.flipo.backend.repository;

import com.flipo.backend.model.Cartao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CartaoRepository extends JpaRepository<Cartao, UUID> {

	// Escopado pelo dono da matéria (via Materia.usuario) — evita que um usuário liste cartões
	// de uma matéria de outro usuário adivinhando o materiaId.
	List<Cartao> findByMateriaIdAndMateria_Usuario_Id(UUID materiaId, UUID usuarioId);
}
