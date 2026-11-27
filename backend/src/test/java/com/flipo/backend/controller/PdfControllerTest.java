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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/pdf/extrair-texto} pelo MockMvc, contra o Postgres real. O detalhe da extração
 * está no {@code ExtracaoDeTextoPdfServiceTest}; aqui ficam o formato da resposta, os códigos HTTP
 * e a autenticação. O limite de 10 MB depende do Tomcat e fica no {@link PdfLimiteDeTamanhoTest}.
 *
 * <p>Sem {@code @Transactional}, como o {@code GerarIaELoteControllerTest}: assim a contagem de
 * linhas mostra qualquer escrita de verdade. O usuário criado é apagado no {@link #limpar()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PdfControllerTest {

	private static final String URL = "/api/pdf/extrair-texto";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JwtService jwtService;

	private Usuario usuario;
	private String token;

	@BeforeEach
	void criarUsuario() {
		usuario = usuarioRepository.save(
				new Usuario("Usuária", "pdf-" + UUID.randomUUID() + "@flipo.test", "hash-qualquer"));
		token = jwtService.gerarToken(usuario);
	}

	@AfterEach
	void limpar() {
		usuarioRepository.deleteById(usuario.getId());
	}

	private ResultActions enviar(MockMultipartFile arquivo) throws Exception {
		return mockMvc.perform(multipart(URL).file(arquivo).header("Authorization", "Bearer " + token));
	}

	private static MockMultipartFile arquivo(byte[] conteudo) {
		return new MockMultipartFile("arquivo", "apostila.pdf", "application/pdf", conteudo);
	}

	@Test
	void devolveTextoPaginasETruncadoESoIsso() throws Exception {
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of("Mitose é a divisão celular."), List.of("Meiose gera gametas.")));

		enviar(arquivo(pdf))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.texto").value("Mitose é a divisão celular.\n\nMeiose gera gametas."))
				.andExpect(jsonPath("$.paginas").value(2))
				.andExpect(jsonPath("$.truncado").value(false))
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void reconheceOPdfPeloConteudoENaoPeloNomeOuTipo() throws Exception {
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of("Texto do PDF")));

		enviar(new MockMultipartFile("arquivo", "notas.txt", "text/plain", pdf))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.texto").value("Texto do PDF"));
	}

	@Test
	void naoGravaNadaNoBanco() throws Exception {
		Map<String, Object> antes = contarLinhas();

		enviar(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto qualquer.")))))
				.andExpect(status().isOk());
		enviar(arquivo(PdfsDeTeste.comPaginaEmBranco()))
				.andExpect(status().isUnprocessableContent());

		assertThat(contarLinhas()).isEqualTo(antes);
	}

	@Test
	void pdfSoComPaginaEmBrancoDevolve422() throws Exception {
		enviar(arquivo(PdfsDeTeste.comPaginaEmBranco()))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.mensagem")
						.value("Este PDF não tem texto selecionável (pode ser digitalizado). Cole o texto no campo."));
	}

	@Test
	void pdfComSenhaDevolve400() throws Exception {
		enviar(arquivo(PdfsDeTeste.comSenhas(List.of(List.of("Secreto")), "dono", "leitor")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagem").value("Este PDF está protegido por senha. Envie uma versão sem senha."));
	}

	@Test
	void bytesQueNaoSaoPdfComNomeETipoDePdfDevolvem400() throws Exception {
		enviar(arquivo("não sou um PDF".getBytes(StandardCharsets.UTF_8)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagem").value("O arquivo enviado não é um PDF."));
	}

	@Test
	void pdfTruncadoDevolve400SemDetalheDoPdfBox() throws Exception {
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of("Primeira"), List.of("Segunda")));

		enviar(arquivo(Arrays.copyOf(pdf, pdf.length / 2)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagem").value("Não foi possível ler este PDF. Ele pode estar corrompido."))
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void pdfQueDescomprimeParaUmVolumeEnormeDevolve400() throws Exception {
		byte[] pdf = PdfsDeTeste.comConteudoBruto(
				1, false, "BT /F1 12 Tf 72 700 Td (", "A".repeat(1024), 64 * 1024, ") Tj ET");

		enviar(arquivo(pdf))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagem")
						.value("Este PDF é pesado demais para extrair o texto. Cole o texto no campo."));
	}

	@Test
	void arquivoVazioDevolve400() throws Exception {
		enviar(arquivo(new byte[0]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagem").value("O arquivo enviado está vazio."));
	}

	@Test
	void multipartSemOCampoArquivoDevolve400() throws Exception {
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of("Texto")));

		enviar(new MockMultipartFile("documento", "apostila.pdf", "application/pdf", pdf))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagem").value("Envie o PDF no campo \"arquivo\"."));
	}

	@Test
	void requisicaoQueNaoEhMultipartDevolve400() throws Exception {
		mockMvc.perform(post(URL)
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"arquivo\": \"JVBERi0=\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagem").value("Envie o PDF no campo \"arquivo\"."));
	}

	@Test
	void semTokenDevolve401() throws Exception {
		mockMvc.perform(multipart(URL).file(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto"))))))
				.andExpect(status().isUnauthorized());
	}

	private Map<String, Object> contarLinhas() {
		return jdbcTemplate.queryForMap("SELECT "
				+ "(SELECT count(*) FROM usuarios) AS usuarios, "
				+ "(SELECT count(*) FROM materias) AS materias, "
				+ "(SELECT count(*) FROM cartoes) AS cartoes, "
				+ "(SELECT count(*) FROM chaves_api) AS chaves_api");
	}
}
