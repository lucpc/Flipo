package com.flipo.backend.controller;

import com.flipo.backend.dto.MateriaRequest;
import com.flipo.backend.dto.MateriaResponse;
import com.flipo.backend.model.Materia;
import com.flipo.backend.service.MateriaService;
import com.flipo.backend.service.MateriaService.MateriaComContagem;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api/materias} (docs/03-contrato-api.md). Todos os endpoints exigem autenticação
 * (garantido pela {@code SecurityConfig}) e são escopados ao usuário do JWT — {@code usuarioId}
 * nunca vem de path/body, sempre do {@code Authentication} populado pelo
 * {@code JwtAuthenticationFilter}, cujo principal é o {@link UUID} do usuário.
 */
@RestController
@RequestMapping("/api/materias")
public class MateriaController {

	private final MateriaService materiaService;

	public MateriaController(MateriaService materiaService) {
		this.materiaService = materiaService;
	}

	@GetMapping
	public List<MateriaResponse> listar(@AuthenticationPrincipal UUID usuarioId) {
		return materiaService.listarPorUsuario(usuarioId).stream()
				.map(MateriaController::paraResponse)
				.toList();
	}

	private static MateriaResponse paraResponse(MateriaComContagem materiaComContagem) {
		Materia materia = materiaComContagem.materia();
		return new MateriaResponse(
				materia.getId(), materia.getNome(),
				materiaComContagem.totalAtivos(), materiaComContagem.totalArquivados());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MateriaResponse criar(
			@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody MateriaRequest request) {
		Materia materia = materiaService.criar(usuarioId, request.nome());
		return paraResponse(materia);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remover(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id) {
		materiaService.remover(id, usuarioId);
	}

	// Matéria recém-criada não tem cartões ainda — 0/0 sem precisar consultar CartaoRepository.
	private static MateriaResponse paraResponse(Materia materia) {
		return new MateriaResponse(materia.getId(), materia.getNome(), 0, 0);
	}
}
