package com.flipo.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

/**
 * A extração do {@code POST /api/pdf/extrair-texto}, rodada num processo Java filho pelo
 * {@link ExtracaoDeTextoPdfService}: um PDF hostil que esgote a memória ou trave o PDFBox derruba só
 * este processo, não o backend. Não usa o Spring.
 *
 * <p>O {@link #main} lê o PDF inteiro pela entrada padrão e escreve o resultado na saída padrão, no
 * formato de {@link #escrever}: um byte com o {@link Codigo} e, só no {@link Codigo#OK}, as páginas,
 * o {@code truncado} e o texto em UTF-16. As mensagens para o usuário ficam no pai; daqui só sai o
 * código e o texto extraído. O único argumento é o prazo da extração, em milissegundos.
 */
public final class ProcessoDeExtracaoPdf {

	/** Em unidades UTF-16, como o {@code String.length()}. */
	static final int LIMITE_DO_TEXTO = 100_000;

	/** Limiar de queda entre linhas para o PDFBox abrir um parágrafo; ver {@link #extrator}. */
	static final float LIMIAR_DE_PARAGRAFO = 4.0f;

	/** A maior saída válida: código, páginas, truncado, tamanho do texto e o texto em UTF-16. */
	static final int MAIOR_SAIDA = 1 + 4 + 1 + 4 + 2 * LIMITE_DO_TEXTO;

	/**
	 * Cache de fluxos do PDFBox: até 8 MB em memória, o resto em arquivo temporário, no máximo 64 MB.
	 * Ele só guarda fluxos que o PDFBox escreve, e na leitura quase não é usado: não limita a
	 * descompressão nem a interpretação do conteúdo das páginas. Quem limita isso é o
	 * {@link ExtratorDeTextoLimitado}.
	 */
	private static final long MEMORIA_DE_TRABALHO = 8L * 1024 * 1024;
	private static final long ARMAZENAMENTO_DE_TRABALHO = 64L * 1024 * 1024;

	/** O resultado da extração; o pai traduz cada um para a resposta do contrato. */
	enum Codigo {
		OK, CORROMPIDO, COM_SENHA, SEM_TEXTO, PESADO
	}

	/** {@code texto} só no {@link Codigo#OK}. */
	record Resultado(Codigo codigo, TextoExtraido texto) {

		static Resultado de(Codigo codigo) {
			return new Resultado(codigo, null);
		}
	}

	/** A saída do processo filho não está no formato de {@link #escrever}. */
	static final class SaidaInvalida extends IOException {

		SaidaInvalida(String motivo) {
			super(motivo);
		}
	}

	private ProcessoDeExtracaoPdf() {
	}

	public static void main(String[] args) throws IOException {
		// A saída padrão é só do resultado: o que mais for escrito nela (por uma biblioteca, por
		// exemplo) vai para a saída de erro, que o pai descarta.
		OutputStream saida = new FileOutputStream(FileDescriptor.out);
		System.setOut(System.err);
		// O log do PDFBox pode citar trechos do arquivo e, sem a configuração do Spring, sairia em
		// DEBUG; aqui ninguém o lê. Precisa vir antes da primeira classe do PDFBox.
		System.setProperty("org.apache.commons.logging.LogFactory", "org.apache.commons.logging.impl.LogFactoryImpl");
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

		Duration prazo = Duration.ofMillis(Long.parseLong(args[0]));
		Resultado resultado = extrair(System.in.readAllBytes(), prazo);
		escrever(resultado, saida);
		saida.flush();
		System.exit(0);
	}

