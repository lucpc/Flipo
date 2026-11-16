import 'dart:convert';

import 'package:flipo/api/api_client.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  const armazenamento = FlutterSecureStorage();
  late List<http.Request> requisicoes;
  late int sessoesExpiradas;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    requisicoes = [];
    sessoesExpiradas = 0;
  });

  ApiClient clientRespondendo(int status, String corpo) => ApiClient(
    baseUrl: 'http://api.teste',
    aoExpirarSessao: () => sessoesExpiradas++,
    httpClient: MockClient((requisicao) async {
      requisicoes.add(requisicao);
      return http.Response.bytes(
        utf8.encode(corpo),
        status,
        headers: {'content-type': 'application/json'},
      );
    }),
  );

  test('envia o token salvo como Bearer e lê as matérias', () async {
    final api = clientRespondendo(
      200,
      '[{"id":"m1","nome":"Redes de Computação","totalAtivos":6,"totalArquivados":2}]',
    );
    await api.salvarToken('jwt-123');

    final materias = await api.listarMaterias();

    expect(requisicoes.single.url.toString(), 'http://api.teste/api/materias');
    expect(requisicoes.single.headers['Authorization'], 'Bearer jwt-123');
    expect(materias.single.nome, 'Redes de Computação');
    expect(materias.single.totalAtivos, 6);
    expect(materias.single.totalArquivados, 2);
  });

  test('barra no fim da baseUrl não duplica a barra do caminho', () async {
    final api = ApiClient(
      baseUrl: 'http://api.teste/',
      httpClient: MockClient((requisicao) async {
        requisicoes.add(requisicao);
        return http.Response('[]', 200);
      }),
    );

    await api.listarMaterias();

    expect(requisicoes.single.url.toString(), 'http://api.teste/api/materias');
  });

  test('401 atrasado não apaga um token salvo depois do envio', () async {
    late ApiClient api;
    api = ApiClient(
      baseUrl: 'http://api.teste',
      aoExpirarSessao: () => sessoesExpiradas++,
      httpClient: MockClient((requisicao) async {
        await api.salvarToken('jwt-novo');
        return http.Response('', 401);
      }),
    );
    await api.salvarToken('jwt-vencido');

    await expectLater(api.listarMaterias(), throwsA(isA<ApiException>()));
    expect(await armazenamento.read(key: 'flipo_token'), 'jwt-novo');
    expect(sessoesExpiradas, 0);
  });

  test('sem token salvo, não envia Authorization', () async {
    final api = clientRespondendo(200, '[]');

    await api.listarMaterias();

    expect(requisicoes.single.headers.containsKey('Authorization'), isFalse);
  });

  test('401 apaga o token e avisa que a sessão expirou', () async {
    final api = clientRespondendo(401, '');
    await api.salvarToken('jwt-vencido');

    await expectLater(
      api.listarMaterias(),
      throwsA(
        isA<ApiException>()
            .having((e) => e.status, 'status', 401)
            .having(
              (e) => e.mensagem,
              'mensagem',
              'Sessão expirada. Faça login novamente.',
            ),
      ),
    );
    expect(await armazenamento.read(key: 'flipo_token'), isNull);
    expect(sessoesExpiradas, 1);
  });

  test('401 sem token enviado não avisa sessão expirada', () async {
    final api = clientRespondendo(401, '{"mensagem":"Credenciais inválidas"}');

    await expectLater(
      api.listarMaterias(),
      throwsA(
        isA<ApiException>().having(
          (e) => e.mensagem,
          'mensagem',
          'Credenciais inválidas',
        ),
      ),
    );
    expect(sessoesExpiradas, 0);
  });

  test('outros erros usam a mensagem do backend e mantêm a sessão', () async {
    final api = clientRespondendo(404, '{"mensagem":"Matéria não encontrada"}');
    await api.salvarToken('jwt-123');

    await expectLater(
      api.listarMaterias(),
      throwsA(
        isA<ApiException>()
            .having((e) => e.status, 'status', 404)
            .having((e) => e.mensagem, 'mensagem', 'Matéria não encontrada'),
      ),
    );
    expect(await armazenamento.read(key: 'flipo_token'), 'jwt-123');
    expect(sessoesExpiradas, 0);
  });
}
