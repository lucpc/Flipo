package com.flipo.backend.controller;

import com.flipo.backend.dto.CartaoRequest;
import com.flipo.backend.dto.CartaoResponse;
import com.flipo.backend.model.Cartao;
import com.flipo.backend.service.CartaoService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api/cartoes} e {@code /api/materias/{materiaId}/cartoes} (docs/03-contrato-api.md).
 * Um único controller sob {@code @RequestMapping("/api")} porque o contrato mistura os dois
 * prefixos (listar/criar ficam sob a matéria; editar/arquivar/desarquivar/remover ficam sob o
 * próprio cartão) — duas classes só pra separar esses dois grupos de rota não traria benefício
 * real aqui. Todos os endpoints exigem autenticação (garantido pela {@code SecurityConfig}) e são
 * escopados ao usuário do JWT — {@code usuarioId} nunca vem de path/body, sempre do
 * {@code Authentication} populado pelo {@code JwtAuthenticationFilter}, cujo principal é o
 * {@link UUID} do usuário.
 */
@RestController
@RequestMapping("/api")
public class CartaoController {

	private final CartaoService cartaoService;

	public CartaoController(CartaoService cartaoService) {
		this.cartaoService = cartaoService;
	}

	@GetMapping("/materias/{materiaId}/cartoes")
	public List<CartaoResponse> listar(
			@AuthenticationPrincipal UUID usuarioId,
			@PathVariable UUID materiaId,
			@RequestParam(defaultValue = "false") boolean arquivado) {
		return cartaoService.listarPorMateriaEUsuario(materiaId, usuarioId, arquivado).stream()
				.map(CartaoController::paraResponse)
				.toList();
	}

	@PostMapping("/materias/{materiaId}/cartoes")
	@ResponseStatus(HttpStatus.CREATED)
	public CartaoResponse criar(
			@AuthenticationPrincipal UUID usuarioId,
			@PathVariable UUID materiaId,
			@Valid @RequestBody CartaoRequest request) {
		Cartao cartao = cartaoService.criar(materiaId, usuarioId, request.pergunta(), request.resposta());
		return paraResponse(cartao);
	}

	@PatchMapping("/cartoes/{id}")
	public CartaoResponse editar(
			@AuthenticationPrincipal UUID usuarioId,
			@PathVariable UUID id,
			@Valid @RequestBody CartaoRequest request) {
		Cartao cartao = cartaoService.editar(id, usuarioId, request.pergunta(), request.resposta());
		return paraResponse(cartao);
	}

	@PatchMapping("/cartoes/{id}/arquivar")
	public CartaoResponse arquivar(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id) {
		Cartao cartao = cartaoService.arquivar(id, usuarioId);
		return paraResponse(cartao);
	}

	@PatchMapping("/cartoes/{id}/desarquivar")
	public CartaoResponse desarquivar(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id) {
		Cartao cartao = cartaoService.desarquivar(id, usuarioId);
		return paraResponse(cartao);
	}

	@DeleteMapping("/cartoes/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remover(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id) {
		cartaoService.remover(id, usuarioId);
	}

	private static CartaoResponse paraResponse(Cartao cartao) {
		return new CartaoResponse(
				cartao.getId(), cartao.getPergunta(), cartao.getResposta(),
				cartao.getOrigem(), cartao.isArquivado(), cartao.getUltimaRevisao());
	}
}
