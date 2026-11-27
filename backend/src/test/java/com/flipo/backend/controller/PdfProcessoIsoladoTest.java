package com.flipo.backend.controller;

import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.UsuarioRepository;
import com.flipo.backend.security.JwtService;
import com.flipo.backend.service.PdfsDeTeste;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os PDFs que esgotam a memória do PDFBox (issue #75) contra o servidor de verdade: a extração roda
 * num processo filho, então só ele morre, e o backend continua respondendo às outras requisições
 * enquanto e depois que eles são processados. O heap do filho nos testes é 128 MB
 * ({@code src/test/resources/config/application.properties}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PdfProcessoIsoladoTest {

	private static final String MENSAGEM_PESADO = "Este PDF é pesado demais para extrair o texto. Cole o texto no campo.";

	@LocalServerPort
	private int porta;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private ObjectMapper objectMapper;

	private final HttpClient cliente = HttpClient.newHttpClient();

	private Usuario usuario;
	private String token;

	@BeforeEach
	void criarUsuario() {
		usuario = usuarioRepository.save(
				new Usuario("Usuária", "pdf-isolado-" + UUID.randomUUID() + "@flipo.test", "hash-qualquer"));
		token = jwtService.gerarToken(usuario);
	}

	@AfterEach
	void limpar() {
		usuarioRepository.deleteById(usuario.getId());
	}

	@Test
	void pdfsQueEsgotamAMemoriaDevolvem400EOBackendContinuaRespondendo() throws Exception {
		byte[] xref = PdfsDeTeste.comTabelaXrefDeclarando(30_000_000);
		byte[] toUnicode = PdfsDeTeste.comToUnicodeDeFaixasEnormes(4);
		byte[] valido = PdfsDeTeste.comPaginas(List.of(List.of("Mitose é a divisão celular.")));

		CompletableFuture<HttpResponse<String>> respostaXref = enviarPdf(xref);
		CompletableFuture<HttpResponse<String>> respostaToUnicode = enviarPdf(toUnicode);
		esperarUmaExtracaoEmAndamento();
		// Enquanto os dois ocupam as vagas de extração, outra rota responde, e um PDF válido espera a
		// vaga e é extraído.
		HttpResponse<String> materias = cliente.send(autenticada("/api/materias").GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(materias.statusCode()).isEqualTo(200);
		CompletableFuture<HttpResponse<String>> respostaValida = enviarPdf(valido);

		assertPesadoDemais(respostaXref.get());
		assertPesadoDemais(respostaToUnicode.get());
		assertTextoExtraido(respostaValida.get());

		assertTextoExtraido(enviarPdf(valido).get());
	}

	/** O servidor roda na JVM do teste, então os processos filhos da extração são filhos dela. */
	private static void esperarUmaExtracaoEmAndamento() throws InterruptedException {
		long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (ProcessHandle.current().children().noneMatch(ProcessHandle::isAlive)) {
			assertThat(System.nanoTime()).as("nenhum processo de extração começou").isLessThan(limite);
			Thread.sleep(5);
		}
	}

	private void assertPesadoDemais(HttpResponse<String> resposta) {
		assertThat(resposta.statusCode()).isEqualTo(400);
		assertThat(objectMapper.readTree(resposta.body()).get("mensagem").asText()).isEqualTo(MENSAGEM_PESADO);
	}

	private void assertTextoExtraido(HttpResponse<String> resposta) {
		assertThat(resposta.statusCode()).isEqualTo(200);
		assertThat(objectMapper.readTree(resposta.body()).get("texto").asText()).isEqualTo("Mitose é a divisão celular.");
	}

	private HttpRequest.Builder autenticada(String caminho) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
				.header("Authorization", "Bearer " + token);
	}

	private CompletableFuture<HttpResponse<String>> enviarPdf(byte[] conteudo) {
		String fronteira = "fronteira-" + UUID.randomUUID();
		ByteArrayOutputStream corpo = new ByteArrayOutputStream();
		corpo.writeBytes(("--" + fronteira + "\r\n"
				+ "Content-Disposition: form-data; name=\"arquivo\"; filename=\"apostila.pdf\"\r\n"
				+ "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		corpo.writeBytes(conteudo);
		corpo.writeBytes(("\r\n--" + fronteira + "--\r\n").getBytes(StandardCharsets.US_ASCII));

		HttpRequest requisicao = autenticada("/api/pdf/extrair-texto")
				.header("Content-Type", "multipart/form-data; boundary=" + fronteira)
				.POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
				.build();
		return cliente.sendAsync(requisicao, HttpResponse.BodyHandlers.ofString());
	}
}
