package com.flipo.backend.service;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

import javax.imageio.ImageIO;

/**
 * PDFs gerados na hora com o PDFBox, para os testes não dependerem de arquivos binários no
 * repositório. Cada página é uma lista de linhas em Helvetica (fonte Standard14, sem embutir); linhas
 * vazias seguidas abrem um espaço que o PDFBox lê como novo parágrafo.
 */
public final class PdfsDeTeste {

	private PdfsDeTeste() {
	}

	public static byte[] comPaginas(List<List<String>> paginas) {
		return salvar(montar(paginas, 12), null, CompressParameters.DEFAULT_COMPRESSION);
	}

	/**
	 * Corpo 12 com o espaço entre linhas em {@code entrelinha} vezes o corpo (1,5 é o espaçamento da
	 * ABNT; o padrão dos outros geradores é 1,2).
	 */
	public static byte[] comEntrelinha(List<List<String>> paginas, float entrelinha) {
		return salvar(montar(paginas, 12, entrelinha), null, CompressParameters.DEFAULT_COMPRESSION);
	}

	/** Linhas pequenas (corpo 6), para caber muito texto por página. */
	public static byte[] comPaginasMiudas(List<List<String>> paginas) {
		return salvar(montar(paginas, 6), null, CompressParameters.DEFAULT_COMPRESSION);
	}

	/** Sem fluxos de objetos: a tabela de referências tradicional, de onde o PDFBox recupera páginas. */
	public static byte[] semCompressao(List<List<String>> paginas) {
		return salvar(montar(paginas, 12), null, CompressParameters.NO_COMPRESSION);
	}

	public static byte[] comPaginaEmBranco() {
		PDDocument documento = new PDDocument();
		documento.addPage(new PDPage());
		return salvar(documento, null, CompressParameters.DEFAULT_COMPRESSION);
	}

	public static byte[] comSenhas(List<List<String>> paginas, String senhaDoDono, String senhaDoUsuario) {
		StandardProtectionPolicy politica =
				new StandardProtectionPolicy(senhaDoDono, senhaDoUsuario, new AccessPermission());
		politica.setEncryptionKeyLength(128);
		return salvar(montar(paginas, 12), politica, CompressParameters.DEFAULT_COMPRESSION);
	}

	/**
	 * Um PDF válido de pelo menos {@code tamanho} bytes: o texto mais um fluxo de bytes sem
	 * compressão pendurado no catálogo, que não aparece na página.
	 */
	public static byte[] comTamanho(List<List<String>> paginas, int tamanho) {
		PDDocument documento = montar(paginas, 12);
		COSStream enchimento = documento.getDocument().createCOSStream();
		try (OutputStream saida = enchimento.createRawOutputStream()) {
			saida.write(new byte[tamanho]);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		documento.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("Enchimento"), enchimento);
		return salvar(documento, null, CompressParameters.DEFAULT_COMPRESSION);
	}

	/**
	 * Páginas com um conteúdo escrito à mão, comprimido em Flate: {@code cabecalho}, {@code unidade}
	 * repetida {@code repeticoes} vezes e {@code rodape}, com a Helvetica como {@code /F1}. O conteúdo
	 * passa direto pelo compressor, sem ficar inteiro na memória, então um PDF de poucos KB pode
	 * descomprimir para dezenas de MB. Com {@code compartilhado}, todas as páginas apontam para o
	 * mesmo fluxo.
	 */
	public static byte[] comConteudoBruto(int paginas, boolean compartilhado,
			String cabecalho, String unidade, long repeticoes, String rodape) {
		return comTextoSeguidoDeConteudoBruto(List.of(), paginas, compartilhado, cabecalho, unidade, repeticoes, rodape);
	}

