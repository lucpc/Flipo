package com.flipo.backend.config;

import com.flipo.backend.security.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * JWT em todos os endpoints exceto {@code /api/auth/*} (docs/04-arquitetura-tecnica.md, seção
 * Segurança). API stateless: sem sessão HTTP, sem CSRF (não há cookie de sessão para proteger).
 * CORS liberado só para as origens de {@code flipo.cors.allowed-origin-patterns}, para o build web
 * do app chamar a API de outra origem.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// Usa o bean corsConfigurationSource abaixo. O CorsFilter roda antes da autenticação,
				// então o preflight OPTIONS é respondido sem exigir JWT.
				.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
						// Requisição sem token válido: 401 genérico, nunca vaza detalhe do motivo.
						.authenticationEntryPoint((request, response, authException) ->
								response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	/**
	 * Origens aceitas vêm de configuração (padrões, para aceitar {@code http://localhost:[*]} em dev,
	 * onde o {@code flutter run -d chrome} usa porta aleatória). Sem {@code allowCredentials}: o
	 * JWT vai no cabeçalho {@code Authorization}, não em cookie.
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${flipo.cors.allowed-origin-patterns}") List<String> origensPermitidas) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOriginPatterns(origensPermitidas);
		config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
