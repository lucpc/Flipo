package com.flipo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Chave de API de terceiro (BYOK) configurada por um usuário para um provedor de IA (ex:
 * {@code anthropic}, {@code groq}) — no máximo uma por provedor (constraint
 * {@code uk_chaves_api_usuario_provedor}, migration {@code V4}).
 *
 * <p>{@code chaveCriptografada} nunca guarda texto puro — a criptografia/descriptografia é
 * responsabilidade da camada de serviço (ver docs/04-arquitetura-tecnica.md), não desta entidade.
 * Nenhum endpoint deve devolver esse valor.
 */
@Entity
@Table(name = "chaves_api")
public class ChaveApi {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false, length = 50)
	private String provedor;

	@Column(name = "chave_criptografada", nullable = false, columnDefinition = "text")
	private String chaveCriptografada;

	protected ChaveApi() {
		// exigido pelo JPA
	}

	public ChaveApi(Usuario usuario, String provedor, String chaveCriptografada) {
		this.usuario = usuario;
		this.provedor = provedor;
		this.chaveCriptografada = chaveCriptografada;
	}

	public UUID getId() {
		return id;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public String getProvedor() {
		return provedor;
	}

	public String getChaveCriptografada() {
		return chaveCriptografada;
	}

	public void setChaveCriptografada(String chaveCriptografada) {
		this.chaveCriptografada = chaveCriptografada;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ChaveApi chaveApi)) {
			return false;
		}
		return id != null && id.equals(chaveApi.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		// chaveCriptografada propositalmente omitida do toString.
		return "ChaveApi{id=%s, provedor='%s'}".formatted(id, provedor);
	}
}
