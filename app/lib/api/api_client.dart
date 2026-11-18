import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;

/// URL do backend, definida por ambiente: `--dart-define=API_URL=https://...`.
/// No emulador Android, o localhost da máquina é `http://10.0.2.2:8080`.
const apiUrlPadrao = String.fromEnvironment(
  'API_URL',
  defaultValue: 'http://localhost:8080',
);

class ApiException implements Exception {
  ApiException(this.status, this.mensagem);

  final int status;
  final String mensagem;

  @override
  String toString() => 'ApiException($status): $mensagem';
}

class Materia {
  Materia({
    required this.id,
    required this.nome,
    required this.totalAtivos,
    required this.totalArquivados,
  });

  factory Materia.fromJson(Map<String, dynamic> json) => Materia(
    id: json['id'] as String,
    nome: json['nome'] as String,
    totalAtivos: json['totalAtivos'] as int,
    totalArquivados: json['totalArquivados'] as int,
  );

  final String id;
  final String nome;
  final int totalAtivos;
  final int totalArquivados;
}

class Cartao {
  Cartao({
    required this.id,
    required this.pergunta,
    required this.resposta,
    required this.origem,
    required this.arquivado,
  });

  factory Cartao.fromJson(Map<String, dynamic> json) => Cartao(
    id: json['id'] as String,
    pergunta: json['pergunta'] as String,
    resposta: json['resposta'] as String,
    origem: json['origem'] as String,
    arquivado: json['arquivado'] as bool,
  );

  final String id;
  final String pergunta;
  final String resposta;

  /// `manual` ou `ia`.
  final String origem;
  final bool arquivado;
}

/// Por ora só o nome, para a saudação e o avatar de Minhas matérias.
class Usuario {
  Usuario({required this.nome});

  factory Usuario.fromJson(Map<String, dynamic> json) =>
      Usuario(nome: json['nome'] as String);

  final String nome;
}

/// Client da API REST (docs/03-contrato-api.md). Guarda o JWT no armazenamento seguro do sistema
/// e o envia como `Authorization: Bearer`. Um 401 numa requisição autenticada apaga o token e
/// chama [aoExpirarSessao], para o app voltar ao login.
class ApiClient {
  ApiClient({
    http.Client? httpClient,
    this._armazenamento = const FlutterSecureStorage(),
    String baseUrl = apiUrlPadrao,
    this.aoExpirarSessao,
  }) : _http = httpClient ?? http.Client(),
       // Sem a barra final: `$baseUrl$caminho` geraria `//api/...`, que o Spring recusa.
       baseUrl = baseUrl.replaceFirst(RegExp(r'/+$'), '');

  static const _chaveToken = 'flipo_token';

  final http.Client _http;
  final FlutterSecureStorage _armazenamento;
  final String baseUrl;
  final VoidCallback? aoExpirarSessao;

  Future<void> salvarToken(String token) =>
      _armazenamento.write(key: _chaveToken, value: token);

  Future<void> limparToken() => _armazenamento.delete(key: _chaveToken);

  Future<bool> temToken() async =>
      await _armazenamento.read(key: _chaveToken) != null;

  /// Entra com e-mail e senha e guarda o token recebido.
  Future<void> login(String email, String senha) async {
    final corpo =
        await _requisitar(
              'POST',
              '/api/auth/login',
              corpo: {'email': email, 'senha': senha},
            )
            as Map<String, dynamic>;
    await salvarToken(corpo['token'] as String);
  }

  /// Cria a conta. Não entra: o token vem só do [login].
  Future<void> registrar(String nome, String email, String senha) async {
    await _requisitar(
      'POST',
      '/api/auth/registro',
      corpo: {'nome': nome, 'email': email, 'senha': senha},
    );
  }

  /// O usuário dono do token salvo.
  Future<Usuario> buscarUsuarioAtual() async => Usuario.fromJson(
    await _requisitar('GET', '/api/usuarios/me') as Map<String, dynamic>,
  );

