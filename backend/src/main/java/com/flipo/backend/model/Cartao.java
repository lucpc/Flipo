package com.flipo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um flashcard de uma {@link Materia}.
 *
 * <p>{@code origem} vale {@code "manual"} ou {@code "ia"} — validado por uma {@code CHECK}
 * constraint no banco (migration {@code V3}), não por um enum nativo do Postgres, para evoluir
 * sem {@code ALTER TYPE} (docs/02-modelo-de-dados.md).
 *
 * <p>{@code arquivado} é a única flag de progresso do cartão. Não existe repetição espaçada
 * (sem {@code intervalo}/{@code fatorFacilidade}/{@code proximaRevisao}) — decisão de produto
 * fechada, não reabrir sem alinhar antes. {@code arquivado} só é alterado por edição deliberada
 * do cartão (endpoints {@code PATCH /cartoes/{id}/arquivar|desarquivar}), nunca como efeito
 * colateral de geração por IA ou de sessão de estudo.
 */
@Entity
@Table(name = "cartoes")
public class Cartao {

	public static final String ORIGEM_MANUAL = "manual";
	public static final String ORIGEM_IA = "ia";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "materia_id", nullable = false)
	private Materia materia;

	@Column(nullable = false, columnDefinition = "text")
	private String pergunta;

	@Column(nullable = false, columnDefinition = "text")
	private String resposta;

	@Column(nullable = false, length = 20)
	private String origem;

	@Column(nullable = false)
	private boolean arquivado = false;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	// Só um registro informativo de "visto pela última vez em" — nunca usado para calcular prazos.
	@Column(name = "ultima_revisao")
	private Instant ultimaRevisao;

	protected Cartao() {
		// exigido pelo JPA
	}

	public Cartao(Materia materia, String pergunta, String resposta, String origem) {
		this.materia = materia;
		this.pergunta = pergunta;
		this.resposta = resposta;
		this.origem = origem;
	}

	@PrePersist
	void prePersist() {
		if (criadoEm == null) {
			criadoEm = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public Materia getMateria() {
		return materia;
	}

	public String getPergunta() {
		return pergunta;
	}

	public void setPergunta(String pergunta) {
		this.pergunta = pergunta;
	}

	public String getResposta() {
		return resposta;
	}

	public void setResposta(String resposta) {
		this.resposta = resposta;
	}

	public String getOrigem() {
		return origem;
	}

	public boolean isArquivado() {
		return arquivado;
	}

	public void setArquivado(boolean arquivado) {
		this.arquivado = arquivado;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getUltimaRevisao() {
		return ultimaRevisao;
	}

	public void setUltimaRevisao(Instant ultimaRevisao) {
		this.ultimaRevisao = ultimaRevisao;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Cartao cartao)) {
			return false;
		}
		return id != null && id.equals(cartao.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "Cartao{id=%s, origem='%s', arquivado=%s}".formatted(id, origem, arquivado);
	}
}
