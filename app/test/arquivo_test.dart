import 'dart:async';
import 'dart:convert';

import 'package:flipo/main.dart';
import 'package:flipo/telas/arquivo.dart';
import 'package:flipo/telas/autenticacao.dart';
import 'package:flipo/telas/editar_cartao.dart';
import 'package:flipo/telas/materias.dart';
import 'package:flipo/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  late List<http.Request> requisicoes;
  // O backend falso: os cartões da matéria m1, com o estado de arquivado de cada um.
  late List<Map<String, Object>> cartoes;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'flipo_token': 'jwt-ana'});
    requisicoes = [];
    cartoes = [];
  });

  http.Response json(int status, Object corpo) => http.Response.bytes(
    utf8.encode(jsonEncode(corpo)),
    status,
    headers: {'content-type': 'application/json'},
  );

  Map<String, Object> cartao(int n, {bool arquivado = true}) => {
    'id': 'c$n',
    'pergunta': 'Pergunta $n',
    'resposta': 'Resposta $n',
    'origem': 'manual',
    'arquivado': arquivado,
  };

  Map<String, Object>? comId(String id) =>
      cartoes.where((c) => c['id'] == id).firstOrNull;

  int total(bool arquivado) =>
      cartoes.where((c) => c['arquivado'] == arquivado).length;

  /// Entra no app já logado, abre a matéria Redes e toca em "Arquivo". O backend guarda o estado
  /// de [cartoes]; [respostas] troca a resposta de uma rota ("MÉTODO caminho"). Com [esperar]
  /// falso, não espera a tela assentar (um carregamento pendente gira para sempre).
  Future<void> abrirArquivo(
    WidgetTester tester, {
    Map<String, FutureOr<http.Response> Function(http.Request)> respostas =
        const {},
    bool esperar = true,
  }) async {
    http.Response padrao(http.Request r) {
      final caminho = r.url.path;
      final id = caminho.split('/').elementAtOrNull(3);
      return switch ('${r.method} $caminho') {
        'GET /api/usuarios/me' => json(200, {
          'id': 'u1',
          'nome': 'Ana',
          'email': 'ana@exemplo.com',
        }),
        'GET /api/materias' => json(200, [
          {
            'id': 'm1',
            'nome': 'Redes',
            'totalAtivos': total(false),
            'totalArquivados': total(true),
          },
        ]),
        'GET /api/materias/m1/cartoes' => json(200, [
          for (final c in cartoes)
            if ('${c['arquivado']}' == r.url.queryParameters['arquivado']) c,
        ]),
        final rota when rota.endsWith('/desarquivar') => json(
          200,
          comId(id!)!..['arquivado'] = false,
        ),
        final rota when rota.startsWith('PATCH /api/cartoes/') => json(
          200,
          comId(id!)!..addAll(
            (jsonDecode(r.body) as Map<String, dynamic>).cast<String, Object>(),
          ),
        ),
        final rota when rota.startsWith('DELETE /api/cartoes/') => () {
          cartoes.remove(comId(id!));
          return http.Response('', 204);
        }(),
        final outra => throw StateError('inesperada: $outra'),
      };
    }

    await tester.pumpWidget(
      FlipoApp(
        httpClient: MockClient((r) async {
          requisicoes.add(r);
          return (respostas['${r.method} ${r.url.path}'] ?? padrao)(r);
        }),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Redes'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Arquivo'));
    if (esperar) {
      await tester.pumpAndSettle();
    } else {
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
    }
  }

  Future<void> tocar(WidgetTester tester, Finder alvo) async {
    await tester.tap(alvo);
    await tester.pumpAndSettle();
  }

  /// As requisições que mexem nos cartões, como "PATCH /api/cartoes/c1/desarquivar".
  List<String> mudancas() => [
    for (final r in requisicoes)
      if (r.method != 'GET') '${r.method} ${r.url.path}',
  ];

  int listagens() => requisicoes
      .where((r) => r.method == 'GET' && r.url.path == '/api/materias')
      .length;

  final proximo = find.text('Próximo cartão');
  final desarquivar = find.text('Desarquivar este cartão');
  final editar = find.byTooltip('Editar cartão');
  final avaliacao = find.textContaining(
    RegExp('acertei|errei|como você se saiu', caseSensitive: false),
  );

  // O único botão contornado do cartão; enquanto envia, o rótulo dá lugar ao progresso.
  OutlinedButton botaoDesarquivar(WidgetTester tester) =>
      tester.widget<OutlinedButton>(find.byType(OutlinedButton));

  testWidgets('busca só os arquivados e mostra o cabeçalho, a posição e o '
      'cartão com a borda na íris', (tester) async {
    cartoes = [cartao(1), cartao(2), cartao(3, arquivado: false)];
    await abrirArquivo(tester);

    expect(find.byType(TelaArquivo), findsOneWidget);
    final busca = requisicoes.singleWhere(
      (r) => r.url.path == '/api/materias/m1/cartoes',
    );
    expect(busca.method, 'GET');
    expect(busca.url.queryParameters, {'arquivado': 'true'});
    expect(find.text('Arquivo'), findsOneWidget);
    expect(find.text('Redes · cartão 1 de 2'), findsOneWidget);
    expect(find.text('pergunta'), findsOneWidget);
    expect(find.text('Pergunta 1'), findsOneWidget);
    expect(find.text('Pergunta 3'), findsNothing);
    expect(avaliacao, findsNothing);

    final borda = tester
        .widget<Material>(
          find
              .ancestor(
                of: find.text('toque para virar'),
                matching: find.byType(Material),
              )
              .first,
        )
        .shape;
    expect(
      (borda as RoundedRectangleBorder).side.color,
      temaFlipo.colorScheme.tertiary,
    );
    // Abaixo do cartão, logo acima de "Próximo cartão" (quadro 07a).
    expect(
      tester.getTopLeft(desarquivar).dy,
      greaterThan(tester.getBottomLeft(find.text('toque para virar')).dy),
    );
    expect(
      tester.getBottomLeft(desarquivar).dy,
      lessThan(tester.getTopLeft(proximo).dy),
    );
  });

  testWidgets('desarquivar devolve o cartão aos ativos, e a revisão segue no '
      'próximo, pela pergunta', (tester) async {
    cartoes = [cartao(1), cartao(2)];
    await abrirArquivo(tester);
    await tocar(tester, find.text('Pergunta 1'));
    expect(find.text('Resposta 1'), findsOneWidget);

    await tocar(tester, desarquivar);

    expect(mudancas(), ['PATCH /api/cartoes/c1/desarquivar']);
    expect(find.text('Redes · cartão 1 de 1'), findsOneWidget);
    expect(find.text('pergunta'), findsOneWidget);
    expect(find.text('Pergunta 2'), findsOneWidget);
    expect(find.textContaining('Pergunta 1'), findsNothing);
    expect(avaliacao, findsNothing);
  });

  testWidgets('desarquivar o último encerra a revisão, sem recomeçar; voltar '
      'mostra os totais novos', (tester) async {
    cartoes = [cartao(1), cartao(2, arquivado: false)];
    await abrirArquivo(tester);
    expect(listagens(), 1);

    await tocar(tester, desarquivar);

    expect(find.text('Nenhum cartão no Arquivo'), findsOneWidget);
    expect(
      find.text('Os cartões desarquivados voltam para "Estudar cartões".'),
      findsOneWidget,
    );
    expect(find.textContaining('cartão 1 de'), findsNothing);
    expect(find.text('Estudar de novo'), findsNothing);

    await tocar(tester, find.text('Voltar para a matéria'));
    expect(find.byType(TelaArquivo), findsNothing);
    expect(find.byType(TelaMateria), findsOneWidget);
    expect(listagens(), 2);
    expect(find.text('2 ativos'), findsOneWidget);
    expect(find.text('nenhum arquivado'), findsOneWidget);
  });

  testWidgets('passar por todos leva ao fim do Arquivo, que deixa '
      'recomeçar', (tester) async {
    cartoes = [cartao(1), cartao(2)];
    await abrirArquivo(tester);
    await tocar(tester, proximo);
    await tocar(tester, proximo);

    expect(find.text('Você passou pelos cartões arquivados'), findsOneWidget);
    expect(
      find.text('Sem nota, sem placar. Volte quando quiser.'),
      findsOneWidget,
    );
    expect(mudancas(), isEmpty);

    await tocar(tester, find.text('Estudar de novo'));
    expect(find.text('Redes · cartão 1 de 2'), findsOneWidget);
  });

  testWidgets('enquanto desarquiva, o botão fica desabilitado e mostra o '
      'progresso, e voltar espera', (tester) async {
    cartoes = [cartao(1), cartao(2)];
    final resposta = Completer<http.Response>();
    await abrirArquivo(
      tester,
      respostas: {'PATCH /api/cartoes/c1/desarquivar': (_) => resposta.future},
    );

    await tester.tap(desarquivar);
    await tester.pump();

    expect(botaoDesarquivar(tester).onPressed, isNull);
    expect(
      find.descendant(
        of: find.byType(OutlinedButton),
        matching: find.byType(CircularProgressIndicator),
      ),
      findsOneWidget,
    );
    // O ⋮ do mesmo cartão também espera.
    expect(
      tester
          .widget<IconButton>(
            find.ancestor(of: editar, matching: find.byType(IconButton)),
          )
          .onPressed,
      isNull,
    );

    await tester.binding.handlePopRoute();
    await tester.pump();
    expect(find.byType(TelaArquivo), findsOneWidget);

    resposta.complete(json(200, cartao(1, arquivado: false)));
    await tester.pumpAndSettle();
    expect(find.text('Redes · cartão 1 de 1'), findsOneWidget);
    expect(botaoDesarquivar(tester).onPressed, isNotNull);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byType(TelaArquivo), findsNothing);
  });

  testWidgets('falha ao desarquivar mostra o erro e mantém o cartão no '
      'lugar; tentar de novo desarquiva', (tester) async {
    cartoes = [cartao(1), cartao(2)];
    var falhar = true;
    await abrirArquivo(
      tester,
      respostas: {
        'PATCH /api/cartoes/c1/desarquivar': (_) => falhar
            ? json(500, {'mensagem': 'Erro interno.'})
            : json(200, cartao(1, arquivado: false)),
      },
    );
    await tocar(tester, find.text('Pergunta 1'));

    await tocar(tester, desarquivar);

    expect(find.text('Erro interno.'), findsOneWidget);
    expect(find.text('Redes · cartão 1 de 2'), findsOneWidget);
    expect(find.text('Resposta 1'), findsOneWidget);
    expect(botaoDesarquivar(tester).onPressed, isNotNull);

    falhar = false;
    await tocar(tester, desarquivar);

    expect(find.text('Erro interno.'), findsNothing);
    expect(find.text('Redes · cartão 1 de 1'), findsOneWidget);
    expect(find.text('Pergunta 2'), findsOneWidget);
  });

  testWidgets('avançar enquanto desarquiva tira o cartão certo e fica no que '
      'está na tela', (tester) async {
    cartoes = [cartao(1), cartao(2), cartao(3)];
    final resposta = Completer<http.Response>();
    await abrirArquivo(
      tester,
      respostas: {'PATCH /api/cartoes/c1/desarquivar': (_) => resposta.future},
    );

    await tester.tap(desarquivar);
    await tester.pump();
    await tocar(tester, proximo);
    expect(find.text('Redes · cartão 2 de 3'), findsOneWidget);
    await tocar(tester, find.text('Pergunta 2'));
    // O cartão novo tem o botão livre.
    expect(botaoDesarquivar(tester).onPressed, isNotNull);

    resposta.complete(json(200, cartao(1, arquivado: false)));
    await tester.pumpAndSettle();

    expect(find.text('Redes · cartão 1 de 2'), findsOneWidget);
    expect(find.text('Resposta 2'), findsOneWidget);
    await tocar(tester, proximo);
    expect(find.text('Redes · cartão 2 de 2'), findsOneWidget);
    expect(find.text('Pergunta 3'), findsOneWidget);
    await tocar(tester, proximo);
    await tocar(tester, find.text('Estudar de novo'));
    expect(find.text('Pergunta 2'), findsOneWidget);
  });

  group('⋮ de edição', () {
    testWidgets('abre a edição do cartão arquivado; salvar o texto o mantém '
        'no lugar, com o texto novo', (tester) async {
      cartoes = [cartao(1), cartao(2)];
      await abrirArquivo(tester);
      await tocar(tester, proximo);

      await tocar(tester, editar);
      expect(find.byType(TelaEditarCartao), findsOneWidget);
      expect(find.text('Redes · arquivado'), findsOneWidget);
      await tester.enterText(find.byType(TextField).first, 'O que é NAT?');
      await tocar(tester, find.text('Salvar'));

      expect(mudancas(), ['PATCH /api/cartoes/c2']);
      expect(find.byType(TelaArquivo), findsOneWidget);
      expect(find.text('Redes · cartão 2 de 2'), findsOneWidget);
      expect(find.text('O que é NAT?'), findsOneWidget);
    });

    testWidgets('desarquivar pela edição tira o cartão do Arquivo', (
      tester,
    ) async {
      cartoes = [cartao(1), cartao(2)];
      await abrirArquivo(tester);

      await tocar(tester, editar);
      await tocar(tester, find.text('Desarquivar cartão'));

      expect(mudancas(), ['PATCH /api/cartoes/c1/desarquivar']);
      expect(find.text('Redes · cartão 1 de 1'), findsOneWidget);
      expect(find.text('Pergunta 2'), findsOneWidget);
    });

    testWidgets('excluir pela edição, depois de confirmar, tira o cartão do '
        'Arquivo; o último encerra a revisão', (tester) async {
      cartoes = [cartao(1)];
      await abrirArquivo(tester);

      await tocar(tester, editar);
      await tocar(tester, find.text('Excluir cartão'));
      expect(find.text('"Pergunta 1" será apagado de vez.'), findsOneWidget);
      await tocar(tester, find.text('Excluir'));

      expect(mudancas(), ['DELETE /api/cartoes/c1']);
      expect(find.byType(TelaArquivo), findsOneWidget);
      expect(find.text('Nenhum cartão no Arquivo'), findsOneWidget);
    });
  });

  testWidgets('sem arquivados ao abrir, mostra o Arquivo vazio', (
    tester,
  ) async {
    await abrirArquivo(
      tester,
      respostas: {
        // A matéria ainda conta um arquivado, que saiu em outro aparelho.
        'GET /api/materias': (_) => json(200, [
          {'id': 'm1', 'nome': 'Redes', 'totalAtivos': 0, 'totalArquivados': 1},
        ]),
      },
    );

    expect(find.byType(TelaArquivo), findsOneWidget);
    expect(find.text('Nenhum cartão no Arquivo'), findsOneWidget);
    expect(desarquivar, findsNothing);
    expect(find.text('Estudar de novo'), findsNothing);
  });

  testWidgets('falha ao carregar mostra o erro, e "Tentar de novo" carrega', (
    tester,
  ) async {
    cartoes = [cartao(1)];
    final segunda = Completer<http.Response>();
    var tentativas = 0;
    await abrirArquivo(
      tester,
      respostas: {
        'GET /api/materias/m1/cartoes': (_) => ++tentativas == 1
            ? json(500, {'mensagem': 'Erro interno.'})
            : segunda.future,
      },
    );
    expect(find.text('Erro interno.'), findsOneWidget);
    expect(proximo, findsNothing);

    await tester.tap(find.text('Tentar de novo'));
    await tester.pump();
    expect(find.text('Erro interno.'), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    segunda.complete(json(200, [cartao(1)]));
    await tester.pumpAndSettle();
    expect(find.text('Redes · cartão 1 de 1'), findsOneWidget);
  });

  testWidgets('a falha que chega depois de sair da tela não mexe nela', (
    tester,
  ) async {
    cartoes = [cartao(1)];
    final lista = Completer<http.Response>();
    await abrirArquivo(
      tester,
      respostas: {'GET /api/materias/m1/cartoes': (_) => lista.future},
      esperar: false,
    );
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byType(TelaArquivo), findsNothing);
    lista.complete(json(500, {'mensagem': 'Erro interno.'}));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(TelaMateria), findsOneWidget);
  });

  // O "Voltar para a matéria" do fim fecha a tela mesmo com um desarquivar em andamento.
  for (final (nome, resposta) in [
    ('dá certo', () => json(200, cartao(1, arquivado: false))),
    ('falha', () => json(500, {'mensagem': 'Erro interno.'})),
  ]) {
    testWidgets('o desarquivar que $nome depois de sair pelo fim não mexe na '
        'tela', (tester) async {
      cartoes = [cartao(1)];
      final pendente = Completer<http.Response>();
      await abrirArquivo(
        tester,
        respostas: {
          'PATCH /api/cartoes/c1/desarquivar': (_) => pendente.future,
        },
      );
      await tester.tap(desarquivar);
      await tester.pump();
      await tocar(tester, proximo);
      await tocar(tester, find.text('Voltar para a matéria'));
      expect(find.byType(TelaArquivo), findsNothing);

      pendente.complete(resposta());
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(find.byType(TelaMateria), findsOneWidget);
    });
  }

  testWidgets('401 ao carregar volta ao login', (tester) async {
    cartoes = [cartao(1)];
    await abrirArquivo(
      tester,
      respostas: {
        'GET /api/materias/m1/cartoes': (_) => http.Response('', 401),
      },
    );

    expect(tester.takeException(), isNull);
    expect(find.byType(TelaArquivo), findsNothing);
    expect(find.byType(TelaLogin), findsOneWidget);
  });

  testWidgets('401 ao desarquivar volta ao login sem erro na tela que saiu', (
    tester,
  ) async {
    cartoes = [cartao(1)];
    await abrirArquivo(
      tester,
      respostas: {
        'PATCH /api/cartoes/c1/desarquivar': (_) => http.Response('', 401),
      },
    );

    await tocar(tester, desarquivar);

    expect(tester.takeException(), isNull);
    expect(find.byType(TelaArquivo), findsNothing);
    expect(find.byType(TelaLogin), findsOneWidget);
    expect(find.text('Sua sessão expirou. Entre de novo.'), findsOneWidget);
  });
}
