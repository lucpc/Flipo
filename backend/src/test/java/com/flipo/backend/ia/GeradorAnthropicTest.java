package com.flipo.backend.ia;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link GeradorAnthropic} contra um servidor HTTP local que imita a Messages API com respostas
 * enlatadas: nenhum teste sai para a rede nem precisa de chave real. Cada teste configura o que o
 * servidor responde e lê a requisição que ele recebeu.
 */
class GeradorAnthropicTest {

	private static final String CHAVE = "sk-ant-teste-chave-que-nao-pode-vazar";
	private static final String MODELO = "modelo-configurado-para-teste";
	private static final String TEXTO = "A mitocôndria é a organela responsável pela respiração celular.";

	private final ObjectMapper json = JsonMapper.builder().build();

	private HttpServer servidor;
	private GeradorAnthropic gerador;

	private final AtomicReference<Requisicao> recebida = new AtomicReference<>();
	private volatile int statusDaResposta;
	private volatile String corpoDaResposta;

	record Requisicao(String metodo, String caminho, String autorizacao, String xApiKey, JsonNode corpo) {
	}

	@BeforeEach
	void subirServidor() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		servidor.createContext("/", troca -> {
			byte[] corpo = troca.getRequestBody().readAllBytes();
			recebida.set(new Requisicao(
					troca.getRequestMethod(),
					troca.getRequestURI().getPath(),
					troca.getRequestHeaders().getFirst("Authorization"),
					troca.getRequestHeaders().getFirst("X-Api-Key"),
					json.readTree(corpo)));
			byte[] resposta = corpoDaResposta.getBytes(StandardCharsets.UTF_8);
			troca.getResponseHeaders().add("Content-Type", "application/json");
			troca.sendResponseHeaders(statusDaResposta, resposta.length);
			try (OutputStream saida = troca.getResponseBody()) {
				saida.write(resposta);
			}
		});
		servidor.start();

