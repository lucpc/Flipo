package com.flipo.backend.controller;

import com.flipo.backend.model.Usuario;
import com.flipo.backend.repository.UsuarioRepository;
import com.flipo.backend.security.JwtService;
import com.flipo.backend.service.ExtracaoDeTextoPdfService;
import com.flipo.backend.service.ExtracoesDeTeste;
import com.flipo.backend.service.PdfsDeTeste;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O 503 do {@code POST /api/pdf/extrair-texto} quando não abre vaga de extração: o serviço de verdade,
 * mas com zero vagas e 50 ms de espera ({@link ExtracoesDeTeste#semVagas()}). {@code @Transactional}
 * desfaz o usuário criado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PdfOcupadoTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JwtService jwtService;

	@TestBean
	private ExtracaoDeTextoPdfService extracaoDeTextoPdfService;

	static ExtracaoDeTextoPdfService extracaoDeTextoPdfService() {
		return ExtracoesDeTeste.semVagas();
	}

	@Test
	void semVagaDeExtracaoDevolve503ComMensagem() throws Exception {
		Usuario usuario = usuarioRepository.save(
				new Usuario("Usuária", "pdf-ocupado-" + UUID.randomUUID() + "@flipo.test", "hash-qualquer"));
		byte[] pdf = PdfsDeTeste.comPaginas(List.of(List.of("Texto")));

		mockMvc.perform(multipart("/api/pdf/extrair-texto")
				.file(new MockMultipartFile("arquivo", "apostila.pdf", "application/pdf", pdf))
				.header("Authorization", "Bearer " + jwtService.gerarToken(usuario)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.mensagem")
						.value("O servidor está ocupado lendo outros PDFs. Tente de novo em instantes."));
	}
}
