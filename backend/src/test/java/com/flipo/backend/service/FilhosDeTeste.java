package com.flipo.backend.service;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Processos filhos no lugar do {@link ProcessoDeExtracaoPdf}, para o {@code ExtracaoDeTextoPdfServiceTest}
 * alcançar o que o filho de verdade não faz: travar, escrever sem parar, devolver uma saída fora do
 * formato e mostrar o que herdou do pai. Só existem no classpath dos testes.
 */
final class FilhosDeTeste {

	/** A propriedade que o teste define no pai e que não pode chegar ao filho. */
	static final String PROPRIEDADE_DO_PAI = "flipo.teste.propriedade-do-pai";

	private FilhosDeTeste() {
	}

	public static final class QueNaoTermina {

		public static void main(String[] args) throws InterruptedException {
			Thread.sleep(Long.MAX_VALUE);
		}
	}

	public static final class ComSaidaSemFim {

		public static void main(String[] args) throws IOException {
			OutputStream saida = System.out;
			byte[] bloco = new byte[64 * 1024];
			while (true) {
				saida.write(bloco);
			}
		}
	}

	/** Sai com 137, o código de um processo encerrado pelo SIGKILL (como o do OOM killer do kernel). */
	public static final class ComMortePorSinal {

		public static void main(String[] args) {
			Runtime.getRuntime().halt(137);
		}
	}

	public static final class ComCodigoQueNaoExiste {

		public static void main(String[] args) {
			System.out.write(42);
			System.out.flush();
		}
	}

	/**
	 * Devolve, no formato do filho de verdade, quantas variáveis de ambiente herdou, a propriedade do
	 * teste e a pasta temporária.
	 */
	public static final class QueDevolveOQueHerdou {

		public static void main(String[] args) throws IOException {
			String texto = "ambiente=" + System.getenv().size() + " propriedade=" + System.getProperty(PROPRIEDADE_DO_PAI)
					+ " tmpdir=" + System.getProperty("java.io.tmpdir");
			ProcessoDeExtracaoPdf.escrever(new ProcessoDeExtracaoPdf.Resultado(ProcessoDeExtracaoPdf.Codigo.OK,
					new TextoExtraido(texto, 1, false)), System.out);
		}
	}
}
