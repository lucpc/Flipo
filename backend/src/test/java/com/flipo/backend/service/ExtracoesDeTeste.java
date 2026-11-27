package com.flipo.backend.service;

import java.time.Duration;
import java.util.concurrent.Semaphore;

/** Monta o {@link ExtracaoDeTextoPdfService} com o construtor de teste, para testes fora deste pacote. */
public final class ExtracoesDeTeste {

	private ExtracoesDeTeste() {
	}

	/** Sem nenhuma vaga de extração, e esperando só 50 ms por uma. */
	public static ExtracaoDeTextoPdfService semVagas() {
		return new ExtracaoDeTextoPdfService(Duration.ofSeconds(10), ExtracaoDeTextoPdfService.HEAP_PADRAO,
				Duration.ofSeconds(15), new Semaphore(0, true), Duration.ofMillis(50), ProcessoDeExtracaoPdf.class.getName());
	}
}