	/** O mesmo que a extração fazia no processo do backend, com os erros como {@link Codigo}. */
	static Resultado extrair(byte[] conteudo, Duration prazo) {
		TextoNormalizado texto = new TextoNormalizado(LIMITE_DO_TEXTO);
		boolean interrompida = false;
		int paginas;
		try (PDDocument documento = Loader.loadPDF(conteudo, "", null, null,
				MemoryUsageSetting.setupMixed(MEMORIA_DE_TRABALHO, ARMAZENAMENTO_DE_TRABALHO).streamCache)) {
			paginas = documento.getNumberOfPages();
			try {
				extrator(prazo).writeText(documento, texto);
			} catch (TextoNormalizado.LimiteAtingido e) {
				// Já há texto até o limite; o resto do PDF não seria usado.
			} catch (ExtratorDeTextoLimitado.PesadoDemais e) {
				// Um PDF só longo demais (teto somado, prazo) ainda entrega o texto das páginas já lidas;
				// conteúdo montado para pesar, ou nenhum texto ainda, é recusado.
				if (e.hostil() || texto.vazio()) {
					return Resultado.de(Codigo.PESADO);
				}
				interrompida = true;
			}
		} catch (InvalidPasswordException e) {
			return Resultado.de(Codigo.COM_SENHA);
		} catch (IOException | RuntimeException e) {
			return Resultado.de(Codigo.CORROMPIDO);
		}

		if (texto.vazio()) {
			return Resultado.de(Codigo.SEM_TEXTO);
		}
		return new Resultado(Codigo.OK, new TextoExtraido(texto.toString(), paginas, texto.truncado() || interrompida));
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
	private static PDFTextStripper extrator(Duration prazo) {
		PDFTextStripper extrator = new ExtratorDeTextoLimitado(prazo);
		extrator.setDropThreshold(LIMIAR_DE_PARAGRAFO);
		extrator.setLineSeparator("\n");
		extrator.setParagraphEnd("\n");
		extrator.setPageEnd("\n\n");
		return extrator;
	}

	/**
	 * O texto vai em UTF-16 ({@code writeChars}), unidade por unidade: chega ao pai igual, mesmo com
	 * um substituto sem par, e o tamanho da saída fica fixo em função do tamanho do texto.
	 */
	static void escrever(Resultado resultado, OutputStream destino) throws IOException {
		DataOutputStream saida = new DataOutputStream(new BufferedOutputStream(destino));
		saida.writeByte(resultado.codigo().ordinal());
		if (resultado.codigo() == Codigo.OK) {
			TextoExtraido texto = resultado.texto();
			saida.writeInt(texto.paginas());
			saida.writeBoolean(texto.truncado());
			saida.writeInt(texto.texto().length());
			saida.writeChars(texto.texto());
		}
		saida.flush();
	}

	/** O inverso de {@link #escrever}, no pai. Qualquer byte fora do formato é {@link SaidaInvalida}. */
	static Resultado ler(byte[] saida) throws SaidaInvalida {
		if (saida.length == 0 || saida.length > MAIOR_SAIDA) {
			throw new SaidaInvalida("tamanho " + saida.length);
		}
		int numero = saida[0];
		if (numero < 0 || numero >= Codigo.values().length) {
			throw new SaidaInvalida("código " + numero);
		}
		Codigo codigo = Codigo.values()[numero];
		if (codigo != Codigo.OK) {
			if (saida.length != 1) {
				throw new SaidaInvalida("bytes depois do código");
			}
			return Resultado.de(codigo);
		}
		try {
			DataInputStream entrada = new DataInputStream(new ByteArrayInputStream(saida, 1, saida.length - 1));
			int paginas = entrada.readInt();
			int truncado = entrada.readUnsignedByte();
			int tamanho = entrada.readInt();
			if (paginas < 1 || truncado > 1 || tamanho < 1 || tamanho > LIMITE_DO_TEXTO
					|| saida.length != 1 + 4 + 1 + 4 + 2 * tamanho) {
				throw new SaidaInvalida("cabeçalho do texto");
			}
			char[] texto = new char[tamanho];
			for (int i = 0; i < tamanho; i++) {
				texto[i] = entrada.readChar();
			}
			return new Resultado(Codigo.OK, new TextoExtraido(new String(texto), paginas, truncado == 1));
		} catch (SaidaInvalida e) {
			throw e;
		} catch (IOException e) {
			throw new SaidaInvalida("texto incompleto");
		}
	}
}
