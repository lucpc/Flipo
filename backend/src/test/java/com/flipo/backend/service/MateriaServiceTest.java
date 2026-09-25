package com.flipo.backend.service;

import com.flipo.backend.model.Materia;
import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.CartaoRepository;
import com.flipo.backend.repository.CartaoRepository.ContagemPorMateria;
import com.flipo.backend.repository.MateriaRepository;
import com.flipo.backend.repository.UsuarioRepository;
import com.flipo.backend.service.MateriaService.MateriaComContagem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MateriaServiceTest {

	@Mock
	private MateriaRepository materiaRepository;

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private CartaoRepository cartaoRepository;

	private MateriaService materiaService;

	private final UUID usuarioId = UUID.randomUUID();
	private final UUID materiaId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		materiaService = new MateriaService(materiaRepository, usuarioRepository, cartaoRepository);
	}

	// Materia.id só é atribuído pelo JPA ao persistir — em new Materia(...) puro ele fica null.
	// Os testes de contagem abaixo precisam de um id real pra casar com a projeção agregada
	// (que vem do banco com materiaId preenchido), daí atribuir um explicitamente aqui.
	private static Materia materiaComId(String nome) {
		Materia materia = new Materia(new Usuario("Nome", "dono@flipo.test", "hash"), nome);
		ReflectionTestUtils.setField(materia, "id", UUID.randomUUID());
		return materia;
	}

	private static ContagemPorMateria contagem(UUID materiaId, boolean arquivado, long total) {
		ContagemPorMateria contagem = mock(ContagemPorMateria.class);
		when(contagem.getMateriaId()).thenReturn(materiaId);
		when(contagem.isArquivado()).thenReturn(arquivado);
		when(contagem.getTotal()).thenReturn(total);
		return contagem;
	}

	@Test
	void buscarPorIdEUsuarioDevolveMateriaQuandoPertenceAoUsuario() {
		Materia materia = new Materia(new Usuario("Nome", "dono@flipo.test", "hash"), "Matemática");
		when(materiaRepository.findByIdAndUsuarioId(materiaId, usuarioId))
				.thenReturn(Optional.of(materia));

		Materia encontrada = materiaService.buscarPorIdEUsuario(materiaId, usuarioId);

		assertThat(encontrada).isSameAs(materia);
	}

	@Test
	void buscarPorIdEUsuarioLancaRecursoNaoEncontradoQuandoMateriaPertenceAOutroUsuario() {
		when(materiaRepository.findByIdAndUsuarioId(materiaId, usuarioId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> materiaService.buscarPorIdEUsuario(materiaId, usuarioId))
				.isInstanceOf(RecursoNaoEncontradoException.class);
	}

	@Test
	void buscarPorIdEUsuarioLancaRecursoNaoEncontradoQuandoIdNaoExisteComAMesmaExcecaoDoCasoDeOutroDono() {
		UUID idInexistente = UUID.randomUUID();
		when(materiaRepository.findByIdAndUsuarioId(idInexistente, usuarioId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> materiaService.buscarPorIdEUsuario(idInexistente, usuarioId))
				.isInstanceOf(RecursoNaoEncontradoException.class)
				.hasMessage("Recurso não encontrado.");
	}

	@Test
	void listarPorUsuarioDevolveContagemZeroQuandoMateriaNaoTemCartoes() {
		Materia materia = materiaComId("Química");
		when(materiaRepository.findByUsuarioId(usuarioId)).thenReturn(List.of(materia));
		when(cartaoRepository.contarPorMateriaEArquivadoDoUsuario(usuarioId)).thenReturn(List.of());

		List<MateriaComContagem> materias = materiaService.listarPorUsuario(usuarioId);

		assertThat(materias).containsExactly(new MateriaComContagem(materia, 0, 0));
	}

	@Test
	void listarPorUsuarioContaApenasCartoesAtivos() {
		Materia materia = materiaComId("Física");
		when(materiaRepository.findByUsuarioId(usuarioId)).thenReturn(List.of(materia));
		ContagemPorMateria contagemAtivos = contagem(materia.getId(), false, 3);
		when(cartaoRepository.contarPorMateriaEArquivadoDoUsuario(usuarioId))
				.thenReturn(List.of(contagemAtivos));

		List<MateriaComContagem> materias = materiaService.listarPorUsuario(usuarioId);

		assertThat(materias).containsExactly(new MateriaComContagem(materia, 3, 0));
	}

	@Test
	void listarPorUsuarioContaApenasCartoesArquivados() {
		Materia materia = materiaComId("Geografia");
		when(materiaRepository.findByUsuarioId(usuarioId)).thenReturn(List.of(materia));
		ContagemPorMateria contagemArquivados = contagem(materia.getId(), true, 5);
		when(cartaoRepository.contarPorMateriaEArquivadoDoUsuario(usuarioId))
				.thenReturn(List.of(contagemArquivados));

		List<MateriaComContagem> materias = materiaService.listarPorUsuario(usuarioId);

		assertThat(materias).containsExactly(new MateriaComContagem(materia, 0, 5));
	}

	@Test
	void listarPorUsuarioCombinaAtivosEArquivadosPorMateriaSemNPlusUm() {
		Materia quimica = materiaComId("Química");
		Materia historia = materiaComId("História");
		when(materiaRepository.findByUsuarioId(usuarioId)).thenReturn(List.of(quimica, historia));
		// Uma única chamada agregada cobre as duas matérias — não há stub por matéria.
		List<ContagemPorMateria> contagens = List.of(
				contagem(quimica.getId(), false, 2),
				contagem(quimica.getId(), true, 1),
				contagem(historia.getId(), false, 4));
		when(cartaoRepository.contarPorMateriaEArquivadoDoUsuario(usuarioId)).thenReturn(contagens);

		List<MateriaComContagem> materias = materiaService.listarPorUsuario(usuarioId);

		assertThat(materias)
				.extracting(MateriaComContagem::materia, MateriaComContagem::totalAtivos,
						MateriaComContagem::totalArquivados)
				.containsExactly(
						tuple(quimica, 2L, 1L),
						tuple(historia, 4L, 0L));
	}

	@Test
	void listarPorUsuarioNaoSomaContagemDeMateriaDeOutroUsuario() {
		Materia materiaDoUsuario = materiaComId("Biologia");
		UUID materiaDeOutroUsuarioId = UUID.randomUUID();
		when(materiaRepository.findByUsuarioId(usuarioId)).thenReturn(List.of(materiaDoUsuario));
		// A query já é escopada por usuarioId no repositório; mesmo que uma contagem de outra
		// matéria "vazasse" pro resultado, ela não deve ser atribuída à matéria do usuário atual.
		ContagemPorMateria contagemDeOutraMateria = contagem(materiaDeOutroUsuarioId, false, 99);
		when(cartaoRepository.contarPorMateriaEArquivadoDoUsuario(usuarioId))
				.thenReturn(List.of(contagemDeOutraMateria));

		List<MateriaComContagem> materias = materiaService.listarPorUsuario(usuarioId);

		assertThat(materias).containsExactly(new MateriaComContagem(materiaDoUsuario, 0, 0));
	}

	@Test
	void criarAssociaAMateriaAoUsuarioDoJwtESalva() {
		Usuario usuario = new Usuario("Nome", "dono@flipo.test", "hash");
		when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);
		when(materiaRepository.save(any(Materia.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Materia criada = materiaService.criar(usuarioId, "Biologia");

		assertThat(criada.getNome()).isEqualTo("Biologia");
		assertThat(criada.getUsuario()).isSameAs(usuario);
	}

	@Test
	void removerDeletaAMateriaQuandoPertenceAoUsuario() {
		Materia materia = new Materia(new Usuario("Nome", "dono@flipo.test", "hash"), "Física");
		when(materiaRepository.findByIdAndUsuarioId(materiaId, usuarioId)).thenReturn(Optional.of(materia));

		materiaService.remover(materiaId, usuarioId);

		verify(materiaRepository).delete(materia);
	}

	@Test
	void removerLancaRecursoNaoEncontradoENaoDeletaQuandoMateriaPertenceAOutroUsuario() {
		when(materiaRepository.findByIdAndUsuarioId(materiaId, usuarioId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> materiaService.remover(materiaId, usuarioId))
				.isInstanceOf(RecursoNaoEncontradoException.class);

		verify(materiaRepository, never()).delete(any());
	}
}
