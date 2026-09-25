package com.flipo.backend.service;

import com.flipo.backend.model.Materia;
import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.MateriaRepository;

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
class MateriaServiceTest {

	@Mock
	private MateriaRepository materiaRepository;

	private MateriaService materiaService;

	private final UUID usuarioId = UUID.randomUUID();
	private final UUID materiaId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		materiaService = new MateriaService(materiaRepository);
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
}
