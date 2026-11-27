package com.flipo.backend.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O formato da saída do processo filho, lido pelo pai: o que não está no formato é recusado. */
class ProcessoDeExtracaoPdfTest {

	private static byte[] escrito(ProcessoDeExtracaoPdf.Resultado resultado) throws IOException {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		ProcessoDeExtracaoPdf.escrever(resultado, saida);
		return saida.toByteArray();
	}

	/** O texto vai unidade UTF-16 por unidade: um substituto sem par chega igual. */
	@Test
	void oTextoChegaIgualAoQueOFilhoEscreveu() throws IOException {
		TextoExtraido texto = new TextoExtraido("Mitose é\n\ndivisão 🧬 \uD835", 3, true);

		ProcessoDeExtracaoPdf.Resultado lido =
				ProcessoDeExtracaoPdf.ler(escrito(new ProcessoDeExtracaoPdf.Resultado(ProcessoDeExtracaoPdf.Codigo.OK, texto)));

		assertThat(lido.codigo()).isEqualTo(ProcessoDeExtracaoPdf.Codigo.OK);
		assertThat(lido.texto()).isEqualTo(texto);
	}

	@Test
	void oTextoNoLimiteCabeNaMaiorSaida() throws IOException {
		TextoExtraido texto = new TextoExtraido("x".repeat(ProcessoDeExtracaoPdf.LIMITE_DO_TEXTO), 1, true);
		byte[] saida = escrito(new ProcessoDeExtracaoPdf.Resultado(ProcessoDeExtracaoPdf.Codigo.OK, texto));

		assertThat(saida).hasSize(ProcessoDeExtracaoPdf.MAIOR_SAIDA);
		assertThat(ProcessoDeExtracaoPdf.ler(saida).texto()).isEqualTo(texto);
	}

	@Test
	void codigoSemTextoEhSoUmByte() throws IOException {
		byte[] saida = escrito(ProcessoDeExtracaoPdf.Resultado.de(ProcessoDeExtracaoPdf.Codigo.COM_SENHA));

		assertThat(saida).hasSize(1);
		assertThat(ProcessoDeExtracaoPdf.ler(saida).codigo()).isEqualTo(ProcessoDeExtracaoPdf.Codigo.COM_SENHA);
	}

	@Test
	void saidaForaDoFormatoEhRecusada() throws IOException {
		byte[] valida = escrito(new ProcessoDeExtracaoPdf.Resultado(ProcessoDeExtracaoPdf.Codigo.OK,
				new TextoExtraido("Texto", 1, false)));
		byte[] textoAMais = Arrays.copyOf(valida, valida.length + 2);
		byte[] textoAMenos = Arrays.copyOf(valida, valida.length - 1);
		byte[] truncadoQueNaoEhBooleano = valida.clone();
		truncadoQueNaoEhBooleano[5] = 7;

		for (byte[] saida : new byte[][] {
				new byte[0], {42}, {-1}, {(byte) ProcessoDeExtracaoPdf.Codigo.PESADO.ordinal(), 0},
				textoAMais, textoAMenos, truncadoQueNaoEhBooleano, new byte[ProcessoDeExtracaoPdf.MAIOR_SAIDA + 1]}) {
			assertThatThrownBy(() -> ProcessoDeExtracaoPdf.ler(saida))
					.isInstanceOf(ProcessoDeExtracaoPdf.SaidaInvalida.class);
		}
	}
}
