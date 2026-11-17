package com.flipo.backend.config;

import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.UsuarioRepository;
import com.flipo.backend.security.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS para o build web do app (issue #50), com a origem padrão de dev
 * ({@code flipo.cors.allowed-origin-patterns = http://localhost:*}). Roda contra o Postgres real,
 * como os testes de controller; {@code @Transactional} dá rollback ao final de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CorsTest {

	/** Porta qualquer: o {@code flutter run -d chrome} sorteia uma a cada execução. */
	private static final String ORIGEM_PERMITIDA = "http://localhost:53123";

	private static final String ORIGEM_NAO_PERMITIDA = "https://site-malicioso.example";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JwtService jwtService;

	@Test
	void preflightDeOrigemPermitidaEmEndpointProtegidoPassaSemToken() throws Exception {
		mockMvc.perform(options("/api/materias")
				.header("Origin", ORIGEM_PERMITIDA)
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "Authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ORIGEM_PERMITIDA))
				.andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
				// JWT vai no cabeçalho, não em cookie: credenciais não são liberadas.
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
	}

	@Test
	void preflightDeOrigemPermitidaNoLoginRecebeCabecalhosCors() throws Exception {
		mockMvc.perform(options("/api/auth/login")
				.header("Origin", ORIGEM_PERMITIDA)
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ORIGEM_PERMITIDA))
				.andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
	}

	@Test
	void preflightDeOrigemNaoPermitidaEhRecusado() throws Exception {
		mockMvc.perform(options("/api/materias")
				.header("Origin", ORIGEM_NAO_PERMITIDA)
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	/**
	 * {@code [*]} só cobre a porta: uma origem que apenas começa com "localhost:" não passa. Com um
	 * {@code *} simples, a segunda origem (userinfo {@code localhost:1}, host {@code evil.com}) seria
	 * aceita; a primeira o Spring já recusa como malformada.
	 */
	@Test
	void preflightDeOrigemParecidaComLocalhostEhRecusado() throws Exception {
		for (String origem : List.of("http://localhost:8080.evil.com", "http://localhost:1@evil.com")) {
			mockMvc.perform(options("/api/materias")
					.header("Origin", origem)
					.header("Access-Control-Request-Method", "GET"))
					.andExpect(status().isForbidden())
					.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		}
	}

	/**
	 * CORS não afrouxa a autenticação; e o 401 leva allow-origin para o app web conseguir lê-lo
	 * (o {@code api_client.dart} limpa o token e volta ao login ao ver um 401).
	 */
	@Test
	void getSemTokenDeOrigemPermitidaDevolve401ComAllowOrigin() throws Exception {
		mockMvc.perform(get("/api/materias")
				.header("Origin", ORIGEM_PERMITIDA))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Access-Control-Allow-Origin", ORIGEM_PERMITIDA));
	}

	@Test
	void getAutenticadoDeOrigemPermitidaRecebeAllowOrigin() throws Exception {
		Usuario usuario = usuarioRepository.save(
				new Usuario("Usuária", "usuaria-" + UUID.randomUUID() + "@flipo.test", "hash-qualquer"));
		String token = jwtService.gerarToken(usuario);

		mockMvc.perform(get("/api/materias")
				.header("Origin", ORIGEM_PERMITIDA)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ORIGEM_PERMITIDA));
	}
}
