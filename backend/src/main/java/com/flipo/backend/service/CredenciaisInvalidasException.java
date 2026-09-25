package com.flipo.backend.service;

/**
 * Lançada por {@link AuthService#login} quando o e-mail não existe ou a senha não confere.
 * Mensagem propositalmente genérica — nunca revela qual dos dois foi o motivo.
 */
public class CredenciaisInvalidasException extends RuntimeException {

	public CredenciaisInvalidasException() {
		super("E-mail ou senha inválidos.");
	}
}
