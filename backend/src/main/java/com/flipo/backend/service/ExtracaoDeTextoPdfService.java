package com.flipo.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * {@code POST /api/pdf/extrair-texto} (docs/03-contrato-api.md): extrai o texto de um PDF para o
 * usuário revisar e cortar no campo do "Gerar com IA" antes do {@code gerar-ia}. Não grava nada e
 * não guarda o arquivo: não tem acesso a repositório, e o que o PDFBox põe em arquivo temporário é
 * apagado quando o documento é fechado.
 *
 * <p>Nada do conteúdo vai para log nem para a resposta de erro: só o tipo da exceção, no log.
 *
 * <p>Os tetos de custo ({@link ExtratorDeTextoLimitado}) e o limite de extrações simultâneas são
 * mitigação: a extração ainda roda no processo do backend, e há caminhos do PDFBox que eles não
 * cobrem (ver o Javadoc do extrator). Isolá-la num processo separado é a issue #75.
 */
@Service
public class ExtracaoDeTextoPdfService {

	private static final Logger log = LoggerFactory.getLogger(ExtracaoDeTextoPdfService.class);

	/** Em unidades UTF-16, como o {@code String.length()}. */
	static final int LIMITE_DO_TEXTO = 100_000;

	/** Limiar de queda entre linhas para o PDFBox abrir um parágrafo; ver {@link #extrator()}. */
	static final float LIMIAR_DE_PARAGRAFO = 4.0f;

