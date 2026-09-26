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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartaoServiceTest {

	@Mock
	private CartaoRepository cartaoRepository;

	@Mock
	private MateriaService materiaService;

	private CartaoService cartaoService;

	private final UUID usuarioId = UUID.randomUUID();
	private final UUID cartaoId = UUID.randomUUID();
	private final UUID materiaId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		cartaoService = new CartaoService(cartaoRepository, materiaService);
	}

	private static Materia materia() {
		return new Materia(new Usuario("Nome", "dono@flipo.test", "hash"), "História");
	}

	@Test
	void buscarPorIdEUsuarioDevolveCartaoQuandoSuaMateriaPertenceAoUsuario() {
		Cartao cartao = new Cartao(materia(), "pergunta", "resposta", Cartao.ORIGEM_MANUAL);
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

	@Test
	void listarPorMateriaEUsuarioValidaPosseDaMateriaAntesDeListar() {
		Materia materia = materia();
		when(materiaService.buscarPorIdEUsuario(materiaId, usuarioId)).thenReturn(materia);
		when(cartaoRepository.findByMateriaIdAndMateria_Usuario_IdAndArquivado(materiaId, usuarioId, false))
				.thenReturn(List.of());

		cartaoService.listarPorMateriaEUsuario(materiaId, usuarioId, false);

		verify(materiaService).buscarPorIdEUsuario(materiaId, usuarioId);
	}

	@Test
	void listarPorMateriaEUsuarioLancaRecursoNaoEncontradoQuandoMateriaPertenceAOutroUsuarioENaoConsultaCartoes() {
		when(materiaService.buscarPorIdEUsuario(materiaId, usuarioId))
				.thenThrow(new RecursoNaoEncontradoException());

		assertThatThrownBy(() -> cartaoService.listarPorMateriaEUsuario(materiaId, usuarioId, false))
				.isInstanceOf(RecursoNaoEncontradoException.class);

		verify(cartaoRepository, never())
				.findByMateriaIdAndMateria_Usuario_IdAndArquivado(any(), any(), anyBoolean());
	}

	@Test
	void listarPorMateriaEUsuarioRepassaOFiltroDeArquivadoParaARepository() {
		when(materiaService.buscarPorIdEUsuario(materiaId, usuarioId)).thenReturn(materia());
		Cartao arquivado = new Cartao(materia(), "p", "r", Cartao.ORIGEM_MANUAL);
		arquivado.setArquivado(true);
		when(cartaoRepository.findByMateriaIdAndMateria_Usuario_IdAndArquivado(materiaId, usuarioId, true))
				.thenReturn(List.of(arquivado));

		List<Cartao> resultado = cartaoService.listarPorMateriaEUsuario(materiaId, usuarioId, true);

		assertThat(resultado).containsExactly(arquivado);
	}

	@Test
	void criarValidaPosseDaMateriaESalvaCartaoManual() {
		Materia materia = materia();
		when(materiaService.buscarPorIdEUsuario(materiaId, usuarioId)).thenReturn(materia);
		when(cartaoRepository.save(any(Cartao.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Cartao criado = cartaoService.criar(materiaId, usuarioId, "pergunta", "resposta");

		assertThat(criado.getPergunta()).isEqualTo("pergunta");
		assertThat(criado.getResposta()).isEqualTo("resposta");
		assertThat(criado.getOrigem()).isEqualTo(Cartao.ORIGEM_MANUAL);
		assertThat(criado.isArquivado()).isFalse();
		assertThat(criado.getMateria()).isSameAs(materia);
	}

	@Test
	void criarLancaRecursoNaoEncontradoENaoSalvaQuandoMateriaPertenceAOutroUsuario() {
		when(materiaService.buscarPorIdEUsuario(materiaId, usuarioId))
				.thenThrow(new RecursoNaoEncontradoException());

		assertThatThrownBy(() -> cartaoService.criar(materiaId, usuarioId, "pergunta", "resposta"))
				.isInstanceOf(RecursoNaoEncontradoException.class);

		verify(cartaoRepository, never()).save(any());
	}

	@Test
	void editarAtualizaPerguntaERespostaMasNaoAlteraArquivado() {
		Cartao cartao = new Cartao(materia(), "pergunta antiga", "resposta antiga", Cartao.ORIGEM_MANUAL);
		cartao.setArquivado(true);
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.of(cartao));
		when(cartaoRepository.save(any(Cartao.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Cartao editado = cartaoService.editar(cartaoId, usuarioId, "pergunta nova", "resposta nova");

		assertThat(editado.getPergunta()).isEqualTo("pergunta nova");
		assertThat(editado.getResposta()).isEqualTo("resposta nova");
		assertThat(editado.isArquivado()).isTrue();
	}

	@Test
	void editarLancaRecursoNaoEncontradoENaoSalvaQuandoCartaoPertenceAOutroUsuario() {
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> cartaoService.editar(cartaoId, usuarioId, "p", "r"))
				.isInstanceOf(RecursoNaoEncontradoException.class);

		verify(cartaoRepository, never()).save(any());
	}

	@Test
	void arquivarMarcaArquivadoTrueMasNaoAlteraPerguntaOuResposta() {
		Cartao cartao = new Cartao(materia(), "pergunta", "resposta", Cartao.ORIGEM_MANUAL);
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.of(cartao));
		when(cartaoRepository.save(any(Cartao.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Cartao arquivado = cartaoService.arquivar(cartaoId, usuarioId);

		assertThat(arquivado.isArquivado()).isTrue();
		assertThat(arquivado.getPergunta()).isEqualTo("pergunta");
		assertThat(arquivado.getResposta()).isEqualTo("resposta");
	}

	@Test
	void arquivarLancaRecursoNaoEncontradoQuandoCartaoPertenceAOutroUsuario() {
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> cartaoService.arquivar(cartaoId, usuarioId))
				.isInstanceOf(RecursoNaoEncontradoException.class);

		verify(cartaoRepository, never()).save(any());
	}

	@Test
	void desarquivarMarcaArquivadoFalseMasNaoAlteraPerguntaOuResposta() {
		Cartao cartao = new Cartao(materia(), "pergunta", "resposta", Cartao.ORIGEM_MANUAL);
		cartao.setArquivado(true);
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.of(cartao));
		when(cartaoRepository.save(any(Cartao.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Cartao desarquivado = cartaoService.desarquivar(cartaoId, usuarioId);

		assertThat(desarquivado.isArquivado()).isFalse();
		assertThat(desarquivado.getPergunta()).isEqualTo("pergunta");
		assertThat(desarquivado.getResposta()).isEqualTo("resposta");
	}

	@Test
	void desarquivarLancaRecursoNaoEncontradoQuandoCartaoPertenceAOutroUsuario() {
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> cartaoService.desarquivar(cartaoId, usuarioId))
				.isInstanceOf(RecursoNaoEncontradoException.class);

		verify(cartaoRepository, never()).save(any());
	}

	@Test
	void removerDeletaOCartaoQuandoPertenceAoUsuario() {
		Cartao cartao = new Cartao(materia(), "pergunta", "resposta", Cartao.ORIGEM_MANUAL);
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.of(cartao));

		cartaoService.remover(cartaoId, usuarioId);

		verify(cartaoRepository).delete(cartao);
	}

	@Test
	void removerLancaRecursoNaoEncontradoENaoDeletaQuandoCartaoPertenceAOutroUsuario() {
		when(cartaoRepository.findByIdAndMateria_Usuario_Id(cartaoId, usuarioId))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> cartaoService.remover(cartaoId, usuarioId))
				.isInstanceOf(RecursoNaoEncontradoException.class);

		verify(cartaoRepository, never()).delete(any());
	}
}
