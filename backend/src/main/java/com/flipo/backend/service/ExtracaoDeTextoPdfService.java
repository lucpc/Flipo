package com.flipo.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@code POST /api/pdf/extrair-texto} (docs/03-contrato-api.md): extrai o texto de um PDF para o
 * usuário revisar e cortar no campo do "Gerar com IA" antes do {@code gerar-ia}. Não grava nada e
 * não guarda o arquivo: não tem acesso a repositório, e o que o PDFBox põe em arquivo temporário é
 * apagado quando o documento é fechado.
 *
 * <p>Nada do conteúdo vai para log nem para a resposta de erro.
 *
 * <p>A extração roda num processo Java filho ({@link ProcessoDeExtracaoPdf}), com memória e prazo
 * próprios: um PDF hostil que esgote a memória ou trave o PDFBox derruba só o filho, e o usuário
 * recebe 400. Os tetos do {@link ExtratorDeTextoLimitado} continuam valendo dentro do filho: recusam
 * cedo a maior parte dos PDFs hostis, e o prazo deles é o que entrega o texto já lido de um PDF longo.
 */
@Service
public class ExtracaoDeTextoPdfService {

	private static final Logger log = LoggerFactory.getLogger(ExtracaoDeTextoPdfService.class);

	private static final byte[] ASSINATURA = "%PDF-".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] FIM_DO_ARQUIVO = "%%EOF".getBytes(StandardCharsets.US_ASCII);

	/** Onde o marcador de fim precisa aparecer: nos últimos 1024 bytes, como o Acrobat aceita. */
	private static final int JANELA_DO_FIM = 1024;

	/** Prazo de CPU da extração dentro do filho, a última barreira do {@link ExtratorDeTextoLimitado}. */
	private static final Duration PRAZO_DA_EXTRACAO = Duration.ofSeconds(10);

	/**
	 * Padrões das propriedades {@code flipo.pdf.*} (application.properties). O prazo do processo cobre
	 * a partida da JVM, os 10 s da extração e uma folga; passou dele, o filho é encerrado à força.
	 */
	static final String HEAP_PADRAO = "256m";
	static final String PRAZO_DO_PROCESSO_PADRAO = "15s";
	static final String VAGAS_PADRAO = "2";

	/**
	 * Quem espera mais que isso por uma vaga recebe 503. Cada vaga é um processo filho, então as vagas
	 * limitam a memória somada das extrações.
	 */
	private static final Duration ESPERA_POR_VAGA = Duration.ofSeconds(15);

	/** O mínimo que o prazo do processo dá além do prazo da extração, para a partida da JVM. */
	static final Duration FOLGA_DO_PRAZO = Duration.ofSeconds(2);

	/** Depois que o filho sai, o tanto que a leitura da saída dele ainda pode levar. */
	private static final Duration ESPERA_PELA_SAIDA = Duration.ofSeconds(2);

	/** Código de saída da JVM com {@code -XX:+ExitOnOutOfMemoryError}. */
	static final int SAIDA_POR_FALTA_DE_MEMORIA = 3;

	/** Acima disso, o código de saída é 128 mais o sinal que encerrou o processo. */
	private static final int SAIDA_POR_SINAL = 128;

	/** Abre o jar executável do Spring Boot, com as classes em {@code BOOT-INF/}, numa classe qualquer. */
	static final String LANCADOR_DO_JAR = "org.springframework.boot.loader.launch.PropertiesLauncher";

	private static final String MENSAGEM_SEM_ARQUIVO = "Envie o PDF no campo \"arquivo\".";
	private static final String MENSAGEM_VAZIO = "O arquivo enviado está vazio.";
	private static final String MENSAGEM_NAO_E_PDF = "O arquivo enviado não é um PDF.";
	private static final String MENSAGEM_CORROMPIDO = "Não foi possível ler este PDF. Ele pode estar corrompido.";
	private static final String MENSAGEM_COM_SENHA = "Este PDF está protegido por senha. Envie uma versão sem senha.";
	private static final String MENSAGEM_PESADO = "Este PDF é pesado demais para extrair o texto. Cole o texto no campo.";

	private final Duration prazoDaExtracao;
	private final String heap;
	private final Duration prazoDoProcesso;
	private final Semaphore vagas;
	private final Duration esperaPorVaga;
	private final String classeDoFilho;

	@Autowired
	public ExtracaoDeTextoPdfService(
			@Value("${flipo.pdf.processo.heap:" + HEAP_PADRAO + "}") String heap,
			@Value("${flipo.pdf.processo.prazo:" + PRAZO_DO_PROCESSO_PADRAO + "}") Duration prazoDoProcesso,
			@Value("${flipo.pdf.vagas:" + VAGAS_PADRAO + "}") int vagas) {
		this(PRAZO_DA_EXTRACAO, heap, prazoDoProcesso, new Semaphore(vagasValidas(vagas), true), ESPERA_POR_VAGA,
				ProcessoDeExtracaoPdf.class.getName());
	}

	private static int vagasValidas(int vagas) {
		if (vagas < 1) {
			throw new IllegalArgumentException("flipo.pdf.vagas precisa ser pelo menos 1: " + vagas);
		}
		return vagas;
	}

	/**
	 * Para os testes alcançarem os prazos e a falta de vaga sem esperar, e trocarem o filho por um que
	 * devolve uma saída fora do formato.
	 */
	ExtracaoDeTextoPdfService(Duration prazoDaExtracao, String heap, Duration prazoDoProcesso, Semaphore vagas,
			Duration esperaPorVaga, String classeDoFilho) {
		// Sem unidade, a JVM lê bytes: "256" seria um heap de 256 bytes, e o filho nem partiria.
		if (!heap.matches("[1-9][0-9]*[mMgG]")) {
			throw new IllegalArgumentException("flipo.pdf.processo.heap precisa de MB ou GB (ex: 256m): " + heap);
		}
		// Com menos folga, o prazo do processo venceria antes do prazo da extração, e um PDF longo, que
		// entregaria o texto já lido como truncado, seria recusado como pesado demais.
		if (prazoDoProcesso.compareTo(prazoDaExtracao.plus(FOLGA_DO_PRAZO)) < 0) {
			throw new IllegalArgumentException("flipo.pdf.processo.prazo precisa passar de "
					+ prazoDaExtracao.plus(FOLGA_DO_PRAZO).toSeconds() + " s: " + prazoDoProcesso);
		}
		this.prazoDaExtracao = prazoDaExtracao;
		this.heap = heap;
		this.prazoDoProcesso = prazoDoProcesso;
		this.vagas = vagas;
		this.esperaPorVaga = esperaPorVaga;
		this.classeDoFilho = classeDoFilho;
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
			return extrairNoProcessoFilho(ler(arquivo));
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

	private TextoExtraido extrairNoProcessoFilho(byte[] conteudo) {
		Process processo;
		try {
			processo = construtorDoProcesso().start();
		} catch (IOException e) {
			throw new IllegalStateException("Não foi possível iniciar o processo de extração de PDF", e);
		}
		try {
			oferecerAoOomKiller(processo);
			return traduzir(executar(processo, conteudo));
		} finally {
			encerrar(processo);
		}
	}

	/**
	 * No Linux, se a máquina ficar sem memória, o kernel encerra primeiro o filho, não o backend. Só
	 * aumenta a prioridade do próprio filho, o que não pede permissão especial; fora do Linux, ou se a
	 * escrita falhar, segue sem ela.
	 */
	private static void oferecerAoOomKiller(Process processo) {
		try {
			Files.writeString(Path.of("/proc", Long.toString(processo.pid()), "oom_score_adj"), "1000");
		} catch (IOException | RuntimeException e) {
			// Melhor esforço: os limites do filho continuam valendo sem isso.
		}
	}

	/**
	 * O filho recebe só o comando: nenhuma variável de ambiente (há segredos no ambiente do backend,
	 * como a chave da Anthropic, a senha do banco e o segredo do JWT) e, das propriedades do pai, só o
	 * {@code java.io.tmpdir}, onde o PDFBox guarda o que passa de 8 MB. A saída de erro é descartada,
	 * para não encher um buffer que ninguém lê.
	 */
	ProcessBuilder construtorDoProcesso() {
		boolean classeNoClasspath = ClassLoader.getSystemClassLoader()
				.getResource(classeDoFilho.replace('.', '/') + ".class") != null;
		ProcessBuilder construtor = new ProcessBuilder(comando(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				System.getProperty("java.class.path"), classeNoClasspath, heap, classeDoFilho,
				prazoDaExtracao, System.getProperty("java.io.tmpdir")));
		construtor.environment().clear();
		construtor.redirectError(ProcessBuilder.Redirect.DISCARD);
		return construtor;
	}

	/**
	 * O comando do filho. Em desenvolvimento, nos testes e no jar extraído para o Docker
	 * ({@code -Djarmode=tools extract}), a classe do filho está no classpath da JVM e roda direto. No jar
	 * executável do Spring Boot ({@code java -jar}), as classes ficam em {@code BOOT-INF/}, fora do
	 * classpath da JVM, e quem as abre é o {@link #LANCADOR_DO_JAR}.
	 *
	 * <p>{@code -XX:+ExitOnOutOfMemoryError} faz da falta de memória uma saída com
	 * {@link #SAIDA_POR_FALTA_DE_MEMORIA}. Nada da JVM pode ir para a saída padrão, que é só do
	 * resultado: {@code -XX:+DisplayVMOutputToStderr} desvia as mensagens antigas da JVM (como a da
	 * falta de memória), e o par {@code -Xlog:disable -Xlog:all=warning:stderr} desvia o log
	 * unificado, que por padrão escreve avisos e erros na saída padrão (ex: {@code [warning][os,thread]}
	 * num container sem folga de threads). Só o segundo não basta: ele acrescenta a saída de erro sem
	 * tirar a padrão. GC serial e um processador: a extração é de uma thread,
	 * e assim a JVM parte com menos threads e memória. O compilador fica o padrão: só com o C1
	 * ({@code -XX:TieredStopAtLevel=1}) a partida ganhava menos de 0,1 s, e um PDF grande levava o
	 * dobro do tempo, o que corta mais cedo o texto de um PDF longo no prazo da extração.
	 * {@code -XX:-UsePerfData} evita o arquivo de métricas em {@code /tmp} a cada extração.
	 */
	static List<String> comando(String java, String classpath, boolean classeNoClasspath, String heap,
			String classeDoFilho, Duration prazoDaExtracao, String pastaTemporaria) {
		List<String> comando = new ArrayList<>(List.of(java,
				"-Xmx" + heap,
				"-XX:MaxMetaspaceSize=64m",
				"-XX:+UseSerialGC",
				"-XX:ActiveProcessorCount=1",
				"-XX:+ExitOnOutOfMemoryError",
				"-XX:+DisplayVMOutputToStderr",
				"-Xlog:disable",
				"-Xlog:all=warning:stderr",
				"-XX:-UsePerfData",
				"-Djava.awt.headless=true",
				"-Djava.io.tmpdir=" + pastaTemporaria,
				"-cp", classpath));
		if (classeNoClasspath) {
			comando.add(classeDoFilho);
		} else {
			comando.add("-Dloader.main=" + classeDoFilho);
			comando.add(LANCADOR_DO_JAR);
		}
		comando.add(Long.toString(prazoDaExtracao.toMillis()));
		return comando;
	}

	/**
	 * Escreve o PDF e lê a saída em threads próprias, para nenhuma das duas travar a outra nem o prazo.
	 * A leitura para em {@link ProcessoDeExtracaoPdf#MAIOR_SAIDA} mais um byte: passou disso, o filho é
	 * encerrado e a saída é inválida.
	 */
	private ProcessoDeExtracaoPdf.Resultado executar(Process processo, byte[] conteudo) {
		Thread.ofPlatform().daemon().name("extracao-pdf-entrada").start(() -> enviar(processo, conteudo));
		FutureTask<byte[]> leitura = new FutureTask<>(() -> receber(processo));
		Thread.ofPlatform().daemon().name("extracao-pdf-saida").start(leitura);
		try {
			if (!processo.waitFor(prazoDoProcesso.toNanos(), TimeUnit.NANOSECONDS)) {
				log.warn("Extração de PDF encerrada: o processo filho passou do prazo de {}", prazoDoProcesso);
				return ProcessoDeExtracaoPdf.Resultado.de(ProcessoDeExtracaoPdf.Codigo.PESADO);
			}
			int codigoDeSaida = processo.exitValue();
			if (codigoDeSaida == SAIDA_POR_FALTA_DE_MEMORIA) {
				log.warn("Extração de PDF encerrada: o processo filho ficou sem memória");
				return ProcessoDeExtracaoPdf.Resultado.de(ProcessoDeExtracaoPdf.Codigo.PESADO);
			}
			byte[] saida = leitura.get(ESPERA_PELA_SAIDA.toNanos(), TimeUnit.NANOSECONDS);
			// Saída grande demais: o receber já encerrou o filho, e o ler a recusa como inválida.
			boolean encerradoPeloPai = saida.length > ProcessoDeExtracaoPdf.MAIOR_SAIDA;
			// Encerrado por um sinal que não veio daqui, como o do OOM killer do kernel quando o container
			// fica sem memória: o PDF pesou demais.
			if (codigoDeSaida > SAIDA_POR_SINAL && !encerradoPeloPai) {
				log.warn("Extração de PDF encerrada: o processo filho recebeu o sinal {}", codigoDeSaida - SAIDA_POR_SINAL);
				return ProcessoDeExtracaoPdf.Resultado.de(ProcessoDeExtracaoPdf.Codigo.PESADO);
			}
			if (codigoDeSaida != 0 && !encerradoPeloPai) {
				log.warn("Extração de PDF falhou: o processo filho saiu com o código {}", codigoDeSaida);
				return ProcessoDeExtracaoPdf.Resultado.de(ProcessoDeExtracaoPdf.Codigo.CORROMPIDO);
			}
			return ProcessoDeExtracaoPdf.ler(saida);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ExtracaoOcupadaException();
		} catch (ExecutionException | TimeoutException | ProcessoDeExtracaoPdf.SaidaInvalida e) {
			log.warn("Extração de PDF falhou: saída inválida do processo filho ({})", e.getClass().getSimpleName());
			return ProcessoDeExtracaoPdf.Resultado.de(ProcessoDeExtracaoPdf.Codigo.CORROMPIDO);
		}
	}

	private static void enviar(Process processo, byte[] conteudo) {
		try (OutputStream entrada = processo.getOutputStream()) {
			entrada.write(conteudo);
		} catch (IOException e) {
			// O filho saiu ou foi encerrado sem ler tudo: quem decide é o resultado dele.
		}
	}

	private static byte[] receber(Process processo) throws IOException {
		try (InputStream saida = processo.getInputStream()) {
			byte[] lidos = saida.readNBytes(ProcessoDeExtracaoPdf.MAIOR_SAIDA + 1);
			if (lidos.length > ProcessoDeExtracaoPdf.MAIOR_SAIDA) {
				processo.destroyForcibly();
			}
			return lidos;
		}
	}

	/** Em qualquer caminho, o filho termina antes de a vaga voltar. */
	private static void encerrar(Process processo) {
		processo.descendants().forEach(ProcessHandle::destroyForcibly);
		processo.destroyForcibly();
		boolean interrompida = false;
		while (true) {
			try {
				processo.waitFor();
				break;
			} catch (InterruptedException e) {
				interrompida = true;
			}
		}
		if (interrompida) {
			Thread.currentThread().interrupt();
		}
	}

	/** O motivo da recusa vai para o log, só o código: nada do conteúdo do PDF. */
	private static TextoExtraido traduzir(ProcessoDeExtracaoPdf.Resultado resultado) {
		if (resultado.codigo() != ProcessoDeExtracaoPdf.Codigo.OK) {
			log.info("Extração de PDF recusada: {}", resultado.codigo());
		}
		return switch (resultado.codigo()) {
			case OK -> resultado.texto();
			case CORROMPIDO -> throw new PdfInvalidoException(MENSAGEM_CORROMPIDO);
			case COM_SENHA -> throw new PdfInvalidoException(MENSAGEM_COM_SENHA);
			case PESADO -> throw new PdfInvalidoException(MENSAGEM_PESADO);
			case SEM_TEXTO -> throw new PdfSemTextoException();
		};
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
