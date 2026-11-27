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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O limite de 10 MB do {@code POST /api/pdf/extrair-texto} contra o Tomcat de verdade: o MockMvc não
 * passa pela leitura do multipart do servidor, onde o limite é aplicado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PdfLimiteDeTamanhoTest {

	private static final int DEZ_MB = 10 * 1024 * 1024;

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
				new Usuario("Usuária", "pdf-limite-" + UUID.randomUUID() + "@flipo.test", "hash-qualquer"));
		token = jwtService.gerarToken(usuario);
	}

	@AfterEach
	void limpar() {
		usuarioRepository.deleteById(usuario.getId());
	}

	@Test
	void arquivoAcimaDe10MbDevolve413ComMensagem() throws Exception {
		byte[] conteudo = new byte[DEZ_MB + 1];

		HttpResponse<String> resposta = enviar(conteudo);

		assertThat(resposta.statusCode()).isEqualTo(413);
		JsonNode corpo = objectMapper.readTree(resposta.body());
		assertThat(corpo.get("mensagem").asText()).isEqualTo("O arquivo passa do limite de 10 MB.");
	}

	/**
	 * Bem acima do limite, o Tomcat recusa antes de ler o corpo. Com o {@code max-swallow-size}
	 * padrão (2 MB) ele fecharia a conexão com o cliente ainda enviando, e o cliente veria a conexão
	 * cortada em vez do 413 ({@code application.properties}).
	 */
	@Test
	void arquivoBemAcimaDoLimiteAindaChegaAoClienteComo413() throws Exception {
		HttpResponse<String> resposta = enviar(new byte[3 * DEZ_MB]);

		assertThat(resposta.statusCode()).isEqualTo(413);
		assertThat(objectMapper.readTree(resposta.body()).get("mensagem").asText())
				.isEqualTo("O arquivo passa do limite de 10 MB.");
	}

	/** O envelope multipart não conta contra o arquivo: um PDF de quase 10 MB ainda passa. */
	@Test
	void pdfDeQuase10MbEhAceito() throws Exception {
		byte[] pdf = PdfsDeTeste.comTamanho(List.of(List.of("Texto de um PDF grande")), DEZ_MB - 4 * 1024);
		assertThat(pdf.length).isBetween(DEZ_MB - 4 * 1024, DEZ_MB);

		HttpResponse<String> resposta = enviar(pdf);

		assertThat(resposta.statusCode()).isEqualTo(200);
		assertThat(objectMapper.readTree(resposta.body()).get("texto").asText()).isEqualTo("Texto de um PDF grande");
	}

	private HttpResponse<String> enviar(byte[] conteudo) throws Exception {
		String fronteira = "fronteira-" + UUID.randomUUID();
		ByteArrayOutputStream corpo = new ByteArrayOutputStream();
		corpo.write(("--" + fronteira + "\r\n"
				+ "Content-Disposition: form-data; name=\"arquivo\"; filename=\"apostila.pdf\"\r\n"
				+ "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		corpo.write(conteudo);
		corpo.write(("\r\n--" + fronteira + "--\r\n").getBytes(StandardCharsets.US_ASCII));

		HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/pdf/extrair-texto"))
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "multipart/form-data; boundary=" + fronteira)
				.POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
				.build();
		return cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
	}
}
