package com.flipo.backend.controller;

import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.UsuarioRepository;
import com.flipo.backend.security.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita {@code /api/auth/*} de ponta a ponta (controller + service + security) contra o
 * Postgres real do docker-compose, como {@code JpaModelTest} — ver README/Makefile ({@code make
 * up}). {@code @Transactional} faz cada teste dar rollback ao final, sem sujar o banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JwtService jwtService;

	private String emailUnico() {
		return "usuaria-" + UUID.randomUUID() + "@flipo.test";
	}

	private void registrar(String nome, String email, String senha) throws Exception {
		mockMvc.perform(post("/api/auth/registro")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new RegistroBody(nome, email, senha))))
				.andExpect(status().isCreated());
	}

	@Test
	void registrarComSucessoDevolve201ComIdNomeEEmailSemSenha() throws Exception {
		String email = emailUnico();

		mockMvc.perform(post("/api/auth/registro")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new RegistroBody("Usuária", email, "senha12345"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", not(blankOrNullString())))
				.andExpect(jsonPath("$.nome").value("Usuária"))
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.senha").doesNotExist())
				.andExpect(jsonPath("$.senhaHash").doesNotExist());
	}

	@Test
	void registrarComEmailDuplicadoDevolve409SemVazarStacktrace() throws Exception {
		String email = emailUnico();
		registrar("Primeira", email, "senha12345");

		mockMvc.perform(post("/api/auth/registro")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new RegistroBody("Segunda", email, "outrasenha"))))
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON));
	}

	@Test
	void loginComSucessoDevolve200ComTokenJwtDeTresPartes() throws Exception {
		String email = emailUnico();
		registrar("Usuária", email, "senha12345");

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginBody(email, "senha12345"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				// JWT compacto é header.payload.assinatura.
				.andExpect(jsonPath("$.token", org.hamcrest.Matchers.matchesPattern("^[^.]+\\.[^.]+\\.[^.]+$")));
	}

	@Test
	void loginComSenhaErradaDevolve401ComMensagemGenerica() throws Exception {
		String email = emailUnico();
		registrar("Usuária", email, "senha12345");

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginBody(email, "senha-errada"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos."));
	}

	@Test
	void loginComEmailInexistenteDevolve401ComAMesmaMensagemGenericaDeSenhaErrada() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginBody(emailUnico(), "qualquer-senha"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos."));
	}

	@Test
	void endpointProtegidoSemTokenDevolve401() throws Exception {
		// Ainda não existe nenhum controller de matérias/cartões nesta issue — o que este teste
		// prova é que a SecurityFilterChain rejeita ANTES de chegar a um handler (não é um 404),
		// para qualquer rota fora de /api/auth/**.
		mockMvc.perform(get("/api/materias"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void endpointProtegidoComTokenInvalidoDevolve401() throws Exception {
		mockMvc.perform(get("/api/materias")
				.header("Authorization", "Bearer isto-nao-e-um-jwt-valido"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void endpointProtegidoComTokenValidoDeUsuarioApagadoDevolve401() throws Exception {
		// Assinatura e expiração corretas não bastam: o filtro também re-checa que o usuário do
		// subject ainda existe (JwtAuthenticationFilter), para que apagar uma conta invalide
		// imediatamente qualquer token emitido antes, sem esperar a expiração.
		String email = emailUnico();
		registrar("Usuária", email, "senha12345");
		Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
		String token = jwtService.gerarToken(usuario);

		usuarioRepository.delete(usuario);
		usuarioRepository.flush();

		mockMvc.perform(get("/api/materias")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	private record RegistroBody(String nome, String email, String senha) {
	}

	private record LoginBody(String email, String senha) {
	}
}
