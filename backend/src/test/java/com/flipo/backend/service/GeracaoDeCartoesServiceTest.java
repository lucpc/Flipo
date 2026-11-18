package com.flipo.backend.service;

import com.flipo.backend.ia.CartaoSugerido;
import com.flipo.backend.ia.GeradorDeCartoes;
import com.flipo.backend.ia.GeradorDeCartoesFake;
import com.flipo.backend.ia.NivelCartao;
import com.flipo.backend.model.Materia;
import com.flipo.backend.model.Usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeracaoDeCartoesServiceTest {

	private static final String MENSAGEM_FALHA = "Não foi possível gerar sugestões agora. Tente novamente.";

	@Mock
	private MateriaService materiaService;

	@Mock
	private ObjectProvider<GeradorDeCartoes> geradores;

	private final GeradorDeCartoesFake gerador = new GeradorDeCartoesFake();

	private GeracaoDeCartoesService service;

	private final UUID usuarioId = UUID.randomUUID();
	private final UUID materiaId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = new GeracaoDeCartoesService(materiaService, geradores);
	}

	private void comMateriaDoUsuarioEGerador() {
		when(materiaService.buscarPorIdEUsuario(materiaId, usuarioId))
				.thenReturn(new Materia(new Usuario("Nome", "dono@flipo.test", "hash"), "Biologia"));
		when(geradores.getIfAvailable()).thenReturn(gerador);
	}

	@Test
	void gerarRepassaTextoQuantidadeENivelAoGeradorEDevolveAsSugestoes() {
		comMateriaDoUsuarioEGerador();

		List<CartaoSugerido> sugestoes = service.gerar(materiaId, usuarioId, "texto", 3, NivelCartao.APLICACAO);

		assertThat(sugestoes).extracting(CartaoSugerido::pergunta)
				.containsExactly("Pergunta 1", "Pergunta 2", "Pergunta 3");
		assertThat(gerador.chamadas())
				.containsExactly(new GeradorDeCartoesFake.Chamada("texto", 3, NivelCartao.APLICACAO));
	}

	@Test
	void gerarChecaAPosseDaMateriaAntesDeProcurarOGerador() {
		when(materiaService.buscarPorIdEUsuario(materiaId, usuarioId))
				.thenThrow(new RecursoNaoEncontradoException());

		assertThatThrownBy(() -> service.gerar(materiaId, usuarioId, "texto", 10, NivelCartao.DEFINICAO))
				.isInstanceOf(RecursoNaoEncontradoException.class);

		verify(geradores, never()).getIfAvailable();
		assertThat(gerador.chamadas()).isEmpty();
	}

	@Test
	void gerarSemGeradorConfiguradoLancaGeracaoIndisponivel() {
		when(materiaService.buscarPorIdEUsuario(materiaId, usuarioId))
				.thenReturn(new Materia(new Usuario("Nome", "dono@flipo.test", "hash"), "Biologia"));
		when(geradores.getIfAvailable()).thenReturn(null);

		assertThatThrownBy(() -> service.gerar(materiaId, usuarioId, "texto", 10, NivelCartao.DEFINICAO))
				.isInstanceOf(GeracaoIndisponivelException.class)
				.hasMessage("A geração de cartões por IA não está configurada neste servidor.");
	}

	@Test
	void gerarTraduzExcecaoDoGeradorNumaFalhaGenericaSemAMensagemOriginal() {
		comMateriaDoUsuarioEGerador();
		gerador.falharCom(new IllegalStateException("HTTP 401 chave sk-ant-segredo"));

		assertThatThrownBy(() -> service.gerar(materiaId, usuarioId, "texto", 10, NivelCartao.DEFINICAO))
				.isInstanceOf(FalhaNaGeracaoException.class)
				.hasMessage(MENSAGEM_FALHA)
				.hasNoCause();
	}

	@Test
	void gerarRejeitaSaidaNulaVaziaComItemNuloOuComCampoEmBranco() {
		comMateriaDoUsuarioEGerador();

		for (List<CartaoSugerido> saida : Arrays.asList(
				null,
				List.<CartaoSugerido>of(),
				Arrays.asList(new CartaoSugerido("p", "r"), null),
				List.of(new CartaoSugerido("", "r")),
				List.of(new CartaoSugerido("p", null)))) {
			gerador.devolver(saida);
			assertThatThrownBy(() -> service.gerar(materiaId, usuarioId, "texto", 10, NivelCartao.DEFINICAO))
					.isInstanceOf(FalhaNaGeracaoException.class)
					.hasMessage(MENSAGEM_FALHA);
		}
	}

	@Test
	void gerarCortaOExcedenteQuandoOGeradorDevolveMaisDoQueOPedido() {
		comMateriaDoUsuarioEGerador();
		gerador.devolver(List.of(
				new CartaoSugerido("p1", "r1"), new CartaoSugerido("p2", "r2"), new CartaoSugerido("p3", "r3")));

		List<CartaoSugerido> sugestoes = service.gerar(materiaId, usuarioId, "texto", 2, NivelCartao.DEFINICAO);

		assertThat(sugestoes).extracting(CartaoSugerido::pergunta).containsExactly("p1", "p2");
	}

	@Test
	void gerarIgnoraItemMalformadoQueFicaForaDoCorte() {
		comMateriaDoUsuarioEGerador();
		List<CartaoSugerido> onze = new ArrayList<>(IntStream.rangeClosed(1, 10)
				.mapToObj(i -> new CartaoSugerido("p" + i, "r" + i))
				.toList());
		onze.add(new CartaoSugerido("p11", " "));
		gerador.devolver(onze);

		List<CartaoSugerido> sugestoes = service.gerar(materiaId, usuarioId, "texto", 10, NivelCartao.DEFINICAO);

		assertThat(sugestoes).hasSize(10).extracting(CartaoSugerido::pergunta).endsWith("p10");
	}

	@Test
	void gerarAceitaMenosSugestoesDoQueOPedido() {
		comMateriaDoUsuarioEGerador();
		gerador.devolver(List.of(new CartaoSugerido("p1", "r1")));

		assertThat(service.gerar(materiaId, usuarioId, "texto", 10, NivelCartao.DEFINICAO)).hasSize(1);
	}
}