	private static final byte[] ASSINATURA = "%PDF-".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] FIM_DO_ARQUIVO = "%%EOF".getBytes(StandardCharsets.US_ASCII);

	/** Onde o marcador de fim precisa aparecer: nos últimos 1024 bytes, como o Acrobat aceita. */
	private static final int JANELA_DO_FIM = 1024;

	/**
	 * Cache de fluxos do PDFBox: até 8 MB em memória, o resto em arquivo temporário, no máximo 64 MB.
	 * Ele só guarda fluxos que o PDFBox escreve, e na leitura quase não é usado: não limita a
	 * descompressão nem a interpretação do conteúdo das páginas. Quem limita isso é o
	 * {@link ExtratorDeTextoLimitado}, com os tetos abaixo.
	 */
	private static final long MEMORIA_DE_TRABALHO = 8L * 1024 * 1024;
	private static final long ARMAZENAMENTO_DE_TRABALHO = 64L * 1024 * 1024;

	/** Prazo de CPU da extração, a última barreira do {@link ExtratorDeTextoLimitado}. */
	private static final Duration PRAZO = Duration.ofSeconds(10);

	/**
	 * Extrações simultâneas, com fila justa. Os tetos do {@link ExtratorDeTextoLimitado} ainda deixam
	 * uma extração ocupar uma centena de MB; com duas vagas, o pior caso fica conhecido. Quem espera
	 * mais que {@link #ESPERA_POR_VAGA} recebe 503.
	 */
	private static final int VAGAS = 2;
	private static final Duration ESPERA_POR_VAGA = Duration.ofSeconds(15);

	private static final String MENSAGEM_SEM_ARQUIVO = "Envie o PDF no campo \"arquivo\".";
	private static final String MENSAGEM_VAZIO = "O arquivo enviado está vazio.";
	private static final String MENSAGEM_NAO_E_PDF = "O arquivo enviado não é um PDF.";
	private static final String MENSAGEM_CORROMPIDO = "Não foi possível ler este PDF. Ele pode estar corrompido.";
	private static final String MENSAGEM_COM_SENHA = "Este PDF está protegido por senha. Envie uma versão sem senha.";
	private static final String MENSAGEM_PESADO = "Este PDF é pesado demais para extrair o texto. Cole o texto no campo.";

	private final Duration prazo;
	private final Semaphore vagas;
	private final Duration esperaPorVaga;

	public ExtracaoDeTextoPdfService() {
		this(PRAZO, new Semaphore(VAGAS, true), ESPERA_POR_VAGA);
	}

	/** Para os testes alcançarem o prazo e a falta de vaga sem esperar. */
	ExtracaoDeTextoPdfService(Duration prazo, Semaphore vagas, Duration esperaPorVaga) {
		this.prazo = prazo;
		this.vagas = vagas;
		this.esperaPorVaga = esperaPorVaga;
	}

	/**
	 * @throws PdfInvalidoException sem arquivo, arquivo vazio, que não é PDF, corrompido, com senha ou
	 *     pesado demais para extrair
	 * @throws PdfSemTextoException o PDF não tem texto selecionável
	 * @throws ExtracaoOcupadaException não abriu vaga para extrair a tempo
	 */
	public TextoExtraido extrair(MultipartFile arquivo) {
		if (arquivo == null) {
			throw new PdfInvalidoException(MENSAGEM_SEM_ARQUIVO);
		}
		if (arquivo.isEmpty()) {
			throw new PdfInvalidoException(MENSAGEM_VAZIO);
		}
		// A vaga vem antes de ler o arquivo: na fila, cada requisição só tem o upload no arquivo
		// temporário do multipart, não 10 MB na memória.
		ocuparVaga();
		try {
			return extrairDoPdf(ler(arquivo));
		} finally {
			vagas.release();
		}
	}

	private static byte[] ler(MultipartFile arquivo) {
		byte[] conteudo;
		try {
			conteudo = arquivo.getBytes();
		} catch (IOException e) {
			log.warn("Falha ao ler o arquivo enviado: {}", e.getClass().getName());
			throw new PdfInvalidoException(MENSAGEM_CORROMPIDO);
		}
		if (!comecaCom(conteudo, ASSINATURA)) {
			throw new PdfInvalidoException(MENSAGEM_NAO_E_PDF);
		}
		// O PDFBox é tolerante: de um PDF cortado no meio ele recupera as páginas que sobraram e
		// devolveria parte do texto como se fosse tudo. Sem o marcador de fim, o arquivo está incompleto.
		if (!temNoFim(conteudo, FIM_DO_ARQUIVO)) {
			throw new PdfInvalidoException(MENSAGEM_CORROMPIDO);
		}
		return conteudo;
	}

	private void ocuparVaga() {
		boolean conseguiu;
		try {
			conseguiu = vagas.tryAcquire(esperaPorVaga.toNanos(), TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			conseguiu = false;
		}
		if (!conseguiu) {
			throw new ExtracaoOcupadaException();
		}
	}

	private TextoExtraido extrairDoPdf(byte[] conteudo) {
		TextoNormalizado texto = new TextoNormalizado(LIMITE_DO_TEXTO);
		boolean interrompida = false;
		int paginas;
		try (PDDocument documento = Loader.loadPDF(conteudo, "", null, null,
				MemoryUsageSetting.setupMixed(MEMORIA_DE_TRABALHO, ARMAZENAMENTO_DE_TRABALHO).streamCache)) {
			paginas = documento.getNumberOfPages();
			try {
				extrator().writeText(documento, texto);
			} catch (TextoNormalizado.LimiteAtingido e) {
				// Já há texto até o limite; o resto do PDF não seria usado.
			} catch (ExtratorDeTextoLimitado.PesadoDemais e) {
				log.warn("Extração de PDF interrompida: teto de {}", e.getMessage());
				// Um PDF só longo demais (teto somado, prazo) ainda entrega o texto das páginas já lidas;
				// conteúdo montado para pesar, ou nenhum texto ainda, é recusado.
				if (e.hostil() || texto.vazio()) {
					throw new PdfInvalidoException(MENSAGEM_PESADO);
				}
				interrompida = true;
			}
		} catch (PdfInvalidoException e) {
			throw e;
		} catch (InvalidPasswordException e) {
			throw new PdfInvalidoException(MENSAGEM_COM_SENHA);
		} catch (IOException | RuntimeException e) {
			// Só o tipo: a mensagem do PDFBox pode citar trechos do arquivo.
			log.warn("Falha ao extrair o texto do PDF: {}", e.getClass().getName());
			throw new PdfInvalidoException(MENSAGEM_CORROMPIDO);
		}

		if (texto.vazio()) {
			throw new PdfSemTextoException();
		}
		return new TextoExtraido(texto.toString(), paginas, texto.truncado() || interrompida);
	}

	/**
	 * Um parágrafo que o PDFBox detecta termina com uma linha em branco, e cada página também, para o
	 * texto do campo manter a divisão do PDF. O {@link TextoNormalizado} reduz o excesso a uma linha
	 * em branco.
	 *
	 * <p>O limiar de queda ({@code dropThreshold}) sobe do padrão 2,5 para {@value #LIMIAR_DE_PARAGRAFO}:
	 * com o padrão, o espaçamento 1,5 entre linhas (o da ABNT) já conta como fim de parágrafo, e o
	 * texto vinha com uma linha em branco depois de cada linha do PDF.
	 */
	private PDFTextStripper extrator() {
		PDFTextStripper extrator = new ExtratorDeTextoLimitado(prazo);
		extrator.setDropThreshold(LIMIAR_DE_PARAGRAFO);
		extrator.setLineSeparator("\n");
		extrator.setParagraphEnd("\n");
		extrator.setPageEnd("\n\n");
		return extrator;
	}

	private static boolean comecaCom(byte[] conteudo, byte[] prefixo) {
		return conteudo.length >= prefixo.length
				&& Arrays.equals(conteudo, 0, prefixo.length, prefixo, 0, prefixo.length);
	}

	private static boolean temNoFim(byte[] conteudo, byte[] marcador) {
		int inicio = Math.max(0, conteudo.length - JANELA_DO_FIM);
		for (int i = conteudo.length - marcador.length; i >= inicio; i--) {
			if (Arrays.equals(conteudo, i, i + marcador.length, marcador, 0, marcador.length)) {
				return true;
			}
		}
		return false;
	}
}
