package com.flipo.backend.service;

import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.UsuarioRepository;
import com.flipo.backend.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		// O construtor sempre gera um hash "fantasma" (mitigação de timing no login) — sem stub,
		// o mock devolveria null e quebraria a comparação em matches(senha, hashFantasma).
		when(passwordEncoder.encode(anyString())).thenReturn("hash-fantasma");
		authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);
	}

	@Test
	void registrarSalvaUsuarioComSenhaEmHashQuandoEmailAindaNaoExiste() {
		when(usuarioRepository.existsByEmail("nova@flipo.test")).thenReturn(false);
		when(passwordEncoder.encode("senha-em-texto-puro")).thenReturn("senha-em-hash");
		when(usuarioRepository.saveAndFlush(any(Usuario.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		Usuario salvo = authService.registrar("Nova", "nova@flipo.test", "senha-em-texto-puro");

		assertThat(salvo.getNome()).isEqualTo("Nova");
		assertThat(salvo.getEmail()).isEqualTo("nova@flipo.test");
		assertThat(salvo.getSenhaHash()).isEqualTo("senha-em-hash");

		ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
		verify(usuarioRepository).saveAndFlush(captor.capture());
		// A senha em texto puro nunca chega a ser persistida — só o hash.
		assertThat(captor.getValue().getSenhaHash()).isNotEqualTo("senha-em-texto-puro");
	}

	@Test
	void registrarLancaEmailJaCadastradoQuandoEmailJaExisteENaoTentaSalvar() {
		when(usuarioRepository.existsByEmail("ja-existe@flipo.test")).thenReturn(true);

		assertThatThrownBy(() -> authService.registrar("Nome", "ja-existe@flipo.test", "senha12345"))
				.isInstanceOf(EmailJaCadastradoException.class);

		verify(usuarioRepository, never()).saveAndFlush(any());
	}

	@Test
	void registrarLancaEmailJaCadastradoQuandoBancoRejeitaPorCorridaDeCorrida() {
		when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
		when(passwordEncoder.encode(anyString())).thenReturn("hash");
		when(usuarioRepository.saveAndFlush(any(Usuario.class)))
				.thenThrow(new DataIntegrityViolationException("uk_usuarios_email"));

		assertThatThrownBy(() -> authService.registrar("Nome", "corrida@flipo.test", "senha12345"))
				.isInstanceOf(EmailJaCadastradoException.class);
	}

	@Test
	void loginDevolveTokenQuandoEmailExisteESenhaConfere() {
		Usuario usuario = new Usuario("Usuária", "usuaria@flipo.test", "senha-em-hash");
		ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
		when(usuarioRepository.findByEmail("usuaria@flipo.test")).thenReturn(Optional.of(usuario));
		when(passwordEncoder.matches("senha-correta", "senha-em-hash")).thenReturn(true);
		when(jwtService.gerarToken(usuario)).thenReturn("token-jwt");

		String token = authService.login("usuaria@flipo.test", "senha-correta");

		assertThat(token).isEqualTo("token-jwt");
	}

	@Test
	void loginLancaCredenciaisInvalidasQuandoEmailNaoExisteMasAindaAssimComparaHashParaEvitarTiming() {
		when(usuarioRepository.findByEmail("nao-existe@flipo.test")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login("nao-existe@flipo.test", "qualquer-senha"))
				.isInstanceOf(CredenciaisInvalidasException.class);

		// Sem essa chamada, o tempo de resposta de um e-mail inexistente seria menor que o de um
		// e-mail existente com senha errada — um side-channel de user enumeration.
		verify(passwordEncoder).matches(eq("qualquer-senha"), anyString());
	}

	@Test
	void loginLancaCredenciaisInvalidasQuandoSenhaNaoConfereSemVazarQualFoiOMotivo() {
		Usuario usuario = new Usuario("Usuária", "usuaria@flipo.test", "senha-em-hash");
		when(usuarioRepository.findByEmail("usuaria@flipo.test")).thenReturn(Optional.of(usuario));
		when(passwordEncoder.matches(eq("senha-errada"), eq("senha-em-hash"))).thenReturn(false);

		assertThatThrownBy(() -> authService.login("usuaria@flipo.test", "senha-errada"))
				.isInstanceOf(CredenciaisInvalidasException.class)
				.hasMessage("E-mail ou senha inválidos.");
	}
}
