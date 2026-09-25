package com.flipo.backend.controller;

import com.flipo.backend.dto.LoginRequest;
import com.flipo.backend.dto.LoginResponse;
import com.flipo.backend.dto.RegistroRequest;
import com.flipo.backend.dto.RegistroResponse;
import com.flipo.backend.model.Usuario;
import com.flipo.backend.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/auth/*} — únicos endpoints públicos da API (docs/03-contrato-api.md). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public RegistroResponse registrar(@Valid @RequestBody RegistroRequest request) {
		Usuario usuario = authService.registrar(request.nome(), request.email(), request.senha());
		return new RegistroResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		String token = authService.login(request.email(), request.senha());
		return new LoginResponse(token);
	}
}
