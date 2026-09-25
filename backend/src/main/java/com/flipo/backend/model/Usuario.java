package com.flipo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um usuário do Flipo. Toda {@link Materia} e {@link ChaveApi} pertence a exatamente um usuário
 * (docs/02-modelo-de-dados.md).
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false, unique = true)
	private String email;

	// Hash (ex: BCrypt) — nunca a senha em texto puro.
	@Column(name = "senha_hash", nullable = false)
	private String senhaHash;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	protected Usuario() {
		// exigido pelo JPA
	}

	public Usuario(String nome, String email, String senhaHash) {
		this.nome = nome;
		this.email = email;
		this.senhaHash = senhaHash;
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

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public void setSenhaHash(String senhaHash) {
		this.senhaHash = senhaHash;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Usuario usuario)) {
			return false;
		}
		return id != null && id.equals(usuario.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		// senhaHash propositalmente omitido do toString.
		return "Usuario{id=%s, nome='%s', email='%s'}".formatted(id, nome, email);
	}
}