		gerador = new GeradorAnthropic(AnthropicOkHttpClient.builder()
				.apiKey(CHAVE)
				.baseUrl("http://" + servidor.getAddress().getHostString() + ":" + servidor.getAddress().getPort())
				.maxRetries(0)
				.build(), MODELO);
	}

	@AfterEach
	void derrubarServidor() {
		gerador.close();
		servidor.stop(0);
	}

	@Test
	void mapeiaASaidaEstruturadaParaCartoesSugeridos() {
		responderComTexto("end_turn", """
				{"cartoes": [
				  {"pergunta": "Qual organela faz a respiração celular?", "resposta": "A mitocôndria."},
				  {"pergunta": "O que a mitocôndria faz?", "resposta": "A respiração celular."}
				]}""");

		List<CartaoSugerido> cartoes = gerador.gerar(TEXTO, 2, NivelCartao.DEFINICAO);

		assertThat(cartoes).containsExactly(
				new CartaoSugerido("Qual organela faz a respiração celular?", "A mitocôndria."),
				new CartaoSugerido("O que a mitocôndria faz?", "A respiração celular."));
	}

	@Test
	void requisicaoLevaModeloChaveSchemaTextoQuantidadeENivel() {
		responderComTexto("end_turn", "{\"cartoes\": [{\"pergunta\": \"P\", \"resposta\": \"R\"}]}");

		gerador.gerar(TEXTO, 7, NivelCartao.DEFINICAO);

		Requisicao requisicao = recebida.get();
		assertThat(requisicao.metodo()).isEqualTo("POST");
		assertThat(requisicao.caminho()).isEqualTo("/v1/messages");
		// O SDK 3.14 manda a chave como "Authorization: Bearer"; versões antigas usavam x-api-key.
		// Qualquer um dos dois serve: o teste só exige que a chave configurada vá na requisição.
		assertThat(List.of(String.valueOf(requisicao.autorizacao()), String.valueOf(requisicao.xApiKey())))
				.containsAnyOf("Bearer " + CHAVE, CHAVE);

		JsonNode corpo = requisicao.corpo();
		assertThat(corpo.get("model").asString()).isEqualTo(MODELO);
		assertThat(corpo.get("max_tokens").asLong()).isEqualTo(GeradorAnthropic.MAX_TOKENS);
		assertThat(textoDe(corpo.get("system"))).isEqualTo(GeradorAnthropic.INSTRUCOES);

		JsonNode formato = corpo.get("output_config").get("format");
		assertThat(formato.get("type").asString()).isEqualTo("json_schema");
		JsonNode cartao = formato.get("schema").get("properties").get("cartoes").get("items");
		assertThat(cartao.get("properties").propertyNames()).containsExactlyInAnyOrder("pergunta", "resposta");

		JsonNode mensagens = corpo.get("messages");
		assertThat(mensagens).hasSize(1);
		assertThat(mensagens.get(0).get("role").asString()).isEqualTo("user");
		String mensagem = textoDe(mensagens.get(0).get("content"));
		assertThat(mensagem)
				.contains("<texto>\n" + TEXTO + "\n</texto>")
				.contains("Quantidade de cartões: 7")
				.contains("Nível dos cartões: definição");

		// Parâmetros que o modelo rejeita ou que o pedido não deve mandar.
		assertThat(corpo.has("temperature")).isFalse();
		assertThat(corpo.has("top_p")).isFalse();
		assertThat(corpo.has("top_k")).isFalse();
		assertThat(corpo.has("thinking")).isFalse();
	}

	@Test
	void nivelAplicacaoVaiDescritoNaMensagemDoUsuario() {
		responderComTexto("end_turn", "{\"cartoes\": [{\"pergunta\": \"P\", \"resposta\": \"R\"}]}");

		gerador.gerar(TEXTO, 3, NivelCartao.APLICACAO);

		String mensagem = textoDe(recebida.get().corpo().get("messages").get(0).get("content"));
		assertThat(mensagem).contains("Nível dos cartões: aplicação").doesNotContain("definição");
		// O system não muda com o nível (prefixo estável).
		assertThat(textoDe(recebida.get().corpo().get("system"))).isEqualTo(GeradorAnthropic.INSTRUCOES);
	}

	@Test
	void recusaDoModeloLancaExcecao() {
		responderComTexto("refusal", "{\"cartoes\": [{\"pergunta\": \"P\", \"resposta\": \"R\"}]}");

		assertFalhaSemVazarDados("refusal");
	}

	@Test
	void corteEmMaxTokensLancaExcecao() {
		responderComTexto("max_tokens", "{\"cartoes\": [{\"pergunta\": \"P\", \"resp");

		assertFalhaSemVazarDados("max_tokens");
	}

	@Test
	void textoQueNaoEJsonDoFormatoPedidoLancaExcecao() {
		responderComTexto("end_turn", "Aqui estão seus cartões: " + TEXTO);

		assertFalhaSemVazarDados("formato");
	}

	@Test
	void respostaComListaDeCartoesNulaLancaExcecao() {
		responderComTexto("end_turn", "{\"cartoes\": null}");

		assertFalhaSemVazarDados("sem a lista");
	}

	@Test
	void erroHttpDaApiLancaExcecaoSemRepassarOCorpo() {
		// Corpo que ecoa a chave e o texto: a exceção do gerador não pode repassá-lo.
		statusDaResposta = 500;
		corpoDaResposta = """
				{"type": "error", "error": {"type": "api_error", "message": "%s %s"}}"""
				.formatted(CHAVE, TEXTO);

		assertThatThrownBy(() -> gerador.gerar(TEXTO, 2, NivelCartao.DEFINICAO))
				.isInstanceOf(GeradorAnthropic.FalhaNoGeradorAnthropicException.class)
				.hasMessageContaining("500")
				.hasNoCause()
				.message().doesNotContain(CHAVE).doesNotContain(TEXTO);
	}

	@Test
	void falhaDeConexaoLancaExcecao() {
		servidor.stop(0);

		assertThatThrownBy(() -> gerador.gerar(TEXTO, 2, NivelCartao.DEFINICAO))
				.isInstanceOf(GeradorAnthropic.FalhaNoGeradorAnthropicException.class)
				.hasMessageContaining("rede")
				.hasNoCause()
				.message().doesNotContain(CHAVE).doesNotContain(TEXTO);
	}

	@Test
	void toStringDoGeradorNaoMostraAChave() {
		assertThat(gerador.toString()).doesNotContain(CHAVE);
	}

	private void assertFalhaSemVazarDados(String trechoDaMensagem) {
		assertThatThrownBy(() -> gerador.gerar(TEXTO, 2, NivelCartao.DEFINICAO))
				.isInstanceOf(GeradorAnthropic.FalhaNoGeradorAnthropicException.class)
				.hasMessageContaining(trechoDaMensagem)
				.hasNoCause()
				.message().doesNotContain(CHAVE).doesNotContain(TEXTO);
	}

	/**
	 * Resposta 200 da Messages API com um bloco de raciocínio omitido (como o Haiku 5.5 devolve, com
	 * raciocínio adaptativo ligado por padrão) seguido do bloco de texto.
	 */
	private void responderComTexto(String stopReason, String texto) {
		ObjectNode resposta = json.createObjectNode()
				.put("id", "msg_teste")
				.put("type", "message")
				.put("role", "assistant")
				.put("model", MODELO)
				.put("stop_reason", stopReason)
				.putNull("stop_sequence");
		resposta.putArray("content")
				.add(json.createObjectNode().put("type", "thinking").put("thinking", "").put("signature", "sig"))
				.add(json.createObjectNode().put("type", "text").put("text", texto));
		resposta.putObject("usage").put("input_tokens", 10).put("output_tokens", 20);
		statusDaResposta = 200;
		corpoDaResposta = resposta.toString();
	}

	/** {@code system} e {@code content} podem ir como string ou como lista de blocos de texto. */
	private static String textoDe(JsonNode campo) {
		if (campo.isString()) {
			return campo.asString();
		}
		StringBuilder texto = new StringBuilder();
		campo.forEach(bloco -> texto.append(bloco.get("text").asString()));
		return texto.toString();
	}
}
