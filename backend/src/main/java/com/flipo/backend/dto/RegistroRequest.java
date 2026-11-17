package com.flipo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/auth/registro} (docs/03-contrato-api.md). */
public record RegistroRequest(

		@NotBlank
		@Size(max = 255)
		String nome,

		@NotBlank
		@Email
		@Size(max = 255)
		String email,

		// O máximo é em bytes, não em caracteres: ver SenhaAte72Bytes. Mensagem própria no @Size
		// porque, sem max, a padrão diria "entre 8 e 2147483647".
		@NotBlank
		@Size(min = 8, message = "A senha precisa ter pelo menos 8 caracteres.")
		@SenhaAte72Bytes
		String senha) {
}
