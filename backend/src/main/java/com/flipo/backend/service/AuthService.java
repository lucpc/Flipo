package com.flipo.backend.service;

import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.UsuarioRepository;
import com.flipo.backend.security.JwtService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registro e login (docs/03-contrato-api.md, seção Autenticação). Senha nunca é armazenada em
 * texto puro (hash BCrypt via {@link PasswordEncoder}) e credenciais inválidas nunca revelam se
 * foi o e-mail ou a senha que não conferiu (docs/04-arquitetura-tecnica.md, seção Segurança).
 */
@Service
public class AuthService {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	// Hash BCrypt "fantasma", comparado quando o e-mail não existe, para que o custo de
	// passwordEncoder.matches seja pago nos dois casos — sem isso, a ausência dessa chamada
	// quando o e-mail é desconhecido cria uma diferença de tempo de resposta mensurável entre
	// "e-mail não cadastrado" e "e-mail cadastrado, senha errada" (user enumeration via timing).
	private final String hashFantasma;

	public AuthService(
			UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.hashFantasma = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public Usuario registrar(String nome, String email, String senha) {
		if (usuarioRepository.existsByEmail(email)) {
			throw new EmailJaCadastradoException();
		}

		Usuario usuario = new Usuario(nome, email, passwordEncoder.encode(senha));
		try {
			return usuarioRepository.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// Corrida entre o existsByEmail acima e o insert: a constraint única do banco
			// (migration V1) é a garantia real; o check acima é só o caminho feliz mais barato.
			throw new EmailJaCadastradoException();
		}
	}

	public String login(String email, String senha) {
		Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
		String hashParaComparar = usuario != null ? usuario.getSenhaHash() : hashFantasma;
		boolean senhaConfere = passwordEncoder.matches(senha, hashParaComparar);

		if (usuario == null || !senhaConfere) {
			throw new CredenciaisInvalidasException();
		}

		return jwtService.gerarToken(usuario);
	}
}
