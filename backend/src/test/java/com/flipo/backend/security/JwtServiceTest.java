package com.flipo.backend.security;

import com.flipo.backend.model.Usuario;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

	private static final String SEGREDO_DE_TESTE =
			"segredo-de-teste-com-tamanho-suficiente-para-hmac-sha256-0123456789";

	private final JwtService jwtService = new JwtService(SEGREDO_DE_TESTE, 60_000L);

	@Test
	void gerarTokenEDepoisExtrairUsuarioIdDevolveOMesmoId() {
		Usuario usuario = new Usuario("Usuária", "usuaria@flipo.test", "hash");
		UUID id = UUID.randomUUID();
		setId(usuario, id);

		String token = jwtService.gerarToken(usuario);

		assertThat(jwtService.extrairUsuarioId(token)).isEqualTo(id);
	}

	@Test
	void rejeitaTokenAssinadoComOutroSegredo() {
		Usuario usuario = new Usuario("Usuária", "usuaria@flipo.test", "hash");
		setId(usuario, UUID.randomUUID());
		String token = jwtService.gerarToken(usuario);

		JwtService outroServico = new JwtService("outro-segredo-completamente-diferente-0123456789012345", 60_000L);

		assertThatThrownBy(() -> outroServico.extrairUsuarioId(token))
				.isInstanceOf(SignatureException.class);
	}

	@Test
	void rejeitaTokenExpirado() {
		JwtService servicoComExpiracaoImediata = new JwtService(SEGREDO_DE_TESTE, -1_000L);
		Usuario usuario = new Usuario("Usuária", "usuaria@flipo.test", "hash");
		setId(usuario, UUID.randomUUID());

		String tokenJaExpirado = servicoComExpiracaoImediata.gerarToken(usuario);

		assertThatThrownBy(() -> jwtService.extrairUsuarioId(tokenJaExpirado))
				.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void rejeitaTokenMalformado() {
		assertThatThrownBy(() -> jwtService.extrairUsuarioId("isto-nao-e-um-jwt"))
				.isInstanceOf(JwtException.class);
	}

	private void setId(Usuario usuario, UUID id) {
		// Usuario.id só é atribuído pelo JPA (@GeneratedValue) — em teste unitário sem banco,
		// injeta via reflection para simular uma entidade já persistida.
		ReflectionTestUtils.setField(usuario, "id", id);
	}
}
