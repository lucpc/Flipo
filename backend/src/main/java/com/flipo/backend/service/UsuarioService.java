package com.flipo.backend.service;

import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.UsuarioRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Dados do próprio usuário autenticado. {@code usuarioId} vem sempre do JWT, extraído pelo
 * controller — nunca de path/body (docs/04-arquitetura-tecnica.md, seção Segurança).
 */
@Service
public class UsuarioService {

	private final UsuarioRepository usuarioRepository;

	public UsuarioService(UsuarioRepository usuarioRepository) {
		this.usuarioRepository = usuarioRepository;
	}

	/**
	 * Devolve o usuário de id {@code usuarioId}. O {@code JwtAuthenticationFilter} já checou que
	 * ele existe; {@link RecursoNaoEncontradoException} só cobre a conta apagada entre o filtro e
	 * esta consulta.
	 */
	public Usuario buscarPorId(UUID usuarioId) {
		return usuarioRepository.findById(usuarioId).orElseThrow(RecursoNaoEncontradoException::new);
	}
}
