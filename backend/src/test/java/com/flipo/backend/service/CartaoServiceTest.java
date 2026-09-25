package com.flipo.backend.service;

import com.flipo.backend.model.Cartao;
import com.flipo.backend.model.Materia;
import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.CartaoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartaoServiceTest {

	@Mock
	private CartaoRepository cartaoRepository;

	private CartaoService cartaoService;

	private final UUID usuarioId = UUID.randomUUID();
	private final UUID cartaoId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		cartaoService = new CartaoService(cartaoRepository);
	}

	@Test
	void buscarPorIdEUsuarioDevolveCartaoQuandoSuaMateriaPertenceAoUsuario() {
		Materia materia = new Materia(new Usuario("Nome", "dono@flipo.test", "hash"), "História");
		Cartao cartao = new Cartao(materia, "pergunta", "resposta", Cartao.ORIGEM_MANUAL);
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.of(cartao));

		Cartao encontrado = cartaoService.buscarPorIdEUsuario(cartaoId, usuarioId);

		assertThat(encontrado).isSameAs(cartao);
	}

	@Test
	void buscarPorIdEUsuarioLancaRecursoNaoEncontradoQuandoCartaoPertenceAMateriaDeOutroUsuario() {
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> cartaoService.buscarPorIdEUsuario(cartaoId, usuarioId))
				.isInstanceOf(RecursoNaoEncontradoException.class);
	}

	@Test
	void buscarPorIdEUsuarioLancaRecursoNaoEncontradoQuandoIdNaoExisteComAMesmaExcecaoDoCasoDeOutroDono() {
		UUID idInexistente = UUID.randomUUID();
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(idInexistente, usuarioId))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> cartaoService.buscarPorIdEUsuario(idInexistente, usuarioId))
				.isInstanceOf(RecursoNaoEncontradoException.class)
				.hasMessage("Recurso não encontrado.");
	}
}
