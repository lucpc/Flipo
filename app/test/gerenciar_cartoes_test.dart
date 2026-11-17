import 'dart:async';
import 'dart:convert';

import 'package:flipo/api/api_client.dart';
import 'package:flipo/telas/editar_cartao.dart';
import 'package:flipo/telas/gerenciar_cartoes.dart';
import 'package:flipo/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  late List<http.Request> requisicoes;
  // O que a tela devolveu ao fechar, e se fechou.
  String? aviso;
  late bool fechou;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'flipo_token': 'jwt-ana'});
    requisicoes = [];
    aviso = null;
    fechou = false;
  });

  http.Response json(int status, Object corpo) => http.Response.bytes(
    utf8.encode(jsonEncode(corpo)),
    status,
    headers: {'content-type': 'application/json'},
  );

  Map<String, Object> cartao(int n, {bool arquivado = false}) => {
    'id': 'c$n',
    'pergunta': 'Pergunta $n',
    'resposta': 'Resposta $n',
    'origem': 'manual',
    'arquivado': arquivado,
  };

  Cartao modelo(int n, {bool arquivado = false}) =>
      Cartao.fromJson(cartao(n, arquivado: arquivado));

  /// O cartão como o backend devolve para o `PATCH .../arquivar` ou `.../desarquivar`.
  http.Response mudouArquivo(http.Request requisicao, bool arquivado) {
    final id = requisicao.url.pathSegments[2];
    return json(200, {
      ...cartao(int.parse(id.substring(1))),
      'arquivado': arquivado,
    });
  }

  final navegador = GlobalKey<NavigatorState>();

  /// Abre Gerenciar cartões da matéria Redes por cima de uma tela vazia, que guarda o que ela
  /// devolve ao fechar, como a tela da matéria faz. [ativos] e [arquivados] respondem aos dois
  /// `GET` dos cartões; [respostas] mapeia "MÉTODO caminho" para as demais. Um 401 fecha tudo até
  /// a tela de baixo, como o app faz ao voltar ao login.
  Future<void> abrir(
    WidgetTester tester, {
    required FutureOr<http.Response> Function() ativos,
    FutureOr<http.Response> Function()? arquivados,
    Map<String, FutureOr<http.Response> Function(http.Request)> respostas =
        const {},
  }) async {
    final api = ApiClient(
      aoExpirarSessao: () => navegador.currentState!.popUntil((r) => r.isFirst),
      httpClient: MockClient((requisicao) async {
        requisicoes.add(requisicao);
        final chave = '${requisicao.method} ${requisicao.url.path}';
        if (chave == 'GET /api/materias/m1/cartoes') {
          return requisicao.url.queryParameters['arquivado'] == 'true'
              ? (arquivados ?? () => json(200, []))()
              : ativos();
        }
        return respostas[chave]!(requisicao);
      }),
    );
    final materia = Materia(
      id: 'm1',
      nome: 'Redes',
      totalAtivos: 0,
      totalArquivados: 0,
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: temaFlipo,
        navigatorKey: navegador,
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () async {
                aviso = await Navigator.of(context).push<String>(
                  MaterialPageRoute(
                    builder: (_) =>
                        TelaGerenciarCartoes(api: api, materia: materia),
                  ),
                );
                fechou = true;
              },
              child: const Text('abrir'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('abrir'));
    await tester.pumpAndSettle();
  }

  Future<void> tocar(WidgetTester tester, Finder alvo) async {
    await tester.tap(alvo);
    await tester.pumpAndSettle();
  }

  Future<void> voltar(WidgetTester tester) async {
    await tester.pageBack();
    await tester.pumpAndSettle();
  }

  /// O [alvo] dentro do item da lista que tem a [pergunta].
  Finder noItem(String pergunta, Finder alvo) => find.descendant(
    of: find.ancestor(of: find.text(pergunta), matching: find.byType(Card)),
    matching: alvo,
  );

  /// O ícone do estado do cartão, pelo rótulo que o leitor de tela lê.
  Finder estado(String rotulo) =>
      find.byWidgetPredicate((w) => w is Icon && w.semanticLabel == rotulo);

  double topo(WidgetTester tester, String texto) =>
      tester.getTopLeft(find.text(texto)).dy;

  bool habilitado(WidgetTester tester, String rotulo) =>
      tester
          .widget<ButtonStyleButton>(
            find.ancestor(
              of: find.text(rotulo),
              matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
            ),
          )
          .onPressed !=
      null;

  List<String> chamadas() => [
    for (final r in requisicoes)
      if (r.method != 'GET') '${r.method} ${r.url.path}',
  ];

  final campoPergunta = find.widgetWithText(TextFormField, 'Pergunta');
  final campoResposta = find.widgetWithText(TextFormField, 'Resposta');
  const apoioArquivar =
      'Arquivados saem do estudo e ficam no Arquivo da matéria. Dá para '
      'desarquivar quando quiser.';
  const apoioDesarquivar =
      'O cartão volta para os ativos e reaparece em "Estudar cartões".';
  const avisoArquivado = 'Cartão arquivado — agora está no Arquivo';

  group('a lista', () {
    testWidgets('mostra os ativos e depois os arquivados, cada grupo com o '
        'título e o ícone do estado', (tester) async {
      final semantica = tester.ensureSemantics();
      await abrir(
        tester,
        ativos: () => json(200, [cartao(1), cartao(2)]),
        arquivados: () => json(200, [cartao(3, arquivado: true)]),
      );

      expect(find.text('Gerenciar cartões'), findsOneWidget);
      expect(find.text('Redes'), findsOneWidget);
      expect(
        requisicoes.map((r) => r.url.queryParameters['arquivado']),
        unorderedEquals(['false', 'true']),
      );
      expect(find.text('2 ativos'), findsOneWidget);
      expect(find.text('1 arquivado'), findsOneWidget);
      expect(find.text('Resposta 1'), findsOneWidget);
      final ordem = [
        '2 ativos',
        'Pergunta 1',
        'Pergunta 2',
        '1 arquivado',
        'Pergunta 3',
      ];
      for (var i = 1; i < ordem.length; i++) {
        expect(
          topo(tester, ordem[i - 1]),
          lessThan(topo(tester, ordem[i])),
          reason: '${ordem[i - 1]} antes de ${ordem[i]}',
        );
      }
      expect(noItem('Pergunta 3', estado('arquivado')), findsOneWidget);
      expect(noItem('Pergunta 1', estado('ativo')), findsOneWidget);
      expect(
        tester.getSemantics(find.text('1 arquivado')),
        isSemantics(isHeader: true),
      );
      semantica.dispose();
    });

    testWidgets('sem arquivados, só o grupo dos ativos', (tester) async {
      await abrir(tester, ativos: () => json(200, [cartao(1)]));

      expect(find.text('1 ativo'), findsOneWidget);
      expect(find.textContaining('arquivado'), findsNothing);
    });

    testWidgets('sem cartões, mostra o vazio', (tester) async {
      await abrir(tester, ativos: () => json(200, []));

      expect(find.text('Nenhum cartão nesta matéria'), findsOneWidget);
      expect(find.byType(Card), findsNothing);
    });

    testWidgets('falha ao carregar mostra o erro, e "Tentar de novo" carrega', (
      tester,
    ) async {
      var tentativa = 0;
      await abrir(
        tester,
        ativos: () => tentativa++ == 0
            ? json(500, {'mensagem': 'Erro interno.'})
            : json(200, [cartao(1)]),
      );
      expect(find.text('Erro interno.'), findsOneWidget);
      expect(find.byType(Card), findsNothing);

      await tocar(tester, find.text('Tentar de novo'));

      expect(find.text('Erro interno.'), findsNothing);
      expect(find.text('Pergunta 1'), findsOneWidget);
    });

    testWidgets('a resposta que chega depois de sair da tela não mexe nela', (
      tester,
    ) async {
      final resposta = Completer<http.Response>();
      final api = ApiClient(httpClient: MockClient((_) => resposta.future));
      await tester.pumpWidget(
        MaterialApp(
          home: TelaGerenciarCartoes(
            api: api,
            materia: Materia(
              id: 'm1',
              nome: 'Redes',
              totalAtivos: 0,
              totalArquivados: 0,
            ),
          ),
        ),
      );
      await tester.pumpWidget(const MaterialApp(home: Text('outra tela')));

      resposta.complete(json(500, {'mensagem': 'Erro interno.'}));
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
    });
  });

  group('a edição', () {
    testWidgets('abre com o cartão; salvar grava o texto novo e a lista o '
        'mostra, sem aviso para a matéria', (tester) async {
      await abrir(
        tester,
        ativos: () => json(200, [cartao(1), cartao(2)]),
        respostas: {
          'PATCH /api/cartoes/c1': (r) => json(200, {
            ...cartao(1),
            ...jsonDecode(r.body) as Map<String, dynamic>,
          }),
        },
      );
      await tocar(tester, find.text('Pergunta 1'));

      expect(find.text('Editar cartão'), findsOneWidget);
      expect(find.text('Redes'), findsOneWidget);
      expect(find.text('Pergunta 1'), findsOneWidget);
      expect(find.text('Resposta 1'), findsOneWidget);
      expect(find.text('Arquivar cartão'), findsOneWidget);
      expect(find.text(apoioArquivar), findsOneWidget);
      expect(find.text('Excluir cartão'), findsOneWidget);

      await tester.enterText(campoPergunta, '  O que é NAT? ');
      await tester.enterText(campoResposta, 'Tradução de endereços.');
      await tocar(tester, find.text('Salvar'));

      expect(chamadas(), ['PATCH /api/cartoes/c1']);
      expect(jsonDecode(requisicoes.last.body), {
        'pergunta': 'O que é NAT?',
        'resposta': 'Tradução de endereços.',
      });
      expect(find.text('Gerenciar cartões'), findsOneWidget);
      expect(find.text('O que é NAT?'), findsOneWidget);
      expect(find.text('Pergunta 1'), findsNothing);
      expect(
        topo(tester, 'O que é NAT?'),
        lessThan(topo(tester, 'Pergunta 2')),
      );

      await voltar(tester);
      expect(fechou, isTrue);
      expect(aviso, isNull);
    });

    testWidgets('enquanto salva, o botão carrega e nada mais responde; a '
        'falha mostra o erro e deixa tentar de novo', (tester) async {
      final resposta = Completer<http.Response>();
      await abrir(
        tester,
        ativos: () => json(200, [cartao(1)]),
        respostas: {'PATCH /api/cartoes/c1': (_) => resposta.future},
      );
      await tocar(tester, find.text('Pergunta 1'));
      await tester.enterText(campoPergunta, 'O que é NAT?');
      await tester.tap(find.text('Salvar'));
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Salvar'), findsNothing);
      expect(habilitado(tester, 'Arquivar cartão'), isFalse);
      expect(habilitado(tester, 'Excluir cartão'), isFalse);
      // Voltar não fecha: a lista ficaria diferente da API.
      await tester.pageBack();
      await tester.pump();
      expect(find.text('Editar cartão'), findsOneWidget);

      resposta.complete(json(500, {'mensagem': 'Erro interno.'}));
      await tester.pumpAndSettle();

      expect(find.text('Erro interno.'), findsOneWidget);
      expect(find.text('Editar cartão'), findsOneWidget);
      expect(find.text('O que é NAT?'), findsOneWidget);
      expect(habilitado(tester, 'Salvar'), isTrue);
      expect(habilitado(tester, 'Arquivar cartão'), isTrue);

      // Voltar sem gravar deixa a lista como estava.
      await voltar(tester);
      expect(find.text('Pergunta 1'), findsOneWidget);
      expect(find.text('O que é NAT?'), findsNothing);
    });

    testWidgets('salvar valida os campos antes de gravar', (tester) async {
      await abrir(tester, ativos: () => json(200, [cartao(1)]));
      await tocar(tester, find.text('Pergunta 1'));
      await tester.enterText(campoResposta, '   ');
      await tocar(tester, find.text('Salvar'));

      expect(find.text('Informe a resposta.'), findsOneWidget);
      expect(chamadas(), isEmpty);
    });

    testWidgets('arquivar move o cartão para os arquivados, e voltar leva o '
        'aviso para a matéria', (tester) async {
      await abrir(
        tester,
        ativos: () => json(200, [cartao(1), cartao(2)]),
        respostas: {
          'PATCH /api/cartoes/c1/arquivar': (r) => mudouArquivo(r, true),
        },
      );
      await tocar(tester, find.text('Pergunta 1'));
      await tocar(tester, find.text('Arquivar cartão'));

      expect(chamadas(), ['PATCH /api/cartoes/c1/arquivar']);
      expect(find.text('Gerenciar cartões'), findsOneWidget);
      expect(find.text('1 ativo'), findsOneWidget);
      expect(find.text('1 arquivado'), findsOneWidget);
      expect(noItem('Pergunta 1', estado('arquivado')), findsOneWidget);
      expect(
        topo(tester, 'Pergunta 1'),
        greaterThan(topo(tester, '1 arquivado')),
      );

      await voltar(tester);
      expect(fechou, isTrue);
      expect(aviso, avisoArquivado);
    });

    testWidgets('desarquivar devolve o cartão aos ativos; o aviso de arquivar '
        'antes dele não vale mais', (tester) async {
      await abrir(
        tester,
        ativos: () => json(200, [cartao(1)]),
        respostas: {
          'PATCH /api/cartoes/c1/arquivar': (r) => mudouArquivo(r, true),
          'PATCH /api/cartoes/c1/desarquivar': (r) => mudouArquivo(r, false),
        },
      );
      await tocar(tester, find.text('Pergunta 1'));
      await tocar(tester, find.text('Arquivar cartão'));
      await tocar(tester, find.text('Pergunta 1'));

      expect(find.text('Redes · arquivado'), findsOneWidget);
      expect(find.text('Desarquivar cartão'), findsOneWidget);
      expect(find.text('Arquivar cartão'), findsNothing);
      expect(find.text(apoioDesarquivar), findsOneWidget);

      await tocar(tester, find.text('Desarquivar cartão'));

      expect(chamadas(), [
        'PATCH /api/cartoes/c1/arquivar',
        'PATCH /api/cartoes/c1/desarquivar',
      ]);
      expect(find.text('1 ativo'), findsOneWidget);
      expect(find.textContaining('arquivado'), findsNothing);

      await voltar(tester);
      expect(fechou, isTrue);
      expect(aviso, isNull);
    });

    testWidgets('falha ao arquivar mostra o erro e o cartão continua ativo', (
      tester,
    ) async {
      await abrir(
        tester,
        ativos: () => json(200, [cartao(1)]),
        respostas: {
          'PATCH /api/cartoes/c1/arquivar': (_) =>
              json(404, {'mensagem': 'Cartão não encontrado.'}),
        },
      );
      await tocar(tester, find.text('Pergunta 1'));
      await tocar(tester, find.text('Arquivar cartão'));

      expect(find.text('Cartão não encontrado.'), findsOneWidget);
      expect(find.text('Arquivar cartão'), findsOneWidget);

      await voltar(tester);
      expect(find.text('1 ativo'), findsOneWidget);
    });

    testWidgets('excluir pede confirmação sugerindo arquivar: cancelar não '
        'apaga, confirmar apaga e tira da lista', (tester) async {
      await abrir(
        tester,
        ativos: () => json(200, [cartao(1), cartao(2)]),
        respostas: {'DELETE /api/cartoes/c1': (_) => http.Response('', 204)},
      );
      await tocar(tester, find.text('Pergunta 1'));
      await tocar(tester, find.text('Excluir cartão'));

      expect(find.text('Excluir cartão?'), findsOneWidget);
      expect(
        find.text(
          '"Pergunta 1" será apagado de vez. Se você só quer tirá-lo do '
          'estudo, use "Arquivar cartão".',
        ),
        findsOneWidget,
      );
      await tocar(tester, find.text('Cancelar'));
      expect(find.byType(AlertDialog), findsNothing);
      expect(find.text('Editar cartão'), findsOneWidget);
      expect(chamadas(), isEmpty);

      await tocar(tester, find.text('Excluir cartão'));
      await tocar(tester, find.text('Excluir'));

      expect(chamadas(), ['DELETE /api/cartoes/c1']);
      expect(find.text('Gerenciar cartões'), findsOneWidget);
      expect(find.text('Pergunta 1'), findsNothing);
      expect(find.text('1 ativo'), findsOneWidget);

      await voltar(tester);
      expect(aviso, isNull);
    });

    testWidgets('a confirmação de excluir um arquivado não sugere arquivar', (
      tester,
    ) async {
      await abrir(
        tester,
        ativos: () => json(200, []),
        arquivados: () => json(200, [cartao(3, arquivado: true)]),
      );
      await tocar(tester, find.text('Pergunta 3'));
      await tocar(tester, find.text('Excluir cartão'));

      expect(find.text('"Pergunta 3" será apagado de vez.'), findsOneWidget);
    });

    testWidgets('as ações destrutivas usam a cor de erro do tema', (
      tester,
    ) async {
      await abrir(tester, ativos: () => json(200, [cartao(1)]));
      await tocar(tester, find.text('Pergunta 1'));
      final erro = temaFlipo.colorScheme.error;

      final link = tester.widget<TextButton>(
        find.widgetWithText(TextButton, 'Excluir cartão'),
      );
      expect(link.style?.foregroundColor?.resolve({}), erro);
      await tocar(tester, find.text('Excluir cartão'));
      final excluir = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Excluir'),
      );
      expect(excluir.style?.backgroundColor?.resolve({}), erro);
    });

    testWidgets('401 ao salvar volta ao início sem erro na tela que saiu', (
      tester,
    ) async {
      await abrir(
        tester,
        ativos: () => json(200, [cartao(1)]),
        respostas: {'PATCH /api/cartoes/c1': (_) => http.Response('', 401)},
      );
      await tocar(tester, find.text('Pergunta 1'));
      await tocar(tester, find.text('Salvar'));

      expect(tester.takeException(), isNull);
      expect(find.byType(TelaEditarCartao), findsNothing);
      expect(find.byType(TelaGerenciarCartoes), findsNothing);
      expect(find.text('abrir'), findsOneWidget);
    });
  });

  group('o resultado da edição', () {
    List<String> resumo(List<Cartao> cartoes) => [
      for (final c in cartoes) '${c.id}:${c.pergunta}:${c.arquivado}',
    ];

    final lista = [modelo(1), modelo(2), modelo(3, arquivado: true)];
    final editado = Cartao(
      id: 'c2',
      pergunta: 'Nova',
      resposta: 'Resposta 2',
      origem: 'manual',
      arquivado: false,
    );

    test('editado troca o cartão no lugar, em qualquer lista', () {
      final resultado = CartaoEditado(editado);

      expect(resultado.id, 'c2');
      expect(resultado.cartao, same(editado));
      expect(resultado.aviso, isNull);
      expect(resumo(resultado.aplicarEm(lista)), [
        'c1:Pergunta 1:false',
        'c2:Nova:false',
        'c3:Pergunta 3:true',
      ]);
      expect(
        resumo(resultado.aplicarEm(lista, arquivados: false)),
        resumo(resultado.aplicarEm(lista)),
      );
    });

    test('arquivado troca o cartão, ou o tira de uma lista só de ativos, e '
        'deixa o aviso', () {
      final resultado = CartaoArquivado(modelo(1, arquivado: true));

      expect(resultado.aviso, avisoArquivado);
      expect(resumo(resultado.aplicarEm(lista)), [
        'c1:Pergunta 1:true',
        'c2:Pergunta 2:false',
        'c3:Pergunta 3:true',
      ]);
      // A sessão de estudo (#13).
      expect(resumo(resultado.aplicarEm(lista, arquivados: false)), [
        'c2:Pergunta 2:false',
        'c3:Pergunta 3:true',
      ]);
    });

    test('desarquivado tira o cartão de uma lista só de arquivados, sem '
        'aviso', () {
      final resultado = CartaoDesarquivado(modelo(3));

      expect(resultado.aviso, isNull);
      expect(resumo(resultado.aplicarEm(lista)), [
        'c1:Pergunta 1:false',
        'c2:Pergunta 2:false',
        'c3:Pergunta 3:false',
      ]);
      // O Arquivo (#23).
      expect(resumo(resultado.aplicarEm(lista, arquivados: true)), [
        'c1:Pergunta 1:false',
        'c2:Pergunta 2:false',
      ]);
    });

    test('excluído sai de qualquer lista', () {
      const resultado = CartaoExcluido('c1');

      expect(resultado.cartao, isNull);
      expect(resultado.aviso, isNull);
      expect(resumo(resultado.aplicarEm(lista)), [
        'c2:Pergunta 2:false',
        'c3:Pergunta 3:true',
      ]);
      expect(resumo(resultado.aplicarEm(lista, arquivados: true)), [
        'c2:Pergunta 2:false',
        'c3:Pergunta 3:true',
      ]);
    });

    test('um cartão que não está na lista não muda nada', () {
      expect(
        resumo(const CartaoExcluido('c9').aplicarEm(lista)),
        resumo(lista),
      );
    });
  });
}
