package com.flipo.backend.controller;

import com.flipo.backend.ia.GeradorDeCartoes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code gerar-ia} no contexto da aplicação sem chave da Anthropic configurada (os testes a fixam
 * vazia em {@code src/test/resources/config/application.properties}), logo sem nenhum
 * {@link GeradorDeCartoes} registrado: responde 503 com uma mensagem clara, sem cair num gerador
 * falso. {@code @Transactional} dá rollback ao final, como nos outros testes de controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GerarIaSemGeradorTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ApplicationContext contexto;

	@Test
	void aAplicacaoNaoRegistraNenhumGeradorPorPadrao() {
		assertThat(contexto.getBeanNamesForType(GeradorDeCartoes.class)).isEmpty();
	}

	@Test
	void gerarIaSemGeradorConfiguradoDevolve503ComMensagemClara() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);

		mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes/gerar-ia")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\": \"Texto qualquer.\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.mensagem")
						.value("A geração de cartões por IA não está configurada neste servidor."));
	}

	@Test
	void gerarIaSemGeradorEmMateriaDeOutroUsuarioDevolve404AntesDo503() throws Exception {
		String materiaDoDono = criarMateria(registrarEObterToken());
		String tokenOutro = registrarEObterToken();

		mockMvc.perform(post("/api/materias/" + materiaDoDono + "/cartoes/gerar-ia")
				.header("Authorization", "Bearer " + tokenOutro)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\": \"Texto qualquer.\"}"))
				.andExpect(status().isNotFound());
	}

	private String registrarEObterToken() throws Exception {
		String email = "sem-gerador-" + UUID.randomUUID() + "@flipo.test";
		mockMvc.perform(post("/api/auth/registro")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						Map.of("nome", "Usuária", "email", email, "senha", "senha12345"))))
				.andExpect(status().isCreated());
		MvcResult resultado = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", email, "senha", "senha12345"))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	private String criarMateria(String token) throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/materias")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("nome", "Biologia"))))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asText();
	}
}
