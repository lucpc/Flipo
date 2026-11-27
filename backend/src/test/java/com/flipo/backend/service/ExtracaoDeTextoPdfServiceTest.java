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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExtracaoDeTextoPdfServiceTest {

	private static final String MENSAGEM_CORROMPIDO = "Não foi possível ler este PDF. Ele pode estar corrompido.";

	private static final String MENSAGEM_PESADO = "Este PDF é pesado demais para extrair o texto. Cole o texto no campo.";

	private static final int MB = 1024 * 1024;
	private static final String COMECO_DO_TEXTO = "BT /F1 12 Tf 72 700 Td ";
	private static final String KB_DE_LITERAL = "A".repeat(1024);
	private static final String KB_DE_TJ_VAZIO = "() Tj ".repeat(1024 / 6);

	private static final String FILHO = ProcessoDeExtracaoPdf.class.getName();

	private final ExtracaoDeTextoPdfService service =
			new ExtracaoDeTextoPdfService(ExtracaoDeTextoPdfService.HEAP_PADRAO, Duration.ofSeconds(15), 2);

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
		assertThat(textoCompleto.length()).isGreaterThan(ProcessoDeExtracaoPdf.LIMITE_DO_TEXTO);
		// O corte cai numa letra, não numa quebra (que seria tirada do fim).
		assertThat(textoCompleto.charAt(ProcessoDeExtracaoPdf.LIMITE_DO_TEXTO - 1)).isEqualTo('x');

		TextoExtraido extraido = service.extrair(arquivo(PdfsDeTeste.comPaginasMiudas(paginas)));

		assertThat(extraido.truncado()).isTrue();
		assertThat(extraido.texto()).hasSize(100_000);
		assertThat(extraido.texto()).isEqualTo(textoCompleto.substring(0, 100_000));
		assertThat(extraido.paginas()).isEqualTo(12);
	}

	// ---------------------------------------------------------------- PDFs hostis

	/**
	 * Recusado como pesado demais, e rápido: sem os tetos do {@code ExtratorDeTextoLimitado}, estes
	 * PDFs de poucos KB esgotavam a memória (literal de 300 MB) ou levavam de 15 a 18 s de CPU. Os 3 s
	 * incluem a partida da JVM do processo filho, uns 0,5 s.
	 */
	private void recusadoComoPesadoEmMenosDe3s(byte[] pdf) {
		assertTimeout(Duration.ofSeconds(3), () -> assertThatThrownBy(() -> service.extrair(arquivo(pdf)))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_PESADO));
	}

	@Test
	void literalGiganteComprimidoEhRecusadoSemDescomprimirTudo() {
		byte[] pdf = PdfsDeTeste.comConteudoBruto(1, false, COMECO_DO_TEXTO + "(", KB_DE_LITERAL, 64 * 1024, ") Tj ET");
		assertThat(pdf.length).isLessThan(MB);

		recusadoComoPesadoEmMenosDe3s(pdf);
	}

	/** Dentro do teto de bytes, mas com milhões de glifos numa página. */
	@Test
	void literalDentroDoTetoDeBytesComMilhoesDeGlifosEhRecusado() {
		recusadoComoPesadoEmMenosDe3s(
				PdfsDeTeste.comConteudoBruto(1, false, COMECO_DO_TEXTO + "(", KB_DE_LITERAL, 7 * 1024, ") Tj ET"));
	}

	@Test
	void muitosOperadoresComprimidosSaoRecusados() {
		recusadoComoPesadoEmMenosDe3s(
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

		recusadoComoPesadoEmMenosDe3s(pdf);
	}

	@Test
	void pilhaGraficaProfundaDemaisEhRecusada() {
		recusadoComoPesadoEmMenosDe3s(
				PdfsDeTeste.comConteudoBruto(1, false, "", "q ", 2_000_000, COMECO_DO_TEXTO + "(oi) Tj ET"));
	}

	@Test
	void extracaoQuePassaDoPrazoEhRecusada() {
		ExtracaoDeTextoPdfService semPrazo = new ExtracaoDeTextoPdfService(
				Duration.ZERO, ExtracaoDeTextoPdfService.HEAP_PADRAO, Duration.ofSeconds(15), new Semaphore(1), Duration.ZERO, FILHO);
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

		TextoExtraido extraido = assertTimeout(Duration.ofSeconds(3), () -> service.extrair(arquivo(pdf)));

		assertThat(extraido.texto()).isEqualTo("Primeira página\n\nSegunda página");
		assertThat(extraido.truncado()).isTrue();
		assertThat(extraido.paginas()).isEqualTo(12);
	}

	/** Conteúdo montado para pesar continua 400 mesmo com texto antes: não é só um PDF longo. */
	@Test
	void fluxoHostilDepoisDeJaHaverTextoContinuaRecusado() {
		recusadoComoPesadoEmMenosDe3s(PdfsDeTeste.comTextoSeguidoDeConteudoBruto(List.of("Primeira página"),
				1, false, COMECO_DO_TEXTO, KB_DE_TJ_VAZIO, 64 * 1024, "ET"));
	}

	/** Decodificar este JPEG, mesmo só para contar, pediria gigabytes. */
	@Test
	void conteudoComFiltroDeImagemEhRecusadoSemDecodificar() {
		recusadoComoPesadoEmMenosDe3s(PdfsDeTeste.comConteudoEmDct());
	}

	@Test
	void fonteTrueTypeEmbutidaEnormeEhRecusadaAntesDeCarregar() {
		byte[] pdf = PdfsDeTeste.comArquivoDeFonteDeZeros(COSName.TRUE_TYPE, COSName.FONT_FILE2, 64L * MB);
		assertThat(pdf.length).isLessThan(MB);

		recusadoComoPesadoEmMenosDe3s(pdf);
	}

	@Test
	void fonteCffEmbutidaEnormeEhRecusadaAntesDeCarregar() {
		recusadoComoPesadoEmMenosDe3s(PdfsDeTeste.comArquivoDeFonteDeZeros(COSName.TYPE1, COSName.FONT_FILE3, 64L * MB));
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

		recusadoComoPesadoEmMenosDe3s(pdf);
	}

	@Test
	void pdfComFonteTrueTypeRealEmbutidaContinuaPassando() {
		byte[] pdf = PdfsDeTeste.comFonteTrueTypeEmbutida("Texto em Liberation Sans, com acentuação.");

		assertThat(service.extrair(arquivo(pdf)).texto()).isEqualTo("Texto em Liberation Sans, com acentuação.");
	}

	@Test
	void semVagaParaExtrairNoPrazoDeEsperaDevolveOcupado() {
		ExtracaoDeTextoPdfService semVagas = new ExtracaoDeTextoPdfService(Duration.ofSeconds(10),
				ExtracaoDeTextoPdfService.HEAP_PADRAO, Duration.ofSeconds(15), new Semaphore(0), Duration.ofMillis(50), FILHO);

		assertThatThrownBy(() -> semVagas.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto"))))))
				.isInstanceOf(ExtracaoOcupadaException.class)
				.hasMessage("O servidor está ocupado lendo outros PDFs. Tente de novo em instantes.");
	}

	/** A vaga volta ao fim de cada extração, com sucesso ou com erro. */
	@Test
	void aVagaEhDevolvidaDepoisDeCadaExtracao() {
		Semaphore vagas = new Semaphore(1);
		ExtracaoDeTextoPdfService umaVaga = new ExtracaoDeTextoPdfService(Duration.ofSeconds(10),
				ExtracaoDeTextoPdfService.HEAP_PADRAO, Duration.ofSeconds(15), vagas, Duration.ZERO, FILHO);

		umaVaga.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto")))));
		assertThatThrownBy(() -> umaVaga.extrair(arquivo(PdfsDeTeste.comPaginaEmBranco())))
				.isInstanceOf(PdfSemTextoException.class);
		assertThatThrownBy(() -> umaVaga.extrair(arquivo(PdfsDeTeste.comConteudoEmDct())))
				.isInstanceOf(PdfInvalidoException.class);

		assertThat(vagas.availablePermits()).isEqualTo(1);
	}

	// ---------------------------------------------------------------- Processo filho

	/**
	 * Com heap pequeno no filho, para os PDFs que esgotam a memória chegarem lá mais rápido, e o prazo
	 * da extração no máximo que o prazo do processo permite.
	 */
	private static ExtracaoDeTextoPdfService comFilho(String classeDoFilho, Duration prazoDoProcesso) {
		return new ExtracaoDeTextoPdfService(prazoDoProcesso.minus(ExtracaoDeTextoPdfService.FOLGA_DO_PRAZO), "64m",
				prazoDoProcesso, new Semaphore(2, true), Duration.ofSeconds(15), classeDoFilho);
	}

	private static List<ProcessHandle> filhosVivos() {
		return ProcessHandle.current().children().filter(ProcessHandle::isAlive).toList();
	}

	/**
	 * Estes dois esgotam a memória dentro do PDFBox, onde nenhum teto do extrator alcança: o índice do
	 * arquivo, lido pelo {@code Loader.loadPDF}, e um {@code ToUnicode} que vira milhões de entradas.
	 * Só o filho morre, e a resposta é a de PDF pesado demais.
	 */
	@Test
	void tabelaXrefComMilhoesDeEntradasEsgotaSoAMemoriaDoProcessoFilho() {
		byte[] pdf = PdfsDeTeste.comTabelaXrefDeclarando(30_000_000);
		assertThat(pdf.length).isLessThan(MB);

		assertThatThrownBy(() -> comFilho(FILHO, Duration.ofSeconds(15)).extrair(arquivo(pdf)))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_PESADO);
	}

	@Test
	void toUnicodeComFaixasEnormesEsgotaSoAMemoriaDoProcessoFilho() {
		byte[] pdf = PdfsDeTeste.comToUnicodeDeFaixasEnormes(4);
		assertThat(pdf.length).isLessThan(2 * 1024);

		assertThatThrownBy(() -> comFilho(FILHO, Duration.ofSeconds(15)).extrair(arquivo(pdf)))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_PESADO);
	}

	@Test
	void processoQuePassaDoPrazoEhEncerradoEOPdfEhPesadoDemais() {
		ExtracaoDeTextoPdfService prazoCurto = comFilho(FilhosDeTeste.QueNaoTermina.class.getName(), Duration.ofSeconds(2));

		assertTimeout(Duration.ofSeconds(5), () -> assertThatThrownBy(
				() -> prazoCurto.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto"))))))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_PESADO));
		assertThat(filhosVivos()).isEmpty();
	}

	/** No Linux, o OOM killer do kernel escolhe o filho, e não o backend, quando a máquina fica sem memória. */
	@Test
	void filhoVivoTemAMaiorPrioridadeParaOOomKiller() throws Exception {
		assumeTrue(Files.exists(Path.of("/proc/self/oom_score_adj")), "só no Linux");
		ExtracaoDeTextoPdfService prazoCurto = comFilho(FilhosDeTeste.QueNaoTermina.class.getName(), Duration.ofSeconds(2));
		CompletableFuture<Throwable> extracao = CompletableFuture.supplyAsync(() -> catchThrowable(
				() -> prazoCurto.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto")))))));

		String prioridade = null;
		long limite = System.nanoTime() + Duration.ofSeconds(2).toNanos();
		while (!"1000".equals(prioridade) && System.nanoTime() < limite) {
			prioridade = filhosVivos().stream().findFirst().map(filho -> {
				try {
					return Files.readString(Path.of("/proc", Long.toString(filho.pid()), "oom_score_adj")).strip();
				} catch (IOException e) {
					return null;
				}
			}).orElse(null);
			Thread.sleep(5);
		}

		assertThat(prioridade).isEqualTo("1000");
		assertThat(extracao.get()).isInstanceOf(PdfInvalidoException.class).hasMessage(MENSAGEM_PESADO);
	}

	/** Um sinal que não veio do backend, como o SIGKILL do OOM killer do container: o PDF pesou demais. */
	@Test
	void filhoEncerradoPorSinalEhPdfPesadoDemais() {
		assertThatThrownBy(() -> comFilho(FilhosDeTeste.ComMortePorSinal.class.getName(), Duration.ofSeconds(15))
				.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto"))))))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_PESADO);
	}

	/** A leitura para no tamanho máximo do resultado e encerra o filho, sem esperar o prazo. */
	@Test
	void saidaSemFimDoProcessoEhRecusadaSemEsperarOPrazo() {
		ExtracaoDeTextoPdfService saidaSemFim =
				comFilho(FilhosDeTeste.ComSaidaSemFim.class.getName(), Duration.ofSeconds(15));

		assertTimeout(Duration.ofSeconds(5), () -> assertThatThrownBy(
				() -> saidaSemFim.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto"))))))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_CORROMPIDO));
		assertThat(filhosVivos()).isEmpty();
	}

	@Test
	void saidaForaDoFormatoEhRecusadaSemRepassarNadaDoFilho() {
		ExtracaoDeTextoPdfService codigoQueNaoExiste =
				comFilho(FilhosDeTeste.ComCodigoQueNaoExiste.class.getName(), Duration.ofSeconds(15));

		assertThatThrownBy(() -> codigoQueNaoExiste.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto"))))))
				.isInstanceOf(PdfInvalidoException.class)
				.hasMessage(MENSAGEM_CORROMPIDO);
	}

	/** No ambiente do backend há segredos (chave da Anthropic, senha do banco, segredo do JWT). */
	@Test
	void processoFilhoNaoHerdaOAmbienteNemAsPropriedadesDoPai() {
		assertThat(System.getenv()).isNotEmpty();
		System.setProperty(FilhosDeTeste.PROPRIEDADE_DO_PAI, "segredo");
		try {
			TextoExtraido herdado = comFilho(FilhosDeTeste.QueDevolveOQueHerdou.class.getName(), Duration.ofSeconds(15))
					.extrair(arquivo(PdfsDeTeste.comPaginas(List.of(List.of("Texto")))));

			assertThat(herdado.texto()).isEqualTo(
					"ambiente=0 propriedade=null tmpdir=" + System.getProperty("java.io.tmpdir"));
		} finally {
			System.clearProperty(FilhosDeTeste.PROPRIEDADE_DO_PAI);
		}
	}

	@Test
	void construtorDoProcessoNaoPassaAmbienteEDescartaASaidaDeErro() {
		ProcessBuilder construtor = service.construtorDoProcesso();

		assertThat(construtor.environment()).isEmpty();
		assertThat(construtor.redirectError()).isEqualTo(ProcessBuilder.Redirect.DISCARD);
		assertThat(construtor.command()).contains("-Xmx256m", "-XX:+ExitOnOutOfMemoryError",
				"-XX:+DisplayVMOutputToStderr", "-Xlog:disable", "-Xlog:all=warning:stderr", FILHO);
		assertThat(construtor.command()).filteredOn(argumento -> argumento.startsWith("-D"))
				.containsExactly("-Djava.awt.headless=true", "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"));
	}

	/**
	 * Um aviso do log unificado da JVM (aqui, o de um arquivo CDS que não existe) iria para a saída
	 * padrão e estragaria o resultado de todo PDF. Com as flags do comando, vai para a saída de erro.
	 */
	@Test
	void avisoDoLogUnificadoDaJvmNaoEntraNaSaidaDoFilho() throws Exception {
		List<String> comando = new ArrayList<>(service.construtorDoProcesso().command());
		comando.add(1, "-XX:SharedArchiveFile=/nao-existe.jsa");
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of("Texto")));

		ProcessoDeExtracaoPdf.Resultado resultado = ProcessoDeExtracaoPdf.ler(rodar(comando, pdf));

		assertThat(resultado.texto().texto()).isEqualTo("Texto");
		// Sem as flags, o mesmo aviso abre a saída padrão: o teste alcança o problema.
		comando.removeAll(List.of("-Xlog:disable", "-Xlog:all=warning:stderr"));
		assertThat(new String(rodar(comando, pdf), StandardCharsets.ISO_8859_1)).startsWith("[");
	}

	private static byte[] rodar(List<String> comando, byte[] entrada) throws Exception {
		Process processo = new ProcessBuilder(comando).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		try (OutputStream stdin = processo.getOutputStream()) {
			stdin.write(entrada);
		}
		byte[] saida = processo.getInputStream().readAllBytes();
		assertThat(processo.waitFor(15, TimeUnit.SECONDS)).isTrue();
		return saida;
	}

	@Test
	void comClasseNoClasspathOFilhoRodaDireto() {
		assertThat(ExtracaoDeTextoPdfService.comando("/jre/bin/java", "/app/backend.jar", true, "256m", FILHO,
				Duration.ofSeconds(10), "/tmp"))
				.startsWith("/jre/bin/java", "-Xmx256m")
				.endsWith("-cp", "/app/backend.jar", FILHO, "10000");
	}

	/** No jar executável do Spring Boot, a classe está em {@code BOOT-INF/classes}, fora do classpath. */
	@Test
	void noJarExecutavelOFilhoRodaPeloLancadorDoSpringBoot() {
		assertThat(ExtracaoDeTextoPdfService.comando("/jre/bin/java", "backend.jar", false, "256m", FILHO,
				Duration.ofSeconds(10), "/tmp"))
				.endsWith("-cp", "backend.jar", "-Dloader.main=" + FILHO,
						"org.springframework.boot.loader.launch.PropertiesLauncher", "10000");
	}

	@ParameterizedTest
	@ValueSource(strings = {"256m -Dx=y", "256", "256k", "0m"})
	void heapInvalidoNaConfiguracaoImpedeASubida(String heap) {
		assertThatThrownBy(() -> new ExtracaoDeTextoPdfService(heap, Duration.ofSeconds(15), 2))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** O prazo do processo precisa cobrir os 10 s da extração mais 2 s, senão o truncado parcial some. */
	@Test
	void prazoDoProcessoCurtoDemaisImpedeASubida() {
		assertThatThrownBy(() -> new ExtracaoDeTextoPdfService("256m", Duration.ofSeconds(11), 2))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new ExtracaoDeTextoPdfService("256m", Duration.ofSeconds(12), 2)).isNotNull();
	}

	@Test
	void semVagasNaConfiguracaoImpedeASubida() {
		assertThatThrownBy(() -> new ExtracaoDeTextoPdfService("256m", Duration.ofSeconds(15), 0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