  Future<List<Materia>> listarMaterias() async {
    final corpo = await _requisitar('GET', '/api/materias') as List<dynamic>;
    return corpo
        .map((json) => Materia.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  Future<Materia> criarMateria(String nome) async => Materia.fromJson(
    await _requisitar('POST', '/api/materias', corpo: {'nome': nome})
        as Map<String, dynamic>,
  );

  /// Devolve a matéria renomeada, com os totais atuais.
  Future<Materia> renomearMateria(String id, String nome) async =>
      Materia.fromJson(
        await _requisitar(
              'PATCH',
              '/api/materias/${Uri.encodeComponent(id)}',
              corpo: {'nome': nome},
            )
            as Map<String, dynamic>,
      );

  /// Apaga a matéria e, com ela, todos os seus cartões.
  Future<void> apagarMateria(String id) async {
    await _requisitar('DELETE', '/api/materias/${Uri.encodeComponent(id)}');
  }

  /// Cria um cartão manual, ativo, na matéria.
  Future<Cartao> criarCartao(
    String materiaId,
    String pergunta,
    String resposta,
  ) async => Cartao.fromJson(
    await _requisitar(
          'POST',
          '/api/materias/$materiaId/cartoes',
          corpo: {'pergunta': pergunta, 'resposta': resposta},
        )
        as Map<String, dynamic>,
  );

  /// Os cartões ativos da matéria (sessão de estudo) ou, com [arquivados], os do Arquivo.
  Future<List<Cartao>> listarCartoes(
    String materiaId, {
    bool arquivados = false,
  }) async {
    final corpo =
        await _requisitar(
              'GET',
              '/api/materias/${Uri.encodeComponent(materiaId)}/cartoes'
                  '?arquivado=$arquivados',
            )
            as List<dynamic>;
    return corpo
        .map((json) => Cartao.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  /// Troca a pergunta e a resposta. Não mexe em `arquivado`.
  Future<Cartao> editarCartao(
    String id,
    String pergunta,
    String resposta,
  ) async => Cartao.fromJson(
    await _requisitar(
          'PATCH',
          '/api/cartoes/${Uri.encodeComponent(id)}',
          corpo: {'pergunta': pergunta, 'resposta': resposta},
        )
        as Map<String, dynamic>,
  );

  /// Tira o cartão do estudo e o põe no Arquivo. Só por ação deliberada do usuário, na edição do
  /// cartão: nunca automaticamente, nem durante o estudo.
  Future<Cartao> arquivarCartao(String id) async => Cartao.fromJson(
    await _requisitar(
          'PATCH',
          '/api/cartoes/${Uri.encodeComponent(id)}/arquivar',
        )
        as Map<String, dynamic>,
  );

  /// Devolve o cartão aos ativos.
  Future<Cartao> desarquivarCartao(String id) async => Cartao.fromJson(
    await _requisitar(
          'PATCH',
          '/api/cartoes/${Uri.encodeComponent(id)}/desarquivar',
        )
        as Map<String, dynamic>,
  );

  /// Apaga o cartão de vez.
  Future<void> excluirCartao(String id) async {
    await _requisitar('DELETE', '/api/cartoes/${Uri.encodeComponent(id)}');
  }

  Future<Object?> _requisitar(
    String metodo,
    String caminho, {
    Object? corpo,
  }) async {
    final requisicao = http.Request(metodo, Uri.parse('$baseUrl$caminho'));
    final token = await _armazenamento.read(key: _chaveToken);
    if (token != null) {
      requisicao.headers['Authorization'] = 'Bearer $token';
    }
    if (corpo != null) {
      requisicao.headers['Content-Type'] = 'application/json';
      requisicao.body = jsonEncode(corpo);
    }

    final resposta = await http.Response.fromStream(
      await _http.send(requisicao),
    );
    final texto = resposta.body;

    // Sem token enviado (ex.: login com senha errada) não havia sessão para expirar. Se o token
    // salvo mudou enquanto a requisição voltava (novo login, ou outro 401 já tratou), o 401 é de
    // uma sessão que não existe mais e não mexe na atual.
    if (resposta.statusCode == 401 &&
        token != null &&
        await _armazenamento.read(key: _chaveToken) == token) {
      await limparToken();
      aoExpirarSessao?.call();
    }
    if (resposta.statusCode < 200 || resposta.statusCode >= 300) {
      throw ApiException(
        resposta.statusCode,
        _mensagemDeErro(resposta.statusCode, texto),
      );
    }
    return texto.isEmpty ? null : jsonDecode(texto);
  }

  // Mesmo formato do GlobalExceptionHandler do backend: `{ "mensagem": "..." }`. O 401 do filtro
  // de JWT vem sem esse campo, daí o texto padrão.
  String _mensagemDeErro(int status, String texto) {
    try {
      final corpo = jsonDecode(texto);
      if (corpo is Map && corpo['mensagem'] is String) {
        return corpo['mensagem'] as String;
      }
    } on FormatException {
      // corpo vazio ou não-JSON
    }
    return status == 401
        ? 'Sessão expirada. Faça login novamente.'
        : 'Falha na requisição (status $status).';
  }
}
