package com.flipo.backend.repository;

import com.flipo.backend.model.ChaveApi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChaveApiRepository extends JpaRepository<ChaveApi, UUID> {

	List<ChaveApi> findByUsuarioId(UUID usuarioId);

	Optional<ChaveApi> findByUsuarioIdAndProvedor(UUID usuarioId, String provedor);
}
