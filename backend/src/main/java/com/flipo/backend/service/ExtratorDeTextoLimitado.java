package com.flipo.backend.service;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType3CharProc;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * {@link PDFTextStripper} com tetos de custo, para um PDF pequeno e hostil não esgotar a memória nem a
 * CPU do servidor. O PDFBox descomprime e interpreta o conteúdo das páginas sem limite próprio: um
 * fluxo Flate de 300 KB pode virar um literal de 300 MB, um bilhão de operadores, milhões de
 * {@code q} aninhados ou uma fonte de 1 GB, ou ser reaproveitado por muitas páginas. Passar de um
 * teto lança {@link PesadoDemais}.
 *
 * <ul>
 *   <li><b>Bytes descomprimidos</b>: antes de o PDFBox usar um fluxo, ele é descomprimido só para
 *       contar os bytes, para um descarte, e a contagem para no teto: não chega a descomprimir o
 *       volume todo. Entram o conteúdo de cada página, form XObject e glifo Type 3, a cada vez que é
 *       usado (um fluxo compartilhado por dez páginas conta dez vezes), e, uma vez por extração, os
 *       fluxos da fonte de cada {@code Tf} ({@code FontFile}, {@code FontFile2}, {@code FontFile3},
 *       {@code ToUnicode}, {@code Encoding} e {@code CIDToGIDMap}), antes de o PDFBox carregar a
 *       fonte. Há um teto por fluxo, porque o PDFBox monta em memória todos os operandos de um
 *       operador (um fluxo só de números ocupa umas 15 vezes o seu tamanho), e um somado na extração
 *       toda. Só filtros de dados são aceitos: um filtro de imagem (DCT, JPX, JBIG2, CCITT) num
 *       desses fluxos não tem uso legítimo e decodificá-lo, até para contar, já pode esgotar a
 *       memória.</li>
 *   <li><b>Glifos por página</b>: o PDFBox guarda cada glifo da página até o fim dela, e um literal
 *       dentro do teto de bytes ainda pode ter milhões deles.</li>
 *   <li><b>Profundidade da pilha gráfica</b>: cada {@code q} guarda uma cópia do estado gráfico.</li>
 *   <li><b>Prazo</b>: conferido a cada página, operador e glifo, cobre o que os outros deixam passar.</li>
 * </ul>
 *
 * <p>O que estes tetos não alcançam fica com o processo filho onde a extração roda
 * ({@link ProcessoDeExtracaoPdf}), com heap e prazo próprios: o índice do arquivo, que o
 * {@code Loader.loadPDF} lê antes de qualquer teto (uma tabela XRef com um {@code /Size} absurdo
 * esgota a memória); a expansão de {@code bfrange} de um {@code ToUnicode} de poucos KB, que o
 * {@code CMapParser} transforma em milhões de entradas dentro de um único {@code Tf}, onde o prazo
 * não alcança; e a interpretação da fonte em si, dentro dos 8 MB.
 *
 * <p>{@link PesadoDemais} é unchecked de propósito: o PDFBox engole {@code IOException} lançada dentro
 * do operador {@code Do} (que desenha um form) e seguiria em frente.
 */
final class ExtratorDeTextoLimitado extends PDFTextStripper {

	/**
	 * A extração para nos 100 000 caracteres, então só as primeiras páginas são lidas; uma página de
	 * texto tem dezenas de KB de conteúdo. O teto por fluxo deixa o pior caso de um fluxo só de
	 * números em torno de 120 MB de memória. Uma fonte embutida inteira, sem subconjunto, de mais de
	 * 8 MB (ex: CJK completa) também é recusada.
	 */
	static final long BYTES_POR_FLUXO = 8L * 1024 * 1024;
	static final long BYTES_NA_EXTRACAO = 32L * 1024 * 1024;

	/** Uma página densa tem poucos milhares de glifos. */
	static final int GLIFOS_POR_PAGINA = 100_000;

	/** Um PDF comum aninha {@code q} poucas dezenas de vezes. */
	static final int PROFUNDIDADE_DA_PILHA_GRAFICA = 10_000;

