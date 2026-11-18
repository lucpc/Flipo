import 'dart:async';
import 'dart:convert';

import 'package:flipo/main.dart';
import 'package:flipo/telas/adicionar_cartao.dart';
import 'package:flipo/telas/autenticacao.dart';
import 'package:flipo/telas/editar_cartao.dart';
import 'package:flipo/telas/estudo.dart';
import 'package:flipo/telas/materias.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  late List<http.Request> requisicoes;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'flipo_token': 'jwt-ana'});
    requisicoes = [];
  });

  http.Response json(int status, Object corpo) => http.Response.bytes(
    utf8.encode(jsonEncode(corpo)),
    status,
    headers: {'content-type': 'application/json'},
  );

  Map<String, Object> cartao(int n) => {
    'id': 'c$n',
    'pergunta': 'Pergunta $n',
    'resposta': 'Resposta $n',
    'origem': 'manual',
    'arquivado': false,
  };

  /// Respostas em sequência; a última se repete.
  FutureOr<http.Response> Function() emSequencia(
    List<FutureOr<http.Response> Function()> lista,
  ) {
    var i = 0;
    return () => lista[i < lista.length - 1 ? i++ : i]();
  }

  /// Entra no app já logado, abre a matéria Redes e toca em "Estudar cartões". [cartoes] responde
  /// ao `GET` dos cartões da matéria; [outras] mapeia "MÉTODO caminho" para as demais (ex.: as da
  /// edição). Com [esperar] falso, não espera a tela assentar (um carregamento pendente gira para
  /// sempre): só a transição da rota.
  Future<void> abrirEstudo(
    WidgetTester tester,
    FutureOr<http.Response> Function() cartoes, {
    bool esperar = true,
    Map<String, FutureOr<http.Response> Function()> outras = const {},
  }) async {
    final respostas = <String, FutureOr<http.Response> Function()>{
      'GET /api/usuarios/me': () =>
          json(200, {'id': 'u1', 'nome': 'Ana', 'email': 'ana@exemplo.com'}),
      'GET /api/materias': () => json(200, [
        {'id': 'm1', 'nome': 'Redes', 'totalAtivos': 3, 'totalArquivados': 1},
      ]),
      'GET /api/materias/m1/cartoes': cartoes,
      'POST /api/materias/m1/cartoes': () => json(201, cartao(1)),
      ...outras,
    };
    await tester.pumpWidget(
      FlipoApp(
        httpClient: MockClient((requisicao) async {
          requisicoes.add(requisicao);
          return respostas['${requisicao.method} ${requisicao.url.path}']!();
        }),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Redes'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Estudar cartões'));
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

  final proximo = find.text('Próximo cartão');
  final avaliacao = find.textContaining(
    RegExp('acertei|errei|como você se saiu', caseSensitive: false),
  );

  testWidgets('busca só os cartões ativos e começa pelo primeiro, na '
      'pergunta', (tester) async {
    await abrirEstudo(tester, () => json(200, [cartao(1), cartao(2)]));

    final busca = requisicoes.singleWhere(
      (r) => r.url.path == '/api/materias/m1/cartoes',
    );
    expect(busca.method, 'GET');
    expect(busca.url.queryParameters, {'arquivado': 'false'});
    expect(find.text('Redes'), findsOneWidget);
    expect(find.text('cartão 1 de 2'), findsOneWidget);
    expect(find.text('pergunta'), findsOneWidget);
    expect(find.text('Pergunta 1'), findsOneWidget);
    expect(find.text('toque para virar'), findsOneWidget);
    expect(find.text('Resposta 1'), findsNothing);
    expect(avaliacao, findsNothing);
  });

  testWidgets('tocar no cartão vira entre pergunta e resposta', (tester) async {
    await abrirEstudo(tester, () => json(200, [cartao(1)]));

    await tocar(tester, find.text('Pergunta 1'));
    expect(find.text('resposta'), findsOneWidget);
    expect(find.text('Resposta 1'), findsOneWidget);
    expect(find.text('Pergunta 1'), findsNothing);
    expect(avaliacao, findsNothing);

    await tocar(tester, find.text('Resposta 1'));
    expect(find.text('pergunta'), findsOneWidget);
    expect(find.text('Pergunta 1'), findsOneWidget);
  });

  testWidgets('"Próximo cartão" avança, atualiza a posição, e o cartão '
      'seguinte começa pela pergunta', (tester) async {
    await abrirEstudo(tester, () => json(200, [cartao(1), cartao(2)]));
    await tocar(tester, find.text('Pergunta 1'));

    await tocar(tester, proximo);

    expect(find.text('cartão 2 de 2'), findsOneWidget);
    expect(find.text('pergunta'), findsOneWidget);
    expect(find.text('Pergunta 2'), findsOneWidget);
    expect(find.text('Resposta 2'), findsNothing);
  });

  testWidgets('depois do último vem o fim; "Estudar de novo" recomeça e '
      '"Voltar para a matéria" fecha', (tester) async {
    await abrirEstudo(
      tester,
      () => json(200, [cartao(1), cartao(2), cartao(3)]),
    );
    for (var i = 0; i < 3; i++) {
      await tocar(tester, proximo);
    }

    expect(find.byIcon(Icons.check_circle_outline), findsOneWidget);
    expect(find.text('Você passou pelos cartões ativos'), findsOneWidget);
    expect(
      find.text('Sem nota, sem placar. Volte quando quiser.'),
      findsOneWidget,
    );
    expect(find.textContaining('cartão 3 de 3'), findsNothing);
    expect(avaliacao, findsNothing);

    await tocar(tester, find.text('Estudar de novo'));
    expect(find.text('cartão 1 de 3'), findsOneWidget);
    expect(find.text('pergunta'), findsOneWidget);
    expect(find.text('Pergunta 1'), findsOneWidget);

    for (var i = 0; i < 3; i++) {
      await tocar(tester, proximo);
    }
    await tocar(tester, find.text('Voltar para a matéria'));

    expect(find.byType(TelaEstudo), findsNothing);
    expect(find.byType(TelaMateria), findsOneWidget);
    // A matéria recarrega os totais ao voltar.
    expect(requisicoes.where((r) => r.url.path == '/api/materias').length, 2);
  });

  testWidgets('sem cartões ativos, "Adicionar cartão" abre a criação e, ao '
      'salvar, a sessão recarrega com o cartão novo', (tester) async {
    await abrirEstudo(
      tester,
      emSequencia([
        () => json(200, []),
        () => json(200, [cartao(1)]),
      ]),
    );

    expect(find.text('Nenhum cartão ativo nesta matéria'), findsOneWidget);
    expect(proximo, findsNothing);

    await tocar(tester, find.widgetWithText(FilledButton, 'Adicionar cartão'));
    expect(find.byType(TelaAdicionarCartao), findsOneWidget);

    await tocar(tester, find.text('Criar manualmente'));
    await tester.enterText(find.byType(TextField).at(0), 'Pergunta 1');
    await tester.enterText(find.byType(TextField).at(1), 'Resposta 1');
    await tocar(tester, find.text('Ir para revisão'));
    await tocar(tester, find.text('Confirmar revisão (1 cartão)'));

    expect(find.byType(TelaAdicionarCartao), findsNothing);
    expect(find.byType(TelaEstudo), findsOneWidget);
    expect(find.text('Pergunta 1'), findsOneWidget);
    expect(find.text('cartão 1 de 1'), findsOneWidget);
    expect(
      requisicoes
          .where((r) => r.url.path == '/api/materias/m1/cartoes')
          .map((r) => r.method),
      ['GET', 'POST', 'GET'],
    );
  });

  testWidgets('sair da criação sem salvar volta para a sessão vazia sem '
      'recarregar', (tester) async {
    await abrirEstudo(tester, () => json(200, []));

    await tocar(tester, find.widgetWithText(FilledButton, 'Adicionar cartão'));
    await tocar(tester, find.byType(BackButton));

    expect(find.byType(TelaAdicionarCartao), findsNothing);
    expect(find.text('Nenhum cartão ativo nesta matéria'), findsOneWidget);
    expect(
      requisicoes.where((r) => r.url.path == '/api/materias/m1/cartoes').length,
      1,
    );
  });

  testWidgets('falha ao carregar mostra o erro, e "Tentar de novo" carrega', (
    tester,
  ) async {
    final segundaTentativa = Completer<http.Response>();
    await abrirEstudo(
      tester,
      emSequencia([
        () => json(500, {'mensagem': 'Erro interno.'}),
        () => segundaTentativa.future,
      ]),
    );
    expect(find.text('Erro interno.'), findsOneWidget);
    expect(proximo, findsNothing);

    await tester.tap(find.text('Tentar de novo'));
    await tester.pump();
    expect(find.text('Erro interno.'), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    segundaTentativa.complete(json(200, [cartao(1)]));
    await tester.pumpAndSettle();
    expect(find.text('cartão 1 de 1'), findsOneWidget);
    expect(find.text('Pergunta 1'), findsOneWidget);
  });

  testWidgets('a falha que chega depois de sair da tela não mexe nela', (
    tester,
  ) async {
    final cartoes = Completer<http.Response>();
    await abrirEstudo(tester, () => cartoes.future, esperar: false);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byType(TelaEstudo), findsNothing);
    cartoes.complete(json(500, {'mensagem': 'Erro interno.'}));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(TelaMateria), findsOneWidget);
  });

  testWidgets('401 ao carregar volta ao login sem erro na tela que saiu', (
    tester,
  ) async {
    await abrirEstudo(tester, () => http.Response('', 401));

    expect(tester.takeException(), isNull);
    expect(find.byType(TelaEstudo), findsNothing);
    expect(find.byType(TelaLogin), findsOneWidget);
  });

  group('editar a partir da sessão', () {
    final editar = find.byTooltip('Editar cartão');
    const avisoArquivado = 'Cartão arquivado — agora está no Arquivo';

    Map<String, Object> arquivado(int n) => {...cartao(n), 'arquivado': true};

    List<String> chamadas() => [
      for (final r in requisicoes)
        if (r.method != 'GET') '${r.method} ${r.url.path}',
    ];

    int recargasDaMateria() =>
        requisicoes.where((r) => r.url.path == '/api/materias').length;

    // As três saídas da sessão: o voltar do AppBar, o do sistema e "Voltar para a matéria", que
    // só existe no fim (aqui, depois de passar pelos cartões que restam).
    final saidas = <String, Future<void> Function(WidgetTester)>{
      'o voltar do AppBar': (tester) => tocar(tester, find.byType(BackButton)),
      'o voltar do sistema': (tester) async {
        await tester.binding.handlePopRoute();
        await tester.pumpAndSettle();
      },
      '"Voltar para a matéria"': (tester) async {
        while (proximo.evaluate().isNotEmpty) {
          await tocar(tester, proximo);
        }
        await tocar(tester, find.text('Voltar para a matéria'));
      },
    };

    testWidgets('o ⋮ abre a edição do cartão atual sem virá-lo, e voltar sem '
        'gravar deixa a sessão no mesmo lugar', (tester) async {
      await abrirEstudo(tester, () => json(200, [cartao(1), cartao(2)]));
      await tocar(tester, proximo);

      await tocar(tester, editar);

      expect(find.byType(TelaEditarCartao), findsOneWidget);
      expect(find.widgetWithText(TextFormField, 'Pergunta 2'), findsOneWidget);
      await tocar(tester, find.byType(BackButton));
      expect(find.text('cartão 2 de 2'), findsOneWidget);
      expect(find.text('pergunta'), findsOneWidget);
      expect(find.text('Pergunta 2'), findsOneWidget);
      expect(chamadas(), isEmpty);
    });

    testWidgets('o ⋮ é um botão à parte do cartão, com rótulo para o leitor '
        'de tela', (tester) async {
      final semantica = tester.ensureSemantics();
      await abrirEstudo(tester, () => json(200, [cartao(1)]));

      expect(
        tester.getSemantics(editar),
        isSemantics(tooltip: 'Editar cartão', isButton: true),
      );
      // Não entra no nó do cartão, que segue só com a face.
      expect(
        tester.getSemantics(find.text('pergunta')),
        isSemantics(
          label: 'pergunta\nPergunta 1\ntoque para virar',
          tooltip: '',
        ),
      );
      expect(
        tester.getSemantics(editar).id,
        isNot(tester.getSemantics(find.text('pergunta')).id),
      );
      semantica.dispose();
    });

    testWidgets('salvar atualiza o cartão no lugar, na mesma posição, e sair '
        'não leva aviso', (tester) async {
      await abrirEstudo(
        tester,
        () => json(200, [cartao(1), cartao(2), cartao(3)]),
        outras: {
          'PATCH /api/cartoes/c2': () =>
              json(200, {...cartao(2), 'pergunta': 'Pergunta nova'}),
        },
      );
      await tocar(tester, proximo);
      await tocar(tester, editar);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Pergunta 2'),
        'Pergunta nova',
      );
      await tocar(tester, find.text('Salvar'));

      expect(chamadas(), ['PATCH /api/cartoes/c2']);
      expect(find.byType(TelaEditarCartao), findsNothing);
      expect(find.text('cartão 2 de 3'), findsOneWidget);
      expect(find.text('Pergunta nova'), findsOneWidget);
      await tocar(tester, proximo);
      expect(find.text('cartão 3 de 3'), findsOneWidget);
      expect(find.text('Pergunta 3'), findsOneWidget);

      await tocar(tester, find.byType(BackButton));
      expect(find.byType(TelaMateria), findsOneWidget);
      expect(find.text(avisoArquivado), findsNothing);
      expect(recargasDaMateria(), 2);
    });

    testWidgets('arquivar tira o cartão do baralho e mostra o próximo, pela '
        'pergunta', (tester) async {
      await abrirEstudo(
        tester,
        () => json(200, [cartao(1), cartao(2), cartao(3)]),
        outras: {
          'PATCH /api/cartoes/c2/arquivar': () => json(200, arquivado(2)),
        },
      );
      await tocar(tester, proximo);
      await tocar(tester, find.text('Pergunta 2'));
      await tocar(tester, editar);

      await tocar(tester, find.text('Arquivar cartão'));

      expect(chamadas(), ['PATCH /api/cartoes/c2/arquivar']);
      expect(find.text('cartão 2 de 2'), findsOneWidget);
      expect(find.text('pergunta'), findsOneWidget);
      expect(find.text('Pergunta 3'), findsOneWidget);
    });

    testWidgets('arquivar o último cartão ativo mostra o fim, sem "Estudar de '
        'novo"', (tester) async {
      await abrirEstudo(
        tester,
        () => json(200, [cartao(1)]),
        outras: {
          'PATCH /api/cartoes/c1/arquivar': () => json(200, arquivado(1)),
        },
      );
      await tocar(tester, editar);

      await tocar(tester, find.text('Arquivar cartão'));

      expect(find.text('Você passou pelos cartões ativos'), findsOneWidget);
      expect(find.text('Estudar de novo'), findsNothing);
      expect(find.text('Voltar para a matéria'), findsOneWidget);
      expect(find.text('Nenhum cartão ativo nesta matéria'), findsNothing);
    });

    testWidgets('excluir, confirmado, tira o cartão do baralho; sair não leva '
        'aviso', (tester) async {
      await abrirEstudo(
        tester,
        () => json(200, [cartao(1), cartao(2)]),
        outras: {'DELETE /api/cartoes/c1': () => http.Response('', 204)},
      );
      await tocar(tester, editar);

      await tocar(tester, find.text('Excluir cartão'));
      await tocar(tester, find.text('Excluir'));

      expect(chamadas(), ['DELETE /api/cartoes/c1']);
      expect(find.text('cartão 1 de 1'), findsOneWidget);
      expect(find.text('Pergunta 2'), findsOneWidget);

      await saidas['"Voltar para a matéria"']!(tester);
      expect(find.byType(TelaMateria), findsOneWidget);
      expect(find.text(avisoArquivado), findsNothing);
    });

    for (final MapEntry(key: nome, value: sair) in saidas.entries) {
      testWidgets('com um cartão arquivado, $nome leva o aviso para a matéria, '
          'que recarrega os totais', (tester) async {
        await abrirEstudo(
          tester,
          () => json(200, [cartao(1), cartao(2)]),
          outras: {
            'PATCH /api/cartoes/c1/arquivar': () => json(200, arquivado(1)),
          },
        );
        await tocar(tester, editar);
        await tocar(tester, find.text('Arquivar cartão'));
        expect(find.text('Pergunta 2'), findsOneWidget);

        await sair(tester);

        expect(find.byType(TelaEstudo), findsNothing);
        expect(find.byType(TelaMateria), findsOneWidget);
        expect(find.text(avisoArquivado), findsOneWidget);
        expect(recargasDaMateria(), 2);
      });
    }

    testWidgets('excluir outro cartão depois de arquivar não tira o aviso', (
      tester,
    ) async {
      await abrirEstudo(
        tester,
        () => json(200, [cartao(1), cartao(2), cartao(3)]),
        outras: {
          'PATCH /api/cartoes/c1/arquivar': () => json(200, arquivado(1)),
          'DELETE /api/cartoes/c2': () => http.Response('', 204),
        },
      );
      await tocar(tester, editar);
      await tocar(tester, find.text('Arquivar cartão'));
      await tocar(tester, editar);
      await tocar(tester, find.text('Excluir cartão'));
      await tocar(tester, find.text('Excluir'));

      expect(chamadas(), [
        'PATCH /api/cartoes/c1/arquivar',
        'DELETE /api/cartoes/c2',
      ]);
      expect(find.text('Pergunta 3'), findsOneWidget);
      await tocar(tester, find.byType(BackButton));

      expect(find.byType(TelaMateria), findsOneWidget);
      expect(find.text(avisoArquivado), findsOneWidget);
    });

    testWidgets('401 ao editar volta ao login, mesmo com um aviso guardado', (
      tester,
    ) async {
      await abrirEstudo(
        tester,
        () => json(200, [cartao(1), cartao(2)]),
        outras: {
          'PATCH /api/cartoes/c1/arquivar': () => json(200, arquivado(1)),
          'PATCH /api/cartoes/c2': () => http.Response('', 401),
        },
      );
      await tocar(tester, editar);
      await tocar(tester, find.text('Arquivar cartão'));
      await tocar(tester, editar);

      await tocar(tester, find.text('Salvar'));

      expect(tester.takeException(), isNull);
      expect(find.byType(TelaEditarCartao), findsNothing);
      expect(find.byType(TelaEstudo), findsNothing);
      expect(find.byType(TelaMateria), findsNothing);
      expect(find.byType(TelaLogin), findsOneWidget);
    });
  });
}