	/** Primeiro uma página de texto comum para cada item de {@code textos}, depois as de conteúdo bruto. */
	public static byte[] comTextoSeguidoDeConteudoBruto(List<String> textos, int paginasBrutas, boolean compartilhado,
			String cabecalho, String unidade, long repeticoes, String rodape) {
		PDDocument documento = montar(textos.stream().map(List::of).toList(), 12);
		PDResources recursos = recursosComHelvetica();
		COSStream fluxo = null;
		try {
			for (int p = 0; p < paginasBrutas; p++) {
				if (fluxo == null || !compartilhado) {
					fluxo = documento.getDocument().createCOSStream();
					try (OutputStream saida = fluxo.createOutputStream(COSName.FLATE_DECODE)) {
						saida.write(cabecalho.getBytes(StandardCharsets.ISO_8859_1));
						byte[] repetida = unidade.getBytes(StandardCharsets.ISO_8859_1);
						for (long i = 0; i < repeticoes; i++) {
							saida.write(repetida);
						}
						saida.write(rodape.getBytes(StandardCharsets.ISO_8859_1));
					}
				}
				adicionarPagina(documento, recursos, fluxo);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return salvar(documento, null, CompressParameters.DEFAULT_COMPRESSION);
	}

	/**
	 * Uma página cujo conteúdo declara um filtro de imagem DCT, com um JPEG de 8x8 cujo cabeçalho diz
	 * 20000x20000: decodificado, ele pede gigabytes de memória.
	 */
	public static byte[] comConteudoEmDct() {
		byte[] jpeg;
		try {
			ByteArrayOutputStream saida = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", saida);
			jpeg = saida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		for (int i = 0; i < jpeg.length - 8; i++) {
			if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xC0) {
				jpeg[i + 5] = (byte) (20000 >> 8);
				jpeg[i + 6] = (byte) 20000;
				jpeg[i + 7] = (byte) (20000 >> 8);
				jpeg[i + 8] = (byte) 20000;
				break;
			}
		}
		PDDocument documento = new PDDocument();
		COSStream fluxo = documento.getDocument().createCOSStream();
		escreverBruto(fluxo, jpeg);
		fluxo.setItem(COSName.FILTER, COSName.DCT_DECODE);
		adicionarPagina(documento, recursosComHelvetica(), fluxo);
		return salvar(documento, null, CompressParameters.DEFAULT_COMPRESSION);
	}

	/**
	 * Uma página que usa uma fonte de subtipo {@code subtipo} cujo arquivo embutido, na chave
	 * {@code chaveDoArquivo} do descritor, é {@code bytes} zeros comprimidos em Flate.
	 */
	public static byte[] comArquivoDeFonteDeZeros(COSName subtipo, COSName chaveDoArquivo, long bytes) {
		PDDocument documento = new PDDocument();
		return comFonte(documento, fonteDeZeros(documento, subtipo, chaveDoArquivo, bytes));
	}

	/**
	 * Uma Type0 cuja descendente {@code CIDFontType2} tem um {@code FontFile2} de {@code bytes} zeros,
	 * como o Word e o LibreOffice gravam texto Unicode. A descendente sai como referência indireta
	 * ({@code [6 0 R]}): o PDFBox grava assim todo dicionário dentro de um array.
	 */
	public static byte[] comType0DeArquivoDeZeros(long bytes) {
		PDDocument documento = new PDDocument();
		COSDictionary descendente = fonteDeZeros(documento, COSName.CID_FONT_TYPE2, COSName.FONT_FILE2, bytes);
		COSDictionary sistema = new COSDictionary();
		sistema.setString(COSName.REGISTRY, "Adobe");
		sistema.setString(COSName.ORDERING, "Identity");
		sistema.setInt(COSName.SUPPLEMENT, 0);
		descendente.setItem(COSName.CIDSYSTEMINFO, sistema);
		COSDictionary fonte = new COSDictionary();
		fonte.setItem(COSName.TYPE, COSName.FONT);
		fonte.setItem(COSName.SUBTYPE, COSName.TYPE0);
		fonte.setName(COSName.BASE_FONT, "Xx");
		fonte.setItem(COSName.ENCODING, COSName.IDENTITY_H);
		COSArray descendentes = new COSArray();
		descendentes.add(descendente);
		fonte.setItem(COSName.DESCENDANT_FONTS, descendentes);
		return comFonte(documento, fonte);
	}

	private static COSDictionary fonteDeZeros(PDDocument documento, COSName subtipo, COSName chaveDoArquivo, long bytes) {
		COSStream arquivo = documento.getDocument().createCOSStream();
		try (OutputStream saida = arquivo.createOutputStream(COSName.FLATE_DECODE)) {
			byte[] mega = new byte[1024 * 1024];
			for (long escritos = 0; escritos < bytes; escritos += mega.length) {
				saida.write(mega, 0, (int) Math.min(mega.length, bytes - escritos));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		COSDictionary descritor = new COSDictionary();
		descritor.setItem(COSName.TYPE, COSName.FONT_DESC);
		descritor.setName(COSName.FONT_NAME, "Xx");
		descritor.setInt(COSName.FLAGS, 32);
		descritor.setItem(chaveDoArquivo, arquivo);
		COSDictionary fonte = new COSDictionary();
		fonte.setItem(COSName.TYPE, COSName.FONT);
		fonte.setItem(COSName.SUBTYPE, subtipo);
		fonte.setName(COSName.BASE_FONT, "Xx");
		fonte.setItem(COSName.FONT_DESC, descritor);
		return fonte;
	}

	/** Uma página que escreve "oi" com {@code fonte} como {@code /F1}. */
	private static byte[] comFonte(PDDocument documento, COSDictionary fonte) {
		PDResources recursos = new PDResources();
		COSDictionary fontes = new COSDictionary();
		fontes.setItem(COSName.getPDFName("F1"), fonte);
		recursos.getCOSObject().setItem(COSName.FONT, fontes);
		COSStream conteudo = documento.getDocument().createCOSStream();
		escreverBruto(conteudo, "BT /F1 12 Tf 72 700 Td (oi) Tj ET".getBytes(StandardCharsets.US_ASCII));
		adicionarPagina(documento, recursos, conteudo);
		return salvar(documento, null, CompressParameters.DEFAULT_COMPRESSION);
	}

	/**
	 * Uma página com a Helvetica cujo {@code ToUnicode} tem {@code faixas} faixas {@code bfrange} de 16
	 * milhões de códigos cada ({@code <xx000000> <xxFFFFFF>}). O arquivo tem menos de 1 KB, e o
	 * {@code CMapParser} do PDFBox cria uma entrada por código da faixa.
	 */
	public static byte[] comToUnicodeDeFaixasEnormes(int faixas) {
		StringBuilder cmap = new StringBuilder("/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n"
				+ "/CMapName /X def\n1 begincodespacerange <00000000> <FFFFFFFF> endcodespacerange\n"
				+ faixas + " beginbfrange\n");
		for (int i = 0; i < faixas; i++) {
			cmap.append(String.format("<%02X000000> <%02XFFFFFF> <0041>%n", i, i));
		}
		cmap.append("endbfrange\nendcmap CMapName currentdict /CMap defineresource pop end end\n");

		PDDocument documento = new PDDocument();
		COSStream toUnicode = documento.getDocument().createCOSStream();
		try (OutputStream saida = toUnicode.createOutputStream(COSName.FLATE_DECODE)) {
			saida.write(cmap.toString().getBytes(StandardCharsets.US_ASCII));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		COSDictionary fonte = new COSDictionary();
		fonte.setItem(COSName.TYPE, COSName.FONT);
		fonte.setItem(COSName.SUBTYPE, COSName.TYPE1);
		fonte.setName(COSName.BASE_FONT, "Helvetica");
		fonte.setItem(COSName.TO_UNICODE, toUnicode);
		return comFonte(documento, fonte);
	}

	/**
	 * Um PDF de uma página com a tabela de referências num fluxo XRef comprimido que declara
	 * {@code objetos} entradas, todas apontando para o catálogo. Escrito byte a byte: o PDFBox não grava
	 * uma tabela assim. Com 30 milhões de entradas o arquivo tem uns 300 KB, e o {@code Loader.loadPDF}
	 * guarda cada entrada num mapa antes de ler qualquer página.
	 */
	public static byte[] comTabelaXrefDeclarando(int objetos) {
		try {
			ByteArrayOutputStream pdf = new ByteArrayOutputStream();
			escreverTexto(pdf, "%PDF-1.7\n");
			byte[] conteudo = "BT /F1 12 Tf 72 700 Td (oi) Tj ET".getBytes(StandardCharsets.US_ASCII);
			List<String> objetosDoPdf = List.of(
					"<< /Type /Catalog /Pages 2 0 R >>",
					"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
					"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font"
							+ " << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>",
					"<< /Length " + conteudo.length + " >>\nstream\n" + new String(conteudo, StandardCharsets.US_ASCII)
							+ "\nendstream");
			int[] posicoes = new int[objetosDoPdf.size() + 1];
			for (int i = 0; i < objetosDoPdf.size(); i++) {
				posicoes[i + 1] = pdf.size();
				escreverTexto(pdf, (i + 1) + " 0 obj\n" + objetosDoPdf.get(i) + "\nendobj\n");
			}
			int posicaoDaTabela = pdf.size();

			// Entradas de /W [1 4 2]: tipo, posição, geração. O objeto 0 é livre, o 5 é a própria tabela.
			ByteArrayOutputStream comprimida = new ByteArrayOutputStream();
			try (DataOutputStream tabela = new DataOutputStream(
					new BufferedOutputStream(new DeflaterOutputStream(comprimida), 64 * 1024))) {
				tabela.writeByte(0);
				tabela.writeInt(0);
				tabela.writeShort(65535);
				for (int i = 1; i <= objetosDoPdf.size(); i++) {
					tabela.writeByte(1);
					tabela.writeInt(posicoes[i]);
					tabela.writeShort(0);
				}
				for (int i = objetosDoPdf.size() + 1; i < objetos; i++) {
					tabela.writeByte(1);
					tabela.writeInt(i == objetosDoPdf.size() + 1 ? posicaoDaTabela : posicoes[1]);
					tabela.writeShort(0);
				}
			}
			escreverTexto(pdf, (objetosDoPdf.size() + 1) + " 0 obj\n<< /Type /XRef /Size " + objetos
					+ " /W [1 4 2] /Root 1 0 R /Filter /FlateDecode /Length " + comprimida.size() + " >>\nstream\n");
			comprimida.writeTo(pdf);
			escreverTexto(pdf, "\nendstream\nendobj\nstartxref\n" + posicaoDaTabela + "\n%%EOF\n");
			return pdf.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void escreverTexto(OutputStream saida, String texto) throws IOException {
		saida.write(texto.getBytes(StandardCharsets.US_ASCII));
	}

	/** Uma página com {@code texto} na Liberation Sans, a TrueType que vem no jar do PDFBox, embutida inteira. */
	public static byte[] comFonteTrueTypeEmbutida(String texto) {
		PDDocument documento = new PDDocument();
		try (InputStream ttf = PDDocument.class.getResourceAsStream(
				"/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
			PDType0Font fonte = PDType0Font.load(documento, ttf, false);
			PDPage pagina = new PDPage();
			documento.addPage(pagina);
			try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
				conteudo.beginText();
				conteudo.setFont(fonte, 12);
				conteudo.newLineAtOffset(36, 760);
				conteudo.showText(texto);
				conteudo.endText();
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return salvar(documento, null, CompressParameters.DEFAULT_COMPRESSION);
	}

	private static PDResources recursosComHelvetica() {
		PDResources recursos = new PDResources();
		recursos.put(COSName.getPDFName("F1"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
		return recursos;
	}

	private static void adicionarPagina(PDDocument documento, PDResources recursos, COSStream conteudo) {
		PDPage pagina = new PDPage();
		pagina.setResources(recursos);
		pagina.setContents(new PDStream(conteudo));
		documento.addPage(pagina);
	}

	private static void escreverBruto(COSStream fluxo, byte[] dados) {
		try (OutputStream saida = fluxo.createRawOutputStream()) {
			saida.write(dados);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static PDDocument montar(List<List<String>> paginas, float corpo) {
		return montar(paginas, corpo, 1.2f);
	}

	private static PDDocument montar(List<List<String>> paginas, float corpo, float entrelinha) {
		PDDocument documento = new PDDocument();
		PDType1Font fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		try {
			for (List<String> linhas : paginas) {
				PDPage pagina = new PDPage();
				documento.addPage(pagina);
				try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
					conteudo.beginText();
					conteudo.setFont(fonte, corpo);
					conteudo.setLeading(corpo * entrelinha);
					conteudo.newLineAtOffset(36, 760);
					for (String linha : linhas) {
						conteudo.showText(linha);
						conteudo.newLine();
					}
					conteudo.endText();
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return documento;
	}

	private static byte[] salvar(PDDocument documento, StandardProtectionPolicy politica, CompressParameters compressao) {
		try (documento) {
			if (politica != null) {
				documento.protect(politica);
			}
			ByteArrayOutputStream saida = new ByteArrayOutputStream();
			documento.save(saida, compressao);
			return saida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
