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

  test(
    'lista os cartões ativos por padrão, e os arquivados quando pedido',
    () async {
      final api = clientRespondendo(
        200,
        '[{"id":"c1","pergunta":"O que é TCP?","resposta":"Um protocolo",'
        '"origem":"ia","arquivado":false}]',
      );

      final cartoes = await api.listarCartoes('m1');
      await api.listarCartoes('m1', arquivados: true);

      expect(
        requisicoes[0].url.toString(),
        'http://api.teste/api/materias/m1/cartoes?arquivado=false',
      );
      expect(
        requisicoes[1].url.toString(),
        'http://api.teste/api/materias/m1/cartoes?arquivado=true',
      );
      final cartao = cartoes.single;
      expect(cartao.id, 'c1');
      expect(cartao.pergunta, 'O que é TCP?');
      expect(cartao.resposta, 'Um protocolo');
      expect(cartao.origem, 'ia');
      expect(cartao.arquivado, isFalse);
    },
  );

  test('edita, arquiva, desarquiva e exclui o cartão pelo id, codificado no '
      'caminho', () async {
    final api = clientRespondendo(
      200,
      '{"id":"c/1","pergunta":"O que é NAT?","resposta":"Tradução",'
      '"origem":"manual","arquivado":true}',
    );

    final editado = await api.editarCartao('c/1', 'O que é NAT?', 'Tradução');
    final arquivado = await api.arquivarCartao('c/1');
    await api.desarquivarCartao('c/1');
    await api.excluirCartao('c/1');

    expect(
      [for (final r in requisicoes) '${r.method} ${r.url}'],
      [
        'PATCH http://api.teste/api/cartoes/c%2F1',
        'PATCH http://api.teste/api/cartoes/c%2F1/arquivar',
        'PATCH http://api.teste/api/cartoes/c%2F1/desarquivar',
        'DELETE http://api.teste/api/cartoes/c%2F1',
      ],
    );
    expect(jsonDecode(requisicoes.first.body), {
      'pergunta': 'O que é NAT?',
      'resposta': 'Tradução',
    });
    expect(requisicoes.skip(1).map((r) => r.body), everyElement(isEmpty));
    expect(editado.pergunta, 'O que é NAT?');
    expect(arquivado.arquivado, isTrue);
  });

  test('gerar-ia envia texto, quantidade inteira e nível canônico, e lê as '
      'sugestões', () async {
    final api = clientRespondendo(
      200,
      '[{"pergunta":"O que é TCP?","resposta":"Um protocolo"},'
      '{"pergunta":"O que é UDP?","resposta":"Outro protocolo"}]',
    );

    final sugestoes = await api.gerarIa(
      'm/1',
      'Texto sobre transporte.',
      12,
      NivelDeCartao.aplicacao,
    );
    await api.gerarIa('m/1', 'Outro texto.', 10, NivelDeCartao.definicao);

    expect(requisicoes[0].method, 'POST');
    expect(
      requisicoes[0].url.toString(),
      'http://api.teste/api/materias/m%2F1/cartoes/gerar-ia',
    );
    expect(
      requisicoes[0].headers['Content-Type'],
      startsWith('application/json'),
    );
    expect(jsonDecode(requisicoes[0].body), {
      'texto': 'Texto sobre transporte.',
      'quantidade': 12,
      'nivel': 'aplicacao',
    });
    expect(jsonDecode(requisicoes[1].body), {
      'texto': 'Outro texto.',
      'quantidade': 10,
      'nivel': 'definicao',
    });
    expect(sugestoes, [
      (pergunta: 'O que é TCP?', resposta: 'Um protocolo'),
      (pergunta: 'O que é UDP?', resposta: 'Outro protocolo'),
    ]);
  });

  test('gerar-ia com 502 lança o erro com o status', () async {
    final api = clientRespondendo(502, '{"mensagem":"Falha ao gerar."}');

    await expectLater(
      api.gerarIa('m1', 'Texto.', 10, NivelDeCartao.definicao),
      throwsA(isA<ApiException>().having((e) => e.status, 'status', 502)),
    );
  });

  test('lote envia só pergunta e resposta de cada cartão, na ordem, e lê os '
      'gravados', () async {
    final api = clientRespondendo(
      201,
      '[{"id":"c1","pergunta":"O que é TCP?","resposta":"Um protocolo",'
      '"origem":"ia","arquivado":false},'
      '{"id":"c2","pergunta":"O que é UDP?","resposta":"Outro",'
      '"origem":"ia","arquivado":false}]',
    );

    final cartoes = await api.criarCartoesEmLote('m/1', [
      (pergunta: 'O que é TCP?', resposta: 'Um protocolo'),
      (pergunta: 'O que é UDP?', resposta: 'Outro'),
    ]);

    expect(requisicoes.single.method, 'POST');
    expect(
      requisicoes.single.url.toString(),
      'http://api.teste/api/materias/m%2F1/cartoes/lote',
    );
    expect(jsonDecode(requisicoes.single.body), {
      'cartoes': [
        {'pergunta': 'O que é TCP?', 'resposta': 'Um protocolo'},
        {'pergunta': 'O que é UDP?', 'resposta': 'Outro'},
      ],
    });
    expect([for (final c in cartoes) c.id], ['c1', 'c2']);
    expect(cartoes.every((c) => c.origem == 'ia' && !c.arquivado), isTrue);
  });

  test('login envia e-mail e senha e guarda o token recebido', () async {
    final api = clientRespondendo(200, '{"token":"jwt-novo"}');

    await api.login('ana@exemplo.com', 'segredo123');

    expect(requisicoes.single.method, 'POST');
    expect(requisicoes.single.url.path, '/api/auth/login');
    expect(jsonDecode(requisicoes.single.body), {
      'email': 'ana@exemplo.com',
      'senha': 'segredo123',
    });
    expect(await armazenamento.read(key: 'flipo_token'), 'jwt-novo');
    expect(await api.temToken(), isTrue);
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
