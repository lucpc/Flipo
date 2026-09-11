package com.flipo.backend.repository;

import com.flipo.backend.model.Cartao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CartaoRepository extends JpaRepository<Cartao, UUID> {

	List<Cartao> findByMateriaId(UUID materiaId);
}
