package com.flipo.backend.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextoNormalizadoTest {

	private static String normalizar(String cru) throws IOException {
		TextoNormalizado texto = new TextoNormalizado(1_000);
		texto.write(cru);
		assertThat(texto.truncado()).isFalse();
		return texto.toString();
	}

	@Test
	void espacosETabsRepetidosViramUmEspaco() throws IOException {
		assertThat(normalizar("uma  frase\t\tcom \t espaços")).isEqualTo("uma frase com espaços");
	}

	@Test
	void espacoInseparavelContaComoEspaco() throws IOException {
		assertThat(normalizar("dez  reais")).isEqualTo("dez reais");
	}

	@Test
	void quebraDeLinhaSimplesEhPreservadaSemOsEspacosEmVolta() throws IOException {
		assertThat(normalizar("linha um   \n   linha dois")).isEqualTo("linha um\nlinha dois");
	}

	@Test
	void quebrasSeguidasParamEmUmaLinhaEmBranco() throws IOException {
		assertThat(normalizar("parágrafo um\n\n\n\n \t \n\nparágrafo dois\n\nparágrafo três"))
				.isEqualTo("parágrafo um\n\nparágrafo dois\n\nparágrafo três");
	}

	@Test
	void retornoDeCarroViraQuebraDeLinha() throws IOException {
		assertThat(normalizar("um\r\ndois\rtrês\r\n\r\n\r\nquatro")).isEqualTo("um\ndois\ntrês\n\nquatro");
	}

	@Test
	void pontasSaemSemEspacosNemQuebras() throws IOException {
		assertThat(normalizar(" \n\t  texto no meio \n\n \t")).isEqualTo("texto no meio");
	}

	@Test
	void textoSoComEspacosFicaVazio() throws IOException {
		TextoNormalizado texto = new TextoNormalizado(1_000);
		texto.write(" \n\t\n\f ");
		assertThat(texto.vazio()).isTrue();
		assertThat(texto.toString()).isEmpty();
	}

	@Test
	void textoExatamenteNoLimiteNaoEhTruncado() throws IOException {
		TextoNormalizado texto = new TextoNormalizado(10);
		texto.write("0123456789   \n\n  ");
		assertThat(texto.truncado()).isFalse();
		assertThat(texto.toString()).isEqualTo("0123456789");
	}

	@Test
	void textoAlemDoLimiteEhCortadoNoLimiteEInterrompeAExtracao() {
		TextoNormalizado texto = new TextoNormalizado(10);
		assertThatThrownBy(() -> texto.write("0123456789X e mais"))
				.isInstanceOf(TextoNormalizado.LimiteAtingido.class);
		assertThat(texto.truncado()).isTrue();
		assertThat(texto.toString()).isEqualTo("0123456789");
		// Depois do corte, nada mais entra.
		assertThatThrownBy(() -> texto.write("Y")).isInstanceOf(TextoNormalizado.LimiteAtingido.class);
		assertThat(texto.toString()).isEqualTo("0123456789");
	}

	@Test
	void corteNaoParteParSubstituto() {
		// "😀" ocupa duas unidades UTF-16; o limite cai entre elas.
		TextoNormalizado texto = new TextoNormalizado(10);
		assertThatThrownBy(() -> texto.write("012345678😀fim"))
				.isInstanceOf(TextoNormalizado.LimiteAtingido.class);
		assertThat(texto.truncado()).isTrue();
		assertThat(texto.toString()).isEqualTo("012345678");
	}

	@Test
	void corteNaoDeixaEspacoNoFim() {
		TextoNormalizado texto = new TextoNormalizado(10);
		assertThatThrownBy(() -> texto.write("012345678\n\nseguinte"))
				.isInstanceOf(TextoNormalizado.LimiteAtingido.class);
		assertThat(texto.toString()).isEqualTo("012345678");
	}
}
