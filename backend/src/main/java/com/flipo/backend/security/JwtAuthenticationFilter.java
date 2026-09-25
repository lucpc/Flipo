package com.flipo.backend.security;

import com.flipo.backend.repository.UsuarioRepository;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Lê o header {@code Authorization: Bearer <token>}, valida o JWT e — se válido e o usuário ainda
 * existir — autentica a requisição no {@link SecurityContextHolder} usando o id do usuário como
 * principal. Token ausente/inválido/expirado apenas deixa a requisição seguir sem autenticação;
 * quem decide se isso é aceitável é a {@code SecurityFilterChain} (docs/04-arquitetura-tecnica.md).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String PREFIXO_BEARER = "Bearer ";

	private final JwtService jwtService;
	private final UsuarioRepository usuarioRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
		this.jwtService = jwtService;
		this.usuarioRepository = usuarioRepository;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");

		if (header != null && header.startsWith(PREFIXO_BEARER)
				&& SecurityContextHolder.getContext().getAuthentication() == null) {
			String token = header.substring(PREFIXO_BEARER.length());
			try {
				UUID usuarioId = jwtService.extrairUsuarioId(token);
				if (usuarioRepository.existsById(usuarioId)) {
					var authentication =
							new UsernamePasswordAuthenticationToken(usuarioId, null, List.of());
					authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
			} catch (JwtException | IllegalArgumentException e) {
				// Token malformado/expirado/assinatura inválida: segue sem autenticar — a
				// SecurityFilterChain rejeita a requisição adiante.
			}
		}

		filterChain.doFilter(request, response);
	}
}
