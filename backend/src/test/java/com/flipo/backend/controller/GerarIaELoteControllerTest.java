package com.flipo.backend.controller;

import com.flipo.backend.ia.CartaoSugerido;
import com.flipo.backend.ia.GeradorDeCartoesFake;
import com.flipo.backend.ia.NivelCartao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST .../cartoes/gerar-ia} e {@code POST .../cartoes/lote} de ponta a ponta contra o
 * Postgres real, com o {@link GeradorDeCartoesFake} no lugar de um provedor de IA.
 *
 * <p>De propósito <b>sem</b> {@code @Transactional}, ao contrário dos outros testes de controller:
 * cada requisição faz commit de verdade, então a contagem de linhas mostra qualquer escrita do
 * {@code gerar-ia}, e um {@code lote} que falha no meio só deixa o banco limpo se o rollback for
 * real. Os usuários criados aqui são apagados no {@link #limpar()} (matérias e cartões vão em
 * cascata).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(GeradorDeCartoesFake.class)
class GerarIaELoteControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private GeradorDeCartoesFake gerador;

	private final List<String> emailsCriados = new ArrayList<>();

	@BeforeEach
	void reiniciarGerador() {
		gerador.reiniciar();
	}

	@AfterEach
	void limpar() {
		for (String email : emailsCriados) {
			jdbcTemplate.update("DELETE FROM usuarios WHERE email = ?", email);
		}
	}

	// ---------------------------------------------------------------- gerar-ia

	@Test
	void gerarIaDevolveAsSugestoesDoGeradorERepassaTextoQuantidadeENivel() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);
		gerador.devolver(List.of(
				new CartaoSugerido("O que é mitose?", "Divisão celular que gera duas células iguais."),
				new CartaoSugerido("O que é meiose?", "Divisão celular que gera gametas.")));

		gerarIa(token, materiaId, Map.of("texto", "Texto sobre divisão celular.", "quantidade", 2, "nivel", "aplicacao"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].pergunta").value("O que é mitose?"))
				.andExpect(jsonPath("$[0].resposta").value("Divisão celular que gera duas células iguais."))
				.andExpect(jsonPath("$[1].pergunta").value("O que é meiose?"))
				// só pergunta e resposta: nada de id, origem ou arquivado, porque nada foi salvo
				.andExpect(jsonPath("$[0].id").doesNotExist());

		assertThat(gerador.chamadas()).containsExactly(
				new GeradorDeCartoesFake.Chamada("Texto sobre divisão celular.", 2, NivelCartao.APLICACAO));
	}

	@Test
	void gerarIaSemQuantidadeENivelUsaDezEDefinicao() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);

		mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes/gerar-ia")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\": \"Texto qualquer.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(10));

		assertThat(gerador.chamadas()).containsExactly(
				new GeradorDeCartoesFake.Chamada("Texto qualquer.", 10, NivelCartao.DEFINICAO));
	}

	@Test
	void gerarIaNaoGravaNadaNoBanco() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);
		Map<String, Object> antes = contarLinhas();

		gerarIa(token, materiaId, Map.of("texto", "Texto qualquer.", "quantidade", 5))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(5));

		assertThat(contarLinhas()).isEqualTo(antes);
	}

	@Test
	void gerarIaComFalhaDoGeradorDevolve502GenericoSemVazarDetalheENaoGravaNada() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);
		gerador.falharCom(new IllegalStateException("detalhe interno sk-ant-segredo"));
		Map<String, Object> antes = contarLinhas();

		gerarIa(token, materiaId, Map.of("texto", "Texto qualquer."))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.mensagem").value("Não foi possível gerar sugestões agora. Tente novamente."))
				.andExpect(jsonPath("$.mensagem", not(containsString("segredo"))));

		assertThat(contarLinhas()).isEqualTo(antes);
	}

	@Test
	void gerarIaComSaidaMalformadaDoGeradorDevolve502ENaoGravaNada() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);
		Map<String, Object> antes = contarLinhas();

		for (List<CartaoSugerido> saida : List.of(
				List.<CartaoSugerido>of(),
				Arrays.asList(new CartaoSugerido("pergunta", "resposta"), null),
				List.of(new CartaoSugerido("pergunta", "  ")),
				List.of(new CartaoSugerido(null, "resposta")))) {
			gerador.devolver(saida);
			gerarIa(token, materiaId, Map.of("texto", "Texto qualquer."))
					.andExpect(status().isBadGateway())
					.andExpect(jsonPath("$.mensagem").value("Não foi possível gerar sugestões agora. Tente novamente."));
		}

		assertThat(contarLinhas()).isEqualTo(antes);
	}

	@Test
	void gerarIaComCorpoInvalidoDevolve400SemChamarOGerador() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);
		String textoLongoDemais = "a".repeat(20_001);

		for (String corpo : List.of(
				"{}",
				"{\"texto\": \"   \"}",
				"{\"texto\": \"" + textoLongoDemais + "\"}",
				"{\"texto\": \"ok\", \"quantidade\": 0}",
				"{\"texto\": \"ok\", \"quantidade\": 21}",
				"{\"texto\": \"ok\", \"nivel\": \"dificil\"}",
				"{\"texto\": \"ok\", \"nivel\": \"DEFINICAO\"}",
				"{\"texto\": ")) {
			mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes/gerar-ia")
					.header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON)
					.content(corpo))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.mensagem").isNotEmpty());
		}

		assertThat(gerador.chamadas()).isEmpty();
	}

	@Test
	void gerarIaNosLimitesAceitaQuantidadeUmEVinteETextoNoTamanhoMaximo() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);

		gerarIa(token, materiaId, Map.of("texto", "a".repeat(20_000), "quantidade", 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		gerarIa(token, materiaId, Map.of("texto", "ok", "quantidade", 20))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(20));
	}

	@Test
	void gerarIaEmMateriaDeOutroUsuarioDevolve404SemChamarOGerador() throws Exception {
		String tokenDono = registrarEObterToken();
		String materiaDoDono = criarMateria(tokenDono);
		String tokenOutro = registrarEObterToken();

		gerarIa(tokenOutro, materiaDoDono, Map.of("texto", "Texto qualquer."))
				.andExpect(status().isNotFound());
		gerarIa(tokenOutro, UUID.randomUUID().toString(), Map.of("texto", "Texto qualquer."))
				.andExpect(status().isNotFound());

		assertThat(gerador.chamadas()).isEmpty();
	}

	@Test
	void gerarIaSemTokenDevolve401() throws Exception {
		mockMvc.perform(post("/api/materias/" + UUID.randomUUID() + "/cartoes/gerar-ia")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\": \"Texto qualquer.\"}"))
				.andExpect(status().isUnauthorized());

		assertThat(gerador.chamadas()).isEmpty();
	}

	// ---------------------------------------------------------------- lote

	@Test
	void loteGravaTodosOsCartoesComoIaEAtivosNaOrdemRecebida() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);

		lote(token, materiaId, List.of(
				Map.of("pergunta", "P1", "resposta", "R1"),
				Map.of("pergunta", "P2", "resposta", "R2"),
				Map.of("pergunta", "P3", "resposta", "R3")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").isNotEmpty())
				.andExpect(jsonPath("$[0].pergunta").value("P1"))
				.andExpect(jsonPath("$[1].pergunta").value("P2"))
				.andExpect(jsonPath("$[2].pergunta").value("P3"))
				.andExpect(jsonPath("$[2].resposta").value("R3"))
				.andExpect(jsonPath("$[*].origem").value(everyItem(is("ia"))))
				.andExpect(jsonPath("$[*].arquivado").value(everyItem(is(false))));

		assertThat(cartoesNoBanco(materiaId))
				.extracting(l -> l.get("pergunta"), l -> l.get("resposta"), l -> l.get("origem"), l -> l.get("arquivado"))
				.containsExactlyInAnyOrder(
						tuple("P1", "R1", "ia", false),
						tuple("P2", "R2", "ia", false),
						tuple("P3", "R3", "ia", false));
	}

	@Test
	void loteIgnoraOrigemEnviadaPeloClienteEGravaSempreIa() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);

		lote(token, materiaId, List.of(
				Map.of("pergunta", "P1", "resposta", "R1", "origem", "manual", "arquivado", true)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$[0].origem").value("ia"))
				.andExpect(jsonPath("$[0].arquivado").value(false));

		assertThat(cartoesNoBanco(materiaId))
				.extracting(l -> l.get("origem"), l -> l.get("arquivado"))
				.containsExactly(tuple("ia", false));
	}

	@Test
	void loteComUmCartaoInvalidoDevolve400ENaoGravaNenhum() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);

		lote(token, materiaId, List.of(
				Map.of("pergunta", "P1", "resposta", "R1"),
				Map.of("pergunta", "P2", "resposta", " "),
				Map.of("pergunta", "P3", "resposta", "R3")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagem", containsString("cartoes[1].resposta")));

		assertThat(cartoesNoBanco(materiaId)).isEmpty();
	}

	@Test
	void loteComListaVaziaAusenteComItemNuloOuAcimaDoMaximoDevolve400() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);
		String vinteEUm = objectMapper.writeValueAsString(Map.of("cartoes", IntStream.rangeClosed(1, 21)
				.mapToObj(i -> Map.of("pergunta", "P" + i, "resposta", "R" + i))
				.toList()));

		for (String corpo : List.of(
				"{\"cartoes\": []}",
				"{}",
				"{\"cartoes\": [null]}",
				"{\"cartoes\": [{\"pergunta\": \"P1\"}]}",
				vinteEUm)) {
			mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes/lote")
					.header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON)
					.content(corpo))
					.andExpect(status().isBadRequest());
		}

		assertThat(cartoesNoBanco(materiaId)).isEmpty();
	}

	@Test
	void loteNoLimiteGravaVinteCartoes() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);

		lote(token, materiaId, IntStream.rangeClosed(1, 20)
				.mapToObj(i -> Map.<String, Object>of("pergunta", "P" + i, "resposta", "R" + i))
				.toList())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.length()").value(20));

		assertThat(cartoesNoBanco(materiaId)).hasSize(20);
	}

	/**
	 * Falha de banco no meio do lote: o terceiro cartão passa na validação, mas o Postgres recusa
	 * o caractere NUL ({@code \u0000}) em colunas {@code text}. Os dois primeiros já foram
	 * inseridos na mesma transação; se ela não for desfeita por inteiro, eles ficam no banco. Sem
	 * handler para esse erro, o MockMvc relança a exceção em vez de devolver 500.
	 */
	@Test
	void loteComFalhaDoBancoNoMeioDesfazTudo() throws Exception {
		String token = registrarEObterToken();
		String materiaId = criarMateria(token);

		assertThatThrownBy(() -> lote(token, materiaId, List.of(
				Map.of("pergunta", "P1", "resposta", "R1"),
				Map.of("pergunta", "P2", "resposta", "R2"),
				Map.of("pergunta", "P3", "resposta", "com \u0000 no meio"))))
				.hasCauseInstanceOf(DataAccessException.class);

		assertThat(cartoesNoBanco(materiaId)).isEmpty();
	}

	@Test
	void loteEmMateriaDeOutroUsuarioDevolve404ENaoGrava() throws Exception {
		String tokenDono = registrarEObterToken();
		String materiaDoDono = criarMateria(tokenDono);
		String tokenOutro = registrarEObterToken();
		Map<String, Object> antes = contarLinhas();

		lote(tokenOutro, materiaDoDono, List.of(Map.of("pergunta", "P1", "resposta", "R1")))
				.andExpect(status().isNotFound());
		lote(tokenOutro, UUID.randomUUID().toString(), List.of(Map.of("pergunta", "P1", "resposta", "R1")))
				.andExpect(status().isNotFound());

		assertThat(contarLinhas()).isEqualTo(antes);
		assertThat(cartoesNoBanco(materiaDoDono)).isEmpty();
	}

	@Test
	void loteSemTokenDevolve401() throws Exception {
		mockMvc.perform(post("/api/materias/" + UUID.randomUUID() + "/cartoes/lote")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cartoes\": [{\"pergunta\": \"P1\", \"resposta\": \"R1\"}]}"))
				.andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------- apoio

	private ResultActions gerarIa(String token, String materiaId, Map<String, Object> corpo) throws Exception {
		return mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes/gerar-ia")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(corpo)));
	}

	private ResultActions lote(String token, String materiaId, List<Map<String, Object>> cartoes) throws Exception {
		return mockMvc.perform(post("/api/materias/" + materiaId + "/cartoes/lote")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("cartoes", cartoes))));
	}

	/** Total de linhas de cada tabela da aplicação, lido direto do banco. */
	private Map<String, Object> contarLinhas() {
		return jdbcTemplate.queryForMap("SELECT "
				+ "(SELECT count(*) FROM usuarios) AS usuarios, "
				+ "(SELECT count(*) FROM materias) AS materias, "
				+ "(SELECT count(*) FROM cartoes) AS cartoes, "
				+ "(SELECT count(*) FROM chaves_api) AS chaves_api");
	}

	private List<Map<String, Object>> cartoesNoBanco(String materiaId) {
		return jdbcTemplate.queryForList(
				"SELECT pergunta, resposta, origem, arquivado FROM cartoes WHERE materia_id = ?",
				UUID.fromString(materiaId));
	}

	private String registrarEObterToken() throws Exception {
		String email = "ia-" + UUID.randomUUID() + "@flipo.test";
		emailsCriados.add(email);
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
		JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
		return corpo.get("token").asText();
	}

	private String criarMateria(String token) throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/materias")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("nome", "Biologia"))))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
		return corpo.get("id").asText();
	}
}