	private static final Set<COSName> FILTROS_DE_DADOS = Set.of(
			COSName.FLATE_DECODE, COSName.FLATE_DECODE_ABBREVIATION,
			COSName.LZW_DECODE, COSName.LZW_DECODE_ABBREVIATION,
			COSName.ASCII_HEX_DECODE, COSName.ASCII_HEX_DECODE_ABBREVIATION,
			COSName.ASCII85_DECODE, COSName.ASCII85_DECODE_ABBREVIATION,
			COSName.RUN_LENGTH_DECODE, COSName.RUN_LENGTH_DECODE_ABBREVIATION);

	private static final List<COSName> FLUXOS_DA_FONTE =
			List.of(COSName.TO_UNICODE, COSName.ENCODING, COSName.CID_TO_GID_MAP);
	private static final List<COSName> ARQUIVOS_DA_FONTE =
			List.of(COSName.FONT_FILE, COSName.FONT_FILE2, COSName.FONT_FILE3);

	/**
	 * O PDF passou de um dos tetos de custo da extração. {@link #hostil()} separa o conteúdo montado
	 * para pesar (um fluxo enorme, milhões de glifos numa página, pilha funda, filtro de imagem) de um
	 * PDF que só é longo demais (teto somado ou prazo): deste, o texto já lido ainda serve.
	 */
	static final class PesadoDemais extends RuntimeException {

		private final boolean hostil;

		PesadoDemais(String teto, boolean hostil) {
			super(teto, null, false, false);
			this.hostil = hostil;
		}

		boolean hostil() {
			return hostil;
		}
	}

	private final long prazoEmNanos;
	private final long inicio = System.nanoTime();
	private final Set<COSStream> fluxosDeFonteContados = Collections.newSetFromMap(new IdentityHashMap<>());

	private long bytesNaExtracao;
	private int glifosNaPagina;

	/** O prazo começa a contar aqui. */
	ExtratorDeTextoLimitado(Duration prazo) {
		this.prazoEmNanos = prazo.toNanos();
	}

	@Override
	public void processPage(PDPage page) throws IOException {
		conferirPrazo();
		glifosNaPagina = 0;
		Iterator<PDStream> fluxos = page.getContentStreams();
		while (fluxos.hasNext()) {
			contar(fluxos.next().getCOSObject());
		}
		super.processPage(page);
	}

	@Override
	public void showForm(PDFormXObject form) throws IOException {
		contar(form.getCOSObject());
		super.showForm(form);
	}

	@Override
	public void showTransparencyGroup(PDTransparencyGroup form) throws IOException {
		contar(form.getCOSObject());
		super.showTransparencyGroup(form);
	}

	@Override
	protected void processType3Stream(PDType3CharProc charProc, Matrix textRenderingMatrix) throws IOException {
		contar(charProc.getCOSObject());
		super.processType3Stream(charProc, textRenderingMatrix);
	}

