package com.flipo.backend.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * A senha cabe no BCrypt: no máximo 72 bytes em UTF-8. O {@code BCryptPasswordEncoder} lança
 * {@link IllegalArgumentException} acima disso, e {@code @Size} conta caracteres, não bytes (cada
 * letra acentuada ocupa 2). {@code null} é válido aqui; quem barra é o {@code @NotBlank}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SenhaAte72Bytes.Validador.class)
public @interface SenhaAte72Bytes {

	String message() default "A senha pode ter no máximo 72 bytes (letras acentuadas contam 2).";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validador implements ConstraintValidator<SenhaAte72Bytes, String> {

		private static final int MAXIMO_BYTES = 72;

		@Override
		public boolean isValid(String senha, ConstraintValidatorContext context) {
			return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES;
		}
	}
}
