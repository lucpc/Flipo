package com.flipo.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita {@code /api/usuarios/me} de ponta a ponta (controller + service + security) contra o
 * Postgres real do docker-compose, como {@code AuthControllerTest}. {@code @Transactional} faz
 * cada teste dar rollback ao final, sem sujar o banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String emailUnico() {
		return "usuaria-" + UUID.randomUUID() + "@flipo.test";
	}

	/** Registra um usuário novo e devolve o id criado. */
	private String registrar(String nome, String email) throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/auth/registro")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new RegistroBody(nome, email, "senha12345"))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asText();
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginBody(email, "senha12345"))))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
		return corpo.get("token").asText();
	}

	@Test
	void meDevolve200ComOsDadosDoUsuarioDoTokenSemSenha() throws Exception {
		// Dois usuários: o token da Ana tem que devolver a Ana, nunca o outro.
		String emailAna = emailUnico();
		String idAna = registrar("Ana", emailAna);
		registrar("Bruno", emailUnico());
		String tokenAna = login(emailAna);

		mockMvc.perform(get("/api/usuarios/me")
				.header("Authorization", "Bearer " + tokenAna))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(idAna))
				.andExpect(jsonPath("$.nome").value("Ana"))
				.andExpect(jsonPath("$.email").value(emailAna))
				.andExpect(jsonPath("$.senha").doesNotExist())
				.andExpect(jsonPath("$.senhaHash").doesNotExist())
				// Só os três campos do contrato: nenhum outro nome de campo pode carregar o hash.
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void meSemTokenDevolve401() throws Exception {
		mockMvc.perform(get("/api/usuarios/me"))
				.andExpect(status().isUnauthorized());
	}

	private record RegistroBody(String nome, String email, String senha) {
	}

	private record LoginBody(String email, String senha) {
	}
}
