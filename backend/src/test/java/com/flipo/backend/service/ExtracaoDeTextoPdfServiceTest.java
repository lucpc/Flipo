package com.flipo.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class ExtracaoDeTextoPdfServiceTest {

	private static final String MENSAGEM_CORROMPIDO = "Não foi possível ler este PDF. Ele pode estar corrompido.";

	private static final String MENSAGEM_PESADO = "Este PDF é pesado demais para extrair o texto. Cole o texto no campo.";

	private static final int MB = 1024 * 1024;
	private static final String COMECO_DO_TEXTO = "BT /F1 12 Tf 72 700 Td ";
	private static final String KB_DE_LITERAL = "A".repeat(1024);
	private static final String KB_DE_TJ_VAZIO = "() Tj ".repeat(1024 / 6);

	private final ExtracaoDeTextoPdfService service = new ExtracaoDeTextoPdfService();

	private static MockMultipartFile arquivo(byte[] conteudo) {
		return new MockMultipartFile("arquivo", "apostila.pdf", "application/pdf", conteudo);
	}

	@Test
	void extraiOTextoDeVariasPaginasNaOrdemEContaAsPaginas() {
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(
				List.of("Mitose é a divisão celular.", "Gera duas células iguais."),
				List.of("Meiose gera gametas."),
				List.of("Fim da apostila.")));

		TextoExtraido extraido = service.extrair(arquivo(pdf));

		assertThat(extraido.texto()).isEqualTo(
				"Mitose é a divisão celular.\nGera duas células iguais.\n\nMeiose gera gametas.\n\nFim da apostila.");
		assertThat(extraido.paginas()).isEqualTo(3);
		assertThat(extraido.truncado()).isFalse();
	}

	@Test
	void normalizaEspacosRepetidosEPreservaAsQuebrasDeParagrafo() {
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of(
				"   Título   com    espaços   ",
				"primeira    linha",
				"segunda linha",
				"",
				"",
				"",
				"Outro     parágrafo.   ")));

		TextoExtraido extraido = service.extrair(arquivo(pdf));

		assertThat(extraido.texto()).isEqualTo(
				"Título com espaços\nprimeira linha\nsegunda linha\n\nOutro parágrafo.");
	}

	@ParameterizedTest
	@ValueSource(floats = {1.5f, 2.0f})
	void entrelinhaMaiorNaoViraUmParagrafoPorLinha(float entrelinha) {
		// Com o limiar padrão do PDFBox, o espaçamento 1,5 (ABNT) já abria um parágrafo a cada linha,
		// e o texto vinha com uma linha em branco depois de cada linha do PDF.
		byte[] pdf = PdfsDeTeste.comEntrelinha(List.of(
				List.of("Primeira linha do parágrafo,", "segunda linha do parágrafo,", "terceira linha."),
				List.of("Outra página.")), entrelinha);

		TextoExtraido extraido = service.extrair(arquivo(pdf));

		assertThat(extraido.texto()).isEqualTo(
				"Primeira linha do parágrafo,\nsegunda linha do parágrafo,\nterceira linha.\n\nOutra página.");
	}

	@Test
	void paginaEmBrancoNaoTemTextoSelecionavel() {
		byte[] pdf = PdfsDeTeste.comPaginaEmBranco();

		assertThatThrownBy(() -> service.extrair(arquivo(pdf)))
				.isInstanceOf(PdfSemTextoException.class)
				.hasMessage("Este PDF não tem texto selecionável (pode ser digitalizado). Cole o texto no campo.");
	}

	@Test
	void pdfComSenhaDeUsuarioEhRecusado() {
		byte[] pdf = PdfsDeTeste.comSenhas(List.of(List.of("Conteúdo secreto")), "dono", "leitor");

		assertThatThrownBy(() -> service.extrair(arquivo(pdf)))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage("Este PDF está protegido por senha. Envie uma versão sem senha.");
	}

	@Test
	void pdfComSenhaSoDoDonoAbreSemSenhaEEhLido() {
		byte[] pdf = PdfsDeTeste.comSenhas(List.of(List.of("Conteúdo com restrições")), "dono", "");

		assertThat(service.extrair(arquivo(pdf)).texto()).isEqualTo("Conteúdo com restrições");
	}

	@Test
	void bytesQueNaoSaoPdfSaoRecusadosMesmoComNomeETipoDePdf() {
		byte[] conteudo = "isto é um texto qualquer, não um PDF".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> service.extrair(arquivo(conteudo)))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage("O arquivo enviado não é um PDF.");
	}

	@Test
	void pdfComAssinaturaForaDoComecoNaoEhAceito() {
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of("Texto")));
		byte[] comPrefixo = new byte[pdf.length + 1];
		comPrefixo[0] = ' ';
		System.arraycopy(pdf, 0, comPrefixo, 1, pdf.length);

		assertThatThrownBy(() -> service.extrair(arquivo(comPrefixo)))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage("O arquivo enviado não é um PDF.");
	}

	@Test
	void assinaturaDePdfSeguidaDeLixoEhPdfCorrompido() {
		byte[] conteudo = "%PDF-1.7\nnada de objetos aqui\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

		assertThatThrownBy(() -> service.extrair(arquivo(conteudo)))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_CORROMPIDO);
	}

	@Test
	void pdfTruncadoNoMeioEhCorrompido() {
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of("Primeira"), List.of("Segunda")));

		assertThatThrownBy(() -> service.extrair(arquivo(Arrays.copyOf(pdf, pdf.length / 2))))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_CORROMPIDO);
	}

	/**
	 * Sem fluxos de objetos, o PDFBox recupera as páginas anteriores ao corte e devolveria só parte
	 * do texto; a falta do {@code %%EOF} no fim é o que mostra que o arquivo está incompleto.
	 */
	@Test
	void pdfSemCompressaoTruncadoNoMeioEhCorrompidoEmVezDeDevolverParteDoTexto() {
		byte[] pdf = PdfsDeTeste.semCompressao(List.of(
				List.of("Página 1"), List.of("Página 2"), List.of("Página 3"), List.of("Página 4")));

		for (int decimo = 4; decimo <= 9; decimo++) {
			byte[] truncado = Arrays.copyOf(pdf, pdf.length * decimo / 10);
			assertThatThrownBy(() -> service.extrair(arquivo(truncado)))
					.isInstanceOf(PdfInvalidoException.class)
					.hasMessage(MENSAGEM_CORROMPIDO);
		}
		assertThat(service.extrair(arquivo(pdf)).paginas()).isEqualTo(4);
	}

	@Test
	void arquivoVazioEhRecusado() {
		assertThatThrownBy(() -> service.extrair(arquivo(new byte[0])))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage("O arquivo enviado está vazio.");
	}

	@Test
	void semArquivoEhRecusado() {
		assertThatThrownBy(() -> service.extrair(null))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage("Envie o PDF no campo \"arquivo\".");
	}

	@Test
	void textoAcimaDoLimiteVemCortadoNoTamanhoExatoEMarcadoComoTruncado() {
		// 12 páginas de 100 linhas de 89 caracteres: uns 109 000 caracteres depois de normalizar.
		List<List<String>> paginas = new ArrayList<>();
		for (int p = 0; p < 12; p++) {
			List<String> linhas = new ArrayList<>();
			for (int l = 0; l < 100; l++) {
				linhas.add(String.format("%02d%03d", p, l) + "x".repeat(84));
			}
			paginas.add(linhas);
		}
		String textoCompleto = paginas.stream()
				.map(linhas -> String.join("\n", linhas))
				.collect(Collectors.joining("\n\n"));
		assertThat(textoCompleto.length()).isGreaterThan(ExtracaoDeTextoPdfService.LIMITE_DO_TEXTO);
		// O corte cai numa letra, não numa quebra (que seria tirada do fim).
		assertThat(textoCompleto.charAt(ExtracaoDeTextoPdfService.LIMITE_DO_TEXTO - 1)).isEqualTo('x');

		TextoExtraido extraido = service.extrair(arquivo(PdfsDeTeste.comPaginasMiudas(paginas)));

		assertThat(extraido.truncado()).isTrue();
		assertThat(extraido.texto()).hasSize(100_000);
		assertThat(extraido.texto()).isEqualTo(textoCompleto.substring(0, 100_000));
		assertThat(extraido.paginas()).isEqualTo(12);
	}

	// ---------------------------------------------------------------- PDFs hostis

	/**
	 * Recusado como pesado demais, e rápido: sem os tetos do {@code ExtratorDeTextoLimitado}, estes
	 * PDFs de poucos KB esgotavam a memória (literal de 300 MB) ou levavam de 15 a 18 s de CPU.
	 */
	private void recusadoComoPesadoEmMenosDe2s(byte[] pdf) {
		assertTimeout(Duration.ofSeconds(2), () -> assertThatThrownBy(() -> service.extrair(arquivo(pdf)))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_PESADO));
	}

	@Test
	void literalGiganteComprimidoEhRecusadoSemDescomprimirTudo() {
		byte[] pdf = PdfsDeTeste.comConteudoBruto(1, false, COMECO_DO_TEXTO + "(", KB_DE_LITERAL, 64 * 1024, ") Tj ET");
		assertThat(pdf.length).isLessThan(MB);

		recusadoComoPesadoEmMenosDe2s(pdf);
	}

	/** Dentro do teto de bytes, mas com milhões de glifos numa página. */
	@Test
	void literalDentroDoTetoDeBytesComMilhoesDeGlifosEhRecusado() {
		recusadoComoPesadoEmMenosDe2s(
				PdfsDeTeste.comConteudoBruto(1, false, COMECO_DO_TEXTO + "(", KB_DE_LITERAL, 7 * 1024, ") Tj ET"));
	}

	@Test
	void muitosOperadoresComprimidosSaoRecusados() {
		recusadoComoPesadoEmMenosDe2s(
				PdfsDeTeste.comConteudoBruto(1, false, COMECO_DO_TEXTO + "(oi) Tj ", KB_DE_TJ_VAZIO, 64 * 1024, "ET"));
	}

	/**
	 * Cada página fica abaixo do teto por fluxo; o fluxo repetido em dez páginas passa do total antes de
	 * sair qualquer texto ({@code () Tj} não desenha glifo).
	 */
	@Test
	void fluxoCompartilhadoPorVariasPaginasContaACadaPagina() {
		byte[] pdf = PdfsDeTeste.comConteudoBruto(10, true, COMECO_DO_TEXTO, KB_DE_TJ_VAZIO, 5 * 1024, "ET");
		assertThat(pdf.length).isLessThan(MB);

		recusadoComoPesadoEmMenosDe2s(pdf);
	}

	@Test
	void pilhaGraficaProfundaDemaisEhRecusada() {
		recusadoComoPesadoEmMenosDe2s(
				PdfsDeTeste.comConteudoBruto(1, false, "", "q ", 2_000_000, COMECO_DO_TEXTO + "(oi) Tj ET"));
	}

	@Test
	void extracaoQuePassaDoPrazoEhRecusada() {
		ExtracaoDeTextoPdfService semPrazo =
				new ExtracaoDeTextoPdfService(Duration.ZERO, new Semaphore(1), Duration.ZERO);
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of("Texto qualquer")));

		assertThatThrownBy(() -> semPrazo.extrair(arquivo(pdf)))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_PESADO);
	}

	/** Quatro páginas de uns 7,5 MB de desenho cada: perto dos tetos por fluxo e somado, sem passar. */
	@Test
	void pdfLegitimoComConteudoPertoDosTetosContinuaPassando() {
		long repeticoes = (long) (7.5 * MB) / "72 72 m 144 144 l S\n".length();
		byte[] pdf = PdfsDeTeste.comConteudoBruto(4, false,
				COMECO_DO_TEXTO + "(Texto legitimo) Tj ET\n", "72 72 m 144 144 l S\n", repeticoes, "");

		TextoExtraido extraido = assertTimeout(Duration.ofSeconds(10), () -> service.extrair(arquivo(pdf)));

		assertThat(extraido.texto()).isEqualTo(String.join("\n\n", List.of(
				"Texto legitimo", "Texto legitimo", "Texto legitimo", "Texto legitimo")));
		assertThat(extraido.paginas()).isEqualTo(4);
	}

	/** Um PDF longo que passa do teto somado depois das primeiras páginas entrega o texto já lido. */
	@Test
	void tetoSomadoDepoisDeJaHaverTextoDevolveOTextoLidoComoTruncado() {
		byte[] pdf = PdfsDeTeste.comTextoSeguidoDeConteudoBruto(List.of("Primeira página", "Segunda página"),
				10, true, COMECO_DO_TEXTO, KB_DE_TJ_VAZIO, 5 * 1024, "ET");

		TextoExtraido extraido = assertTimeout(Duration.ofSeconds(2), () -> service.extrair(arquivo(pdf)));

		assertThat(extraido.texto()).isEqualTo("Primeira página\n\nSegunda página");
		assertThat(extraido.truncado()).isTrue();
		assertThat(extraido.paginas()).isEqualTo(12);
	}

	/** Conteúdo montado para pesar continua 400 mesmo com texto antes: não é só um PDF longo. */
	@Test
	void fluxoHostilDepoisDeJaHaverTextoContinuaRecusado() {
		recusadoComoPesadoEmMenosDe2s(PdfsDeTeste.comTextoSeguidoDeConteudoBruto(List.of("Primeira página"),
				1, false, COMECO_DO_TEXTO, KB_DE_TJ_VAZIO, 64 * 1024, "ET"));
	}

	/** Decodificar este JPEG, mesmo só para contar, pediria gigabytes. */
	@Test
	void conteudoComFiltroDeImagemEhRecusadoSemDecodificar() {
		recusadoComoPesadoEmMenosDe2s(PdfsDeTeste.comConteudoEmDct());
	}

	@Test
	void fonteTrueTypeEmbutidaEnormeEhRecusadaAntesDeCarregar() {
		byte[] pdf = PdfsDeTeste.comArquivoDeFonteDeZeros(COSName.TRUE_TYPE, COSName.FONT_FILE2, 64L * MB);
		assertThat(pdf.length).isLessThan(MB);

		recusadoComoPesadoEmMenosDe2s(pdf);
	}

	@Test
	void fonteCffEmbutidaEnormeEhRecusadaAntesDeCarregar() {
		recusadoComoPesadoEmMenosDe2s(PdfsDeTeste.comArquivoDeFonteDeZeros(COSName.TYPE1, COSName.FONT_FILE3, 64L * MB));
	}

	/** O formato mais comum de PDF real com Unicode: a descendente chega por referência indireta. */
	@Test
	void fonteType0ComDescendenteIndiretaEnormeEhRecusadaAntesDeCarregar() throws Exception {
		byte[] pdf = PdfsDeTeste.comType0DeArquivoDeZeros(64L * MB);
		try (PDDocument documento = Loader.loadPDF(pdf)) {
			COSDictionary fontes = documento.getPage(0).getResources().getCOSObject().getCOSDictionary(COSName.FONT);
			COSArray descendentes = fontes.getCOSDictionary(COSName.getPDFName("F1")).getCOSArray(COSName.DESCENDANT_FONTS);
			assertThat(descendentes.get(0)).isInstanceOf(COSObject.class);
		}

		recusadoComoPesadoEmMenosDe2s(pdf);
	}

	@Test
	void pdfComFonteTrueTypeRealEmbutidaContinuaPassando() {
		byte[] pdf = PdfsDeTeste.comFonteTrueTypeEmbutida("Texto em Liberation Sans, com acentuação.");

		assertThat(service.extrair(arquivo(pdf)).texto()).isEqualTo("Texto em Liberation Sans, com acentuação.");
	}

	@Test
	void semVagaParaExtrairNoPrazoDeEsperaDevolveOcupado() {
		ExtracaoDeTextoPdfService semVagas =
				new ExtracaoDeTextoPdfService(Duration.ofSeconds(10), new Semaphore(0), Duration.ofMillis(50));

		assertThatThrownBy(() -> semVagas.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto"))))))
				.isInstanceOf(ExtracaoOcupadaException.class)
				.hasMessage("O servidor está ocupado lendo outros PDFs. Tente de novo em instantes.");
	}

	/** A vaga volta ao fim de cada extração, com sucesso ou com erro. */
	@Test
	void aVagaEhDevolvidaDepoisDeCadaExtracao() {
		Semaphore vagas = new Semaphore(1);
		ExtracaoDeTextoPdfService umaVaga = new ExtracaoDeTextoPdfService(Duration.ofSeconds(10), vagas, Duration.ZERO);

		umaVaga.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto")))));
		assertThatThrownBy(() -> umaVaga.extrair(arquivo(PdfsDeTeste.comPaginaEmBranco())))
				.isInstanceOf(PdfSemTextoException.class);
		assertThatThrownBy(() -> umaVaga.extrair(arquivo(PdfsDeTeste.comConteudoEmDct())))
				.isInstanceOf(PdfInvalidoException.class);

		assertThat(vagas.availablePermits()).isEqualTo(1);
	}
}
