package com.flipo.backend.controller;

import com.flipo.backend.ia.CartaoSugerido;
import com.flipo.backend.ia.GeradorDeCartoes;
import com.flipo.backend.ia.NivelCartao;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O {@code gerar-ia} não segura conexão do banco enquanto o gerador trabalha: uma chamada lenta a
 * um provedor de IA não pode prender uma conexão do pool. O gerador deste teste lê quantas conexões
 * do Hikari estão em uso no momento em que é chamado. Depende de
 * {@code spring.jpa.open-in-view=false}: com o open-in-view ligado, a conexão usada na checagem de
 * posse da matéria fica presa até o fim da requisição e a leitura dá 1.
 *
 * <p>Sem {@code @Transactional}: a transação do próprio teste seguraria uma conexão. Por isso o
 * usuário criado é apagado ao final (matéria em cascata).
 */
@SpringBootTest
@AutoConfigureMockMvc
class GerarIaSemConexaoPresaTest {

	@TestConfiguration
	static class Configuracao {

		@Bean
		GeradorQueMedeConexoes geradorQueMedeConexoes(DataSource dataSource) {
			return new GeradorQueMedeConexoes(dataSource);
		}
	}

	static class GeradorQueMedeConexoes implements GeradorDeCartoes {

		private final DataSource dataSource;
		private Integer conexoesAtivasDuranteAGeracao;

		GeradorQueMedeConexoes(DataSource dataSource) {
			this.dataSource = dataSource;
		}

		@Override
		public List<CartaoSugerido> gerar(String texto, int quantidade, NivelCartao nivel) {
			try {
				conexoesAtivasDuranteAGeracao = dataSource.unwrap(HikariDataSource.class)
						.getHikariPoolMXBean().getActiveConnections();
			} catch (SQLException e) {
				throw new IllegalStateException(e);
			}
			return List.of(new CartaoSugerido("pergunta", "resposta"));
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private GeradorQueMedeConexoes gerador;

	private final String email = "conexao-" + UUID.randomUUID() + "@flipo.test";

	@AfterEach
	void limpar() {
		jdbcTemplate.update("DELETE FROM usuarios WHERE email = ?", email);
	}

	@Test
	void gerarIaNaoSeguraConexaoDoBancoDuranteAChamadaAoGerador() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);

		mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes/gerar-ia")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\": \"Texto qualquer.\"}"))
				.andExpect(status().isOk());

		assertThat(gerador.conexoesAtivasDuranteAGeracao).isNotNull().isZero();
	}

	private String registrarEObterToken() throws Exception {
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