	@Override
	protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
		conferirPrazo();
		String nome = operator.getName();
		if (OperatorName.SAVE.equals(nome) && getGraphicsStackSize() >= PROFUNDIDADE_DA_PILHA_GRAFICA) {
			throw new PesadoDemais("profundidade da pilha gráfica", true);
		}
		if (OperatorName.SET_FONT_AND_SIZE.equals(nome) && !operands.isEmpty()
				&& operands.getFirst() instanceof COSName nomeDaFonte) {
			contarFonte(nomeDaFonte);
		}
		super.processOperator(operator, operands);
	}

	@Override
	protected void processTextPosition(TextPosition text) {
		conferirPrazo();
		if (++glifosNaPagina > GLIFOS_POR_PAGINA) {
			throw new PesadoDemais("glifos por página", true);
		}
		super.processTextPosition(text);
	}

	private void conferirPrazo() {
		if (System.nanoTime() - inicio > prazoEmNanos) {
			throw new PesadoDemais("prazo", false);
		}
	}

	/**
	 * Conta os fluxos da fonte que o {@code Tf} vai carregar, a mesma busca que o PDFBox faz nos
	 * recursos atuais. Cada fluxo conta uma vez: o PDFBox guarda a fonte carregada.
	 */
	private void contarFonte(COSName nomeDaFonte) {
		PDResources recursos = getResources();
		COSDictionary fontes = recursos == null ? null : recursos.getCOSObject().getCOSDictionary(COSName.FONT);
		COSDictionary fonte = fontes == null ? null : fontes.getCOSDictionary(nomeDaFonte);
		if (fonte == null) {
			return;
		}
		List<COSDictionary> comDescritor = new ArrayList<>(List.of(fonte));
		COSArray descendentes = fonte.getCOSArray(COSName.DESCENDANT_FONTS);
		if (descendentes != null) {
			// getObject resolve a referência indireta ([6 0 R], o comum); iterar o array devolveria o
			// COSObject sem resolver. Os getCOSDictionary/getCOSStream de dicionário já resolvem.
			for (int i = 0; i < descendentes.size(); i++) {
				if (descendentes.getObject(i) instanceof COSDictionary descendente) {
					comDescritor.add(descendente);
				}
			}
		}
		for (COSDictionary dicionario : comDescritor) {
			contarFluxosDeFonte(dicionario, FLUXOS_DA_FONTE);
			COSDictionary descritor = dicionario.getCOSDictionary(COSName.FONT_DESC);
			if (descritor != null) {
				contarFluxosDeFonte(descritor, ARQUIVOS_DA_FONTE);
			}
		}
	}

	private void contarFluxosDeFonte(COSDictionary dicionario, List<COSName> chaves) {
		for (COSName chave : chaves) {
			COSStream fluxo = dicionario.getCOSStream(chave);
			if (fluxo != null && fluxosDeFonteContados.add(fluxo)) {
				contar(fluxo);
			}
		}
	}

	/**
	 * Descomprime o fluxo só para contar os bytes, sem guardá-los. Um fluxo malformado não é problema
	 * desta contagem: o PDFBox já o pula ao interpretar a página.
	 */
	private void contar(COSStream fluxo) {
		try (InputStream entrada = fluxo.createRawInputStream()) {
			List<Filter> filtros = filtros(fluxo);
			InputStream etapa = entrada;
			for (int i = 0; i < filtros.size() - 1; i++) {
				// Filtros encadeados (raros nesses fluxos): a etapa intermediária fica em memória,
				// limitada ao teto por fluxo.
				ByteArrayOutputStream intermediaria = new ByteArrayOutputStream();
				filtros.get(i).decode(etapa, new Limitada(intermediaria, false), fluxo, i);
				etapa = new ByteArrayInputStream(intermediaria.toByteArray());
			}
			OutputStream descarte = new Limitada(OutputStream.nullOutputStream(), true);
			if (filtros.isEmpty()) {
				etapa.transferTo(descarte);
			} else {
				filtros.getLast().decode(etapa, descarte, fluxo, filtros.size() - 1);
			}
		} catch (IOException e) {
			// Fluxo malformado: segue para o PDFBox, que o trata ao interpretar.
		}
	}

	private static List<Filter> filtros(COSStream fluxo) throws IOException {
		COSBase nomes = fluxo.getFilters();
		List<COSBase> itens = new ArrayList<>();
		if (nomes instanceof COSArray lista) {
			lista.forEach(itens::add);
		} else if (nomes != null) {
			itens.add(nomes);
		}
		List<Filter> filtros = new ArrayList<>();
		for (COSBase item : itens) {
			if (!(item instanceof COSName nome)) {
				throw new IOException("Filtro inválido");
			}
			if (!FILTROS_DE_DADOS.contains(nome)) {
				throw new PesadoDemais("filtro fora dos de dados", true);
			}
			filtros.add(FilterFactory.INSTANCE.getFilter(nome));
		}
		return filtros;
	}

	/**
	 * Repassa ao destino sem deixar um fluxo passar do teto por fluxo. A saída final de cada fluxo
	 * também conta no teto somado na extração.
	 */
	private final class Limitada extends OutputStream {

		private final OutputStream destino;
		private final boolean contaNaExtracao;
		private long escritos;

		Limitada(OutputStream destino, boolean contaNaExtracao) {
			this.destino = destino;
			this.contaNaExtracao = contaNaExtracao;
		}

		@Override
		public void write(int b) throws IOException {
			write(new byte[] {(byte) b}, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			escritos += len;
			if (escritos > BYTES_POR_FLUXO) {
				throw new PesadoDemais("bytes por fluxo", true);
			}
			if (contaNaExtracao) {
				bytesNaExtracao += len;
				if (bytesNaExtracao > BYTES_NA_EXTRACAO) {
					throw new PesadoDemais("bytes na extração", false);
				}
			}
			destino.write(b, off, len);
		}
	}
}
