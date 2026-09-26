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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita {@code /api/materias/{materiaId}/cartoes} e {@code /api/cartoes} de ponta a ponta
 * (controller + service + security) contra o Postgres real do docker-compose, como
 * {@code MateriaControllerTest}. {@code @Transactional} faz cada teste dar rollback ao final, sem
 * sujar o banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CartaoControllerTest {

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

	/** Persiste um cartão direto pelo repositório, sem passar pelo endpoint de criação. */
	private String criarCartaoDireto(String materiaId, boolean arquivado) {
		Materia materia = materiaRepository.getReferenceById(UUID.fromString(materiaId));
		Cartao cartao = new Cartao(materia, "pergunta", "resposta", Cartao.ORIGEM_MANUAL);
		cartao.setArquivado(arquivado);
		return cartaoRepository.save(cartao).getId().toString();
	}

	@Test
	void listarSemTokenDevolve401() throws Exception {
		mockMvc.perform(get("/api/materias/" + UUID.randomUUID() + "/cartoes"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void criarSemTokenDevolve401() throws Exception {
		mockMvc.perform(post("/api/materias/" + UUID.randomUUID() + "/cartoes")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CartaoBody("pergunta", "resposta"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void editarSemTokenDevolve401() throws Exception {
		mockMvc.perform(patch("/api/cartoes/" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CartaoBody("pergunta", "resposta"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void arquivarSemTokenDevolve401() throws Exception {
		mockMvc.perform(patch("/api/cartoes/" + UUID.randomUUID() + "/arquivar"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void desarquivarSemTokenDevolve401() throws Exception {
		mockMvc.perform(patch("/api/cartoes/" + UUID.randomUUID() + "/desarquivar"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deletarSemTokenDevolve401() throws Exception {
		mockMvc.perform(delete("/api/cartoes/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void listarComMateriaIdInexistenteDevolve404() throws Exception {
		String token = registrarEObterToken();

		mockMvc.perform(get("/api/materias/" + UUID.randomUUID() + "/cartoes")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void listarComMateriaDeOutroUsuarioDevolve404() throws Exception {
		String tokenDona = registrarEObterToken();
		String tokenInvasora = registrarEObterToken();
		String materiaId = criarMateria(tokenDona, "Matéria da dona");
		criarCartaoDireto(materiaId, false);

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + tokenInvasora))
				.andExpect(status().isNotFound());
	}

	@Test
	void listarSemFiltroDevolveApenasCartoesAtivosPorPadrao() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria");
		criarCartaoDireto(materiaId, false);
		criarCartaoDireto(materiaId, true);

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].arquivado").value(false));
	}

	@Test
	void listarComArquivadoTrueDevolveApenasCartoesArquivados() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria");
		criarCartaoDireto(materiaId, false);
		criarCartaoDireto(materiaId, true);
		criarCartaoDireto(materiaId, true);

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.param("arquivado", "true")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].arquivado").value(true))
				.andExpect(jsonPath("$[1].arquivado").value(true));
	}

	@Test
	void criarComSucessoDevolve201ComOrigemManualENaoArquivado() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria");

		mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CartaoBody("Quem descobriu o Brasil?", "Cabral"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", not(blankOrNullString())))
				.andExpect(jsonPath("$.pergunta").value("Quem descobriu o Brasil?"))
				.andExpect(jsonPath("$.resposta").value("Cabral"))
				.andExpect(jsonPath("$.origem").value("manual"))
				.andExpect(jsonPath("$.arquivado").value(false));
	}

	@Test
	void criarComPerguntaEmBrancoDevolve400() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria");

		mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CartaoBody("   ", "resposta"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void criarEmMateriaDeOutroUsuarioDevolve404ENaoCriaCartao() throws Exception {
		String tokenDona = registrarEObterToken();
		String tokenInvasora = registrarEObterToken();
		String materiaId = criarMateria(tokenDona, "Matéria da dona");

		mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + tokenInvasora)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CartaoBody("pergunta", "resposta"))))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + tokenDona))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void editarComSucessoAtualizaPerguntaERespostaENaoAlteraArquivado() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria");
		String cartaoId = criarCartaoDireto(materiaId, true);

		mockMvc.perform(patch("/api/cartoes/" + cartaoId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CartaoBody("pergunta editada", "resposta editada"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pergunta").value("pergunta editada"))
				.andExpect(jsonPath("$.resposta").value("resposta editada"))
				.andExpect(jsonPath("$.arquivado").value(true));
	}

	@Test
	void editarComCorpoInvalidoDevolve400() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria");
		String cartaoId = criarCartaoDireto(materiaId, false);

		mockMvc.perform(patch("/api/cartoes/" + cartaoId)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CartaoBody("", "resposta"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void editarCartaoDeOutroUsuarioDevolve404ENaoAltera() throws Exception {
		String tokenDona = registrarEObterToken();
		String tokenInvasora = registrarEObterToken();
		String materiaId = criarMateria(tokenDona, "Matéria da dona");
		String cartaoId = criarCartaoDireto(materiaId, false);

		mockMvc.perform(patch("/api/cartoes/" + cartaoId)
				.header("Authorization", "Bearer " + tokenInvasora)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CartaoBody("hackeado", "hackeado"))))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + tokenDona))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].pergunta").value("pergunta"));
	}

	@Test
	void editarComCartaoIdInexistenteDevolve404() throws Exception {
		String token = registrarEObterToken();

		mockMvc.perform(patch("/api/cartoes/" + UUID.randomUUID())
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CartaoBody("pergunta", "resposta"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void arquivarComSucessoDevolve200ComArquivadoTrueENaoAlteraConteudo() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria");
		String cartaoId = criarCartaoDireto(materiaId, false);

		mockMvc.perform(patch("/api/cartoes/" + cartaoId + "/arquivar")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(cartaoId))
				.andExpect(jsonPath("$.arquivado").value(true))
				.andExpect(jsonPath("$.pergunta").value("pergunta"))
				.andExpect(jsonPath("$.resposta").value("resposta"));

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.param("arquivado", "true")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void arquivarCartaoDeOutroUsuarioDevolve404ENaoArquiva() throws Exception {
		String tokenDona = registrarEObterToken();
		String tokenInvasora = registrarEObterToken();
		String materiaId = criarMateria(tokenDona, "Matéria da dona");
		String cartaoId = criarCartaoDireto(materiaId, false);

		mockMvc.perform(patch("/api/cartoes/" + cartaoId + "/arquivar")
				.header("Authorization", "Bearer " + tokenInvasora))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + tokenDona))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void desarquivarComSucessoDevolve200ComArquivadoFalseENaoAlteraConteudo() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria");
		String cartaoId = criarCartaoDireto(materiaId, true);

		mockMvc.perform(patch("/api/cartoes/" + cartaoId + "/desarquivar")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(cartaoId))
				.andExpect(jsonPath("$.arquivado").value(false))
				.andExpect(jsonPath("$.pergunta").value("pergunta"))
				.andExpect(jsonPath("$.resposta").value("resposta"));

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void desarquivarComIdInexistenteDevolve404() throws Exception {
		String token = registrarEObterToken();

		mockMvc.perform(patch("/api/cartoes/" + UUID.randomUUID() + "/desarquivar")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void deletarComSucessoDevolve204ERemoveOCartao() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token, "Matéria");
		String cartaoId = criarCartaoDireto(materiaId, false);

		mockMvc.perform(delete("/api/cartoes/" + cartaoId)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void deletarCartaoDeOutroUsuarioDevolve404ENaoRemove() throws Exception {
		String tokenDona = registrarEObterToken();
		String tokenInvasora = registrarEObterToken();
		String materiaId = criarMateria(tokenDona, "Matéria da dona");
		String cartaoId = criarCartaoDireto(materiaId, false);

		mockMvc.perform(delete("/api/cartoes/" + cartaoId)
				.header("Authorization", "Bearer " + tokenInvasora))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/materias/" + materiaId + "/cartoes")
				.header("Authorization", "Bearer " + tokenDona))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void deletarComIdInexistenteDevolve404() throws Exception {
		String token = registrarEObterToken();

		mockMvc.perform(delete("/api/cartoes/" + UUID.randomUUID())
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	private record RegistroBody(String nome, String email, String senha) {
	}

	private record LoginBody(String email, String senha) {
	}

	private record MateriaBody(String nome) {
	}

	private record CartaoBody(String pergunta, String resposta) {
	}
}
