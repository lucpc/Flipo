package com.flipo.backend.service;

/** Lançada por {@link AuthService#registrar} quando o e-mail já pertence a outro usuário. */
public class EmailJaCadastradoException extends RuntimeException {

	public EmailJaCadastradoException() {
		super("E-mail já cadastrado.");
	}
}
