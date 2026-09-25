package com.flipo.backend.security;

import com.flipo.backend.model.Usuario;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emite e valida os JWT usados para autenticar requisições (docs/04-arquitetura-tecnica.md,
 * seção Segurança). O subject do token é o id do {@link Usuario} — é a partir dele que qualquer
 * endpoint futuro escopa dados ao usuário autenticado, nunca a partir de um id vindo do
 * path/body da requisição.
 */
@Component
public class JwtService {

	private final SecretKey chave;
	private final long expiracaoMs;

	public JwtService(
			@Value("${flipo.jwt.secret}") String segredo,
			@Value("${flipo.jwt.expiration-ms}") long expiracaoMs) {
		this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
		this.expiracaoMs = expiracaoMs;
	}

	public String gerarToken(Usuario usuario) {
		Instant agora = Instant.now();
		return Jwts.builder()
				.subject(usuario.getId().toString())
				.issuedAt(Date.from(agora))
				.expiration(Date.from(agora.plusMillis(expiracaoMs)))
				// Algoritmo fixo (em vez de deixar o jjwt escolher pelo tamanho da chave): evita que o
				// mesmo segredo produza HS256 num ambiente e HS384/HS512 noutro.
				.signWith(chave, Jwts.SIG.HS256)
				.compact();
	}

	/**
	 * Extrai o id do usuário autenticado a partir de um token. Lança {@link JwtException} (ou uma
	 * subclasse, ex: token expirado/malformado/assinatura inválida) se o token não for válido —
	 * quem chama decide como reagir, sem vazar detalhe nenhum ao cliente.
	 */
	public UUID extrairUsuarioId(String token) {
		String subject = Jwts.parser()
				.verifyWith(chave)
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();
		return UUID.fromString(subject);
	}
}
