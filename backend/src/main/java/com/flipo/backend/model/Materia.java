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
 * Uma matéria de um usuário, agrupando {@link Cartao}es. Sem limite de quantidade por usuário
 * (docs/02-modelo-de-dados.md).
 */
@Entity
@Table(name = "materias")
public class Materia {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false)
	private String nome;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	protected Materia() {
		// exigido pelo JPA
	}

	public Materia(Usuario usuario, String nome) {
		this.usuario = usuario;
		this.nome = nome;
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

	public Usuario getUsuario() {
		return usuario;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Materia materia)) {
			return false;
		}
		return id != null && id.equals(materia.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "Materia{id=%s, nome='%s'}".formatted(id, nome);
	}
}
