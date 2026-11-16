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

/// Client da API REST (docs/03-contrato-api.md). Guarda o JWT no armazenamento seguro do sistema
/// e o envia como `Authorization: Bearer`. Um 401 numa requisição autenticada apaga o token e
/// chama [aoExpirarSessao], para o app voltar ao login.
class ApiClient {
  ApiClient({
    http.Client? httpClient,
    this._armazenamento = const FlutterSecureStorage(),
    this.baseUrl = apiUrlPadrao,
    this.aoExpirarSessao,
  }) : _http = httpClient ?? http.Client();

  static const _chaveToken = 'flipo_token';

  final http.Client _http;
  final FlutterSecureStorage _armazenamento;
  final String baseUrl;
  final VoidCallback? aoExpirarSessao;

  Future<void> salvarToken(String token) =>
      _armazenamento.write(key: _chaveToken, value: token);

  Future<void> limparToken() => _armazenamento.delete(key: _chaveToken);

  Future<List<Materia>> listarMaterias() async {
    final corpo = await _requisitar('GET', '/api/materias') as List<dynamic>;
    return corpo
        .map((json) => Materia.fromJson(json as Map<String, dynamic>))
        .toList();
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

    if (resposta.statusCode == 401) {
      await limparToken();
      // Sem token enviado (ex.: login com senha errada) não havia sessão para expirar.
      if (token != null) aoExpirarSessao?.call();
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
