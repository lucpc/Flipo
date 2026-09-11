package com.flipo.backend.model;

import com.flipo.backend.repository.CartaoRepository;
import com.flipo.backend.repository.ChaveApiRepository;
import com.flipo.backend.repository.MateriaRepository;
import com.flipo.backend.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valida o mapeamento JPA das entidades contra as migrations Flyway reais (rodando no Postgres
 * local do docker-compose — ver README/Makefile), não contra um banco embutido: usa
 * {@link AutoConfigureTestDatabase.Replace#NONE} para manter o datasource configurado em
 * {@code application.properties}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JpaModelTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MateriaRepository materiaRepository;

	@Autowired
	private CartaoRepository cartaoRepository;

	@Autowired
	private ChaveApiRepository chaveApiRepository;

	private Usuario novoUsuario(String sufixo) {
		return new Usuario("Usuária " + sufixo, "usuaria-" + sufixo + "@flipo.test", "hash-" + sufixo);
	}

	@Test
	void persisteUsuarioComIdECriadoEmGeradosAutomaticamente() {
		Usuario usuario = novoUsuario(UUID.randomUUID().toString());

		Usuario salvo = entityManager.persistFlushFind(usuario);

		assertThat(salvo.getId()).isNotNull();
		assertThat(salvo.getCriadoEm()).isNotNull().isBeforeOrEqualTo(Instant.now());
	}

	@Test
	void rejeitaEmailDuplicado() {
		// Passa pelo repositório (não pelo TestEntityManager cru) para exercitar a tradução de
		// exceção do Spring Data (@Repository) — é o caminho real usado pela aplicação.
		String email = "duplicado-" + UUID.randomUUID() + "@flipo.test";
		usuarioRepository.saveAndFlush(new Usuario("Primeira", email, "hash-1"));

		assertThatThrownBy(() -> usuarioRepository.saveAndFlush(new Usuario("Segunda", email, "hash-2")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void materiaPertenceAoUsuarioEEAcessivelPeloRepository() {
		Usuario usuario = entityManager.persistAndFlush(novoUsuario(UUID.randomUUID().toString()));
		Materia materia = entityManager.persistFlushFind(new Materia(usuario, "Matemática"));

		assertThat(materia.getId()).isNotNull();
		assertThat(materia.getUsuario().getId()).isEqualTo(usuario.getId());
		assertThat(materia.getCriadoEm()).isNotNull();

		List<Materia> materiasDoUsuario = materiaRepository.findByUsuarioId(usuario.getId());
		assertThat(materiasDoUsuario).extracting(Materia::getId).containsExactly(materia.getId());
	}

	@Test
	void cartaoUsaOrigemManualPorPadraoDeTesteEArquivadoFalsoPorDefaultDaEntidade() {
		Usuario usuario = entityManager.persistAndFlush(novoUsuario(UUID.randomUUID().toString()));
		Materia materia = entityManager.persistAndFlush(new Materia(usuario, "História"));

		Cartao cartao = entityManager.persistFlushFind(
				new Cartao(materia, "Quando foi a independência?", "1822", Cartao.ORIGEM_MANUAL));

		assertThat(cartao.getId()).isNotNull();
		assertThat(cartao.isArquivado()).isFalse();
		assertThat(cartao.getUltimaRevisao()).isNull();
		assertThat(cartao.getCriadoEm()).isNotNull();

		List<Cartao> cartoesDaMateria = cartaoRepository.findByMateriaId(materia.getId());
		assertThat(cartoesDaMateria).extracting(Cartao::getId).containsExactly(cartao.getId());
	}

	@Test
	void rejeitaOrigemForaDoConjuntoPermitidoPelaCheckConstraint() {
		Usuario usuario = entityManager.persistAndFlush(novoUsuario(UUID.randomUUID().toString()));
		Materia materia = entityManager.persistAndFlush(new Materia(usuario, "Geografia"));

		Cartao cartaoComOrigemInvalida = new Cartao(materia, "pergunta", "resposta", "gerado-por-magica");

		assertThatThrownBy(() -> cartaoRepository.saveAndFlush(cartaoComOrigemInvalida))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void chaveApiEUnicaPorUsuarioEProvedorENuncaExpoeValorEmTextoPuroNoDominio() {
		Usuario usuario = entityManager.persistAndFlush(novoUsuario(UUID.randomUUID().toString()));
		ChaveApi chave = entityManager.persistFlushFind(
				new ChaveApi(usuario, "anthropic", "valor-ja-criptografado"));

		assertThat(chave.getId()).isNotNull();
		assertThat(chave.getChaveCriptografada()).isEqualTo("valor-ja-criptografado");
		// A entidade nunca decide criptografia — só guarda o que a camada de serviço já cifrou.
		// Aqui só validamos que o toString não vaza o valor (defesa contra log acidental).
		assertThat(chave.toString()).doesNotContain("valor-ja-criptografado");

		List<ChaveApi> chavesDoUsuario = chaveApiRepository.findByUsuarioId(usuario.getId());
		assertThat(chavesDoUsuario).extracting(ChaveApi::getId).containsExactly(chave.getId());

		assertThat(chaveApiRepository.findByUsuarioIdAndProvedor(usuario.getId(), "anthropic"))
				.isPresent();
	}

	@Test
	void rejeitaSegundaChaveDoMesmoProvedorParaOMesmoUsuario() {
		Usuario usuario = entityManager.persistAndFlush(novoUsuario(UUID.randomUUID().toString()));
		chaveApiRepository.saveAndFlush(new ChaveApi(usuario, "groq", "primeira-chave"));

		assertThatThrownBy(() ->
				chaveApiRepository.saveAndFlush(new ChaveApi(usuario, "groq", "segunda-chave")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@Sql(statements = {
			"insert into usuarios (id, nome, email, senha_hash) "
					+ "values ('11111111-1111-1111-1111-111111111111', 'Raw', 'raw-insert@flipo.test', 'hash')",
			"insert into materias (id, usuario_id, nome) "
					+ "values ('22222222-2222-2222-2222-222222222222', "
					+ "'11111111-1111-1111-1111-111111111111', 'Matéria raw')",
			"insert into cartoes (id, materia_id, pergunta, resposta, origem) "
					+ "values ('33333333-3333-3333-3333-333333333333', "
					+ "'22222222-2222-2222-2222-222222222222', 'p', 'r', 'manual')"
	})
	void colunaArquivadoUsaDefaultFalseNoBancoQuandoOmitidaEmInsertBruto() {
		Cartao cartao = cartaoRepository
				.findById(UUID.fromString("33333333-3333-3333-3333-333333333333"))
				.orElseThrow();

		assertThat(cartao.isArquivado()).isFalse();
	}

	@Test
	void apagarUsuarioRemoveMateriasECartoesEChavesEmCascataViaForeignKey() {
		Usuario usuario = entityManager.persistAndFlush(novoUsuario(UUID.randomUUID().toString()));
		Materia materia = entityManager.persistAndFlush(new Materia(usuario, "Biologia"));
		Cartao cartao = entityManager.persistAndFlush(
				new Cartao(materia, "pergunta", "resposta", Cartao.ORIGEM_IA));
		ChaveApi chave = entityManager.persistAndFlush(new ChaveApi(usuario, "gemini", "chave"));

		UUID usuarioId = usuario.getId();
		UUID materiaId = materia.getId();
		UUID cartaoId = cartao.getId();
		UUID chaveId = chave.getId();

		// Limpa o contexto de persistência antes de apagar: a cascata é responsabilidade do
		// FK `ON DELETE CASCADE` do banco (migrations V2/V3/V4), não de um cascade=REMOVE do
		// JPA — sem isso, o Hibernate tenta (incorretamente) validar as associações em memória
		// das entidades filhas ainda carregadas contra o usuário marcado para remoção.
		entityManager.clear();

		usuarioRepository.deleteById(usuarioId);
		entityManager.flush();
		entityManager.clear();

		assertThat(usuarioRepository.findById(usuarioId)).isEmpty();
		assertThat(materiaRepository.findById(materiaId)).isEmpty();
		assertThat(cartaoRepository.findById(cartaoId)).isEmpty();
		assertThat(chaveApiRepository.findById(chaveId)).isEmpty();
	}
}
