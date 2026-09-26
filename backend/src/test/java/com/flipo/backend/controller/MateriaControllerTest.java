package com.flipo.backend.controller;

import com.flipo.backend.model.Cartao;
import com.flipo.backend.model.Materia;
import com.flipo.backend.repository.CartaoRepository;
import com.flipo.backend.repository.MateriaRepository;

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

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita {@code /api/materias} de ponta a ponta (controller + service + security) contra o
 * Postgres real do docker-compose, como {@code AuthControllerTest}. {@code @Transactional} faz
 * cada teste dar rollback ao final, sem sujar o banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MateriaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MateriaRepository materiaRepository;

	@Autowired
	private CartaoRepository cartaoRepository;

	private String emailUnico() {
		return "usuaria-" + UUID.randomUUID() + "@flipo.test";
	}

	/** Registra um usuário novo e devolve um token JWT válido para ele. */
	private String registrarEObterToken() throws Exception {
		String email = emailUnico();
		mockMvc.perform(post("/api/auth/registro")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new RegistroBody("Usuária", email, "senha12345"))))
				.andExpect(status().isCreated());

		MvcResult resultado = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginBody(email, "senha12345"))))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
		return corpo.get("token").asText();
	}

	private String criarMateria(String token, String nome) throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/materias")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new MateriaBody(nome))))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
		return corpo.get("id").asText();
	}

	/** Persiste um cartão direto pelo repositório, sem passar pelo endpoint de criação, para manter este teste focado só nas contagens de {@code GET /api/materias}. */
	private void criarCartao(String materiaId, boolean arquivado) {
		Materia materia = materiaRepository.getReferenceById(UUID.fromString(materiaId));
		Cartao cartao = new Cartao(materia, "pergunta", "resposta", Cartao.ORIGEM_MANUAL);
		cartao.setArquivado(arquivado);
		cartaoRepository.save(cartao);
	}

	@Test
	void listarSemTokenDevolve401() throws Exception {
		mockMvc.perform(get("/api/materias"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void criarSemTokenDevolve401() throws Exception {
		mockMvc.perform(post("/api/materias")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new MateriaBody("Matemática"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deletarSemTokenDevolve401() throws Exception {
		mockMvc.perform(delete("/api/materias/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void listarSemMateriasDevolveListaVazia() throws Exception {
		String token = registrarEObterToken();

		mockMvc.perform(get("/api/materias")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void criarComSucessoDevolve201ComIdENome() throws Exception {
		String token = registrarEObterToken();

		mockMvc.perform(post("/api/materias")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new MateriaBody("Matemática"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", not(blankOrNullString())))
				.andExpect(jsonPath("$.nome").value("Matemática"));
	}

	@Test
	void criarComNomeEmBrancoDevolve400() throws Exception {
		String token = registrarEObterToken();

		mockMvc.perform(post("/api/materias")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new MateriaBody("   "))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listarDevolveApenasAsMateriasDoUsuarioAutenticado() throws Exception {
		String tokenA = registrarEObterToken();
		String tokenB = registrarEObterToken();
		criarMateria(tokenA, "Matéria da usuária A");
		criarMateria(tokenB, "Matéria da usuária B");

		mockMvc.perform(get("/api/materias")
				.header("Authorization", "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].nome").value("Matéria da usuária A"));
	}

	@Test
	void deletarComSucessoDevolve204ERemoveAMateria() throws Exception {
		String token = registrarEObterToken();
		String id = criarMateria(token, "Matéria a remover");

		mockMvc.perform(delete("/api/materias/" + id)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/materias")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void deletarMateriaDeOutroUsuarioDevolve404ENaoRemove() throws Exception {
		String tokenDona = registrarEObterToken();
		String tokenInvasora = registrarEObterToken();
		String idDaDona = criarMateria(tokenDona, "Matéria da dona");

		mockMvc.perform(delete("/api/materias/" + idDaDona)
				.header("Authorization", "Bearer " + tokenInvasora))
				.andExpect(status().isNotFound());

		// A matéria da dona continua existindo, intacta — a tentativa da invasora não teve efeito.
		mockMvc.perform(get("/api/materias")
				.header("Authorization", "Bearer " + tokenDona))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void deletarMateriaComIdInexistenteDevolve404() throws Exception {
		String token = registrarEObterToken();

		mockMvc.perform(delete("/api/materias/" + UUID.randomUUID())
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void listarDevolveTotalAtivosETotalArquivadosZeroParaMateriaSemCartoes() throws Exception {
		String token = registrarEObterToken();
		criarMateria(token, "Matéria sem cartões");

		mockMvc.perform(get("/api/materias")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].totalAtivos").value(0))
				.andExpect(jsonPath("$[0].totalArquivados").value(0));
	}

	@Test
	void listarDevolveTotalAtivosETotalArquivadosContandoOsCartoesDaMateria() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria com cartões");
		criarCartao(materiaId, false);
		criarCartao(materiaId, false);
		criarCartao(materiaId, true);

		mockMvc.perform(get("/api/materias")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].totalAtivos").value(2))
				.andExpect(jsonPath("$[0].totalArquivados").value(1));
	}

	@Test
	void listarNaoSomaCartoesDeMateriaDeOutroUsuarioNaContagem() throws Exception {
		String tokenA = registrarEObterToken();
		String tokenB = registrarEObterToken();
		String materiaA = criarMateria(tokenA, "Matéria da usuária A");
		String materiaB = criarMateria(tokenB, "Matéria da usuária B");
		criarCartao(materiaA, false);
		criarCartao(materiaB, false);
		criarCartao(materiaB, false);
		criarCartao(materiaB, true);

		mockMvc.perform(get("/api/materias")
				.header("Authorization", "Bearer " + tokenA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].totalAtivos").value(1))
				.andExpect(jsonPath("$[0].totalArquivados").value(0));
	}

	private record RegistroBody(String nome, String email, String senha) {
	}

	private record LoginBody(String email, String senha) {
	}

	private record MateriaBody(String nome) {
	}
}
