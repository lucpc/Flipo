package com.flipo.backend.controller;

import com.flipo.backend.dto.RegistroResponse;
import com.flipo.backend.model.Usuario;
import com.flipo.backend.service.UsuarioService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code /api/usuarios/me} (docs/03-contrato-api.md, seção Usuário). Exige autenticação
 * (garantido pela {@code SecurityConfig}); o usuário é sempre o do JWT, nunca um id de path/body.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	@GetMapping("/me")
	public RegistroResponse buscarMe(@AuthenticationPrincipal UUID usuarioId) {
		Usuario usuario = usuarioService.buscarPorId(usuarioId);
		return new RegistroResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
	}
}
