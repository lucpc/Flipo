package com.flipo.backend.service;

import com.flipo.backend.model.Materia;
import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.MateriaRepository;
import com.flipo.backend.repository.UsuarioRepository;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MateriaServiceTest {

	@Mock
	private MateriaRepository materiaRepository;

	@Mock
	private UsuarioRepository usuarioRepository;

	private MateriaService materiaService;

	private final UUID usuarioId = UUID.randomUUID();
	private final UUID materiaId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		materiaService = new MateriaService(materiaRepository, usuarioRepository);
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
	void listarPorUsuarioDevolveAsMateriasDoRepositorioParaOUsuario() {
		Materia materia = new Materia(new Usuario("Nome", "dono@flipo.test", "hash"), "Química");
		when(materiaRepository.findByUsuarioId(usuarioId)).thenReturn(List.of(materia));

		List<Materia> materias = materiaService.listarPorUsuario(usuarioId);

		assertThat(materias).containsExactly(materia);
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
