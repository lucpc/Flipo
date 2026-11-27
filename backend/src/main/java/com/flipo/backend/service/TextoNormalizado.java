package com.flipo.backend.service;

import java.io.IOException;
import java.io.Writer;

/**
 * {@link Writer} que recebe o texto cru do {@code PDFTextStripper} e guarda só a versão normalizada
 * do {@code POST /api/pdf/extrair-texto} (docs/03-contrato-api.md): espaços e tabs repetidos viram
 * um espaço, espaços colados a uma quebra de linha somem, quebras seguidas param em uma linha em
 * branco e as pontas ficam sem espaço nem quebra.
 *
 * <p>Normaliza à medida que recebe, para não guardar o texto cru inteiro: quando o texto normalizado
 * passa de {@code limite} unidades UTF-16, corta no limite (sem deixar meio par substituto nem
 * espaço no fim), marca {@link #truncado()} e lança {@link LimiteAtingido} para interromper a
 * extração. Um PDF pequeno pode ter muito texto comprimido, e o resto não seria usado.
 */
final class TextoNormalizado extends Writer {

	/** Interrompe o {@code PDFTextStripper}: o texto já passou do limite. */
	static final class LimiteAtingido extends IOException {
	}

	private final int limite;
	private final StringBuilder texto = new StringBuilder();
	private boolean espacoPendente;
	private int quebrasPendentes;
	private boolean ultimoFoiRetorno;
	private boolean truncado;

	TextoNormalizado(int limite) {
		this.limite = limite;
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws LimiteAtingido {
		if (truncado) {
			throw new LimiteAtingido();
		}
		for (int i = off; i < off + len; i++) {
			acrescentar(cbuf[i]);
		}
	}

	private void acrescentar(char c) throws LimiteAtingido {
		boolean retornoSeguidoDeQuebra = ultimoFoiRetorno && c == '\n';
		ultimoFoiRetorno = c == '\r';
		if (retornoSeguidoDeQuebra) {
			return;
		}
		if (c == '\n' || c == '\r') {
			quebrasPendentes = Math.min(2, quebrasPendentes + 1);
			espacoPendente = false;
			return;
		}
		if (ehEspaco(c)) {
			espacoPendente = quebrasPendentes == 0;
			return;
		}

		if (!texto.isEmpty()) {
			if (quebrasPendentes > 0) {
				texto.append(quebrasPendentes == 1 ? "\n" : "\n\n");
			} else if (espacoPendente) {
				texto.append(' ');
			}
		}
		quebrasPendentes = 0;
		espacoPendente = false;
		texto.append(c);

		if (texto.length() > limite) {
			cortarNoLimite();
			throw new LimiteAtingido();
		}
	}

	private void cortarNoLimite() {
		truncado = true;
		texto.setLength(limite);
		while (!texto.isEmpty()) {
			char ultimo = texto.charAt(texto.length() - 1);
			if (!Character.isHighSurrogate(ultimo) && ultimo != ' ' && ultimo != '\n') {
				break;
			}
			texto.setLength(texto.length() - 1);
		}
	}

	private static boolean ehEspaco(char c) {
		return c == '\t' || c == '\u000B' || c == '\f' || Character.getType(c) == Character.SPACE_SEPARATOR;
	}

	boolean truncado() {
		return truncado;
	}

	boolean vazio() {
		return texto.isEmpty();
	}

	@Override
	public String toString() {
		return texto.toString();
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}
}
