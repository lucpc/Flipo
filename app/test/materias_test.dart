import 'dart:async';
import 'dart:convert';

import 'package:flipo/main.dart';
import 'package:flipo/telas/estudo.dart';
import 'package:flipo/telas/autenticacao.dart';
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

  Map<String, Object> materia(
    String id,
    String nome,
    int ativos,
    int arquivados,
  ) => {
    'id': id,
    'nome': nome,
    'totalAtivos': ativos,
    'totalArquivados': arquivados,
  };

  /// Respostas de `GET /api/materias` em sequência; a última se repete.
  FutureOr<http.Response> Function() emSequencia(
    List<FutureOr<http.Response> Function()> lista,
  ) {
    var i = 0;
    return () => lista[i < lista.length - 1 ? i++ : i]();
  }

  /// Abre o app já logado, com um backend falso: [respostas] mapeia "MÉTODO caminho" para a
  /// resposta. A usuária se chama Ana.
  Future<void> abrirApp(
    WidgetTester tester,
    Map<String, FutureOr<http.Response> Function()> respostas,
  ) async {
    final todas = <String, FutureOr<http.Response> Function()>{
      'GET /api/usuarios/me': () =>
          json(200, {'id': 'u1', 'nome': 'Ana', 'email': 'ana@exemplo.com'}),
      'GET /api/materias/m1/cartoes': () => json(200, []),
      ...respostas,
    };
    await tester.pumpWidget(
      FlipoApp(
        httpClient: MockClient((requisicao) async {
          requisicoes.add(requisicao);
          return todas['${requisicao.method} ${requisicao.url.path}']!();
        }),
      ),
    );
    await tester.pumpAndSettle();
  }

  int listagens() => requisicoes
      .where((r) => r.method == 'GET' && r.url.path == '/api/materias')
      .length;

  Finder botao(String rotulo) =>
      find.ancestor(of: find.text(rotulo), matching: find.byType(FilledButton));

  testWidgets('saudação usa o nome da usuária e o avatar, a inicial', (
    tester,
  ) async {
    await abrirApp(tester, {'GET /api/materias': () => json(200, [])});

    expect(find.text('Minhas matérias'), findsOneWidget);
    expect(find.text('Olá, Ana! O que vamos estudar?'), findsOneWidget);
    expect(
      find.descendant(of: find.byType(CircleAvatar), matching: find.text('A')),
      findsOneWidget,
    );
  });

  testWidgets('cada matéria mostra quantos cartões ativos tem', (tester) async {
    await abrirApp(tester, {
      'GET /api/materias': () => json(200, [
        materia('m1', 'Redes', 6, 2),
        materia('m2', 'Cálculo', 1, 0),
        materia('m3', 'Física', 0, 4),
        materia('m4', 'história', 0, 0),
      ]),
    });

    expect(find.text('6 ativos'), findsOneWidget);
    expect(find.text('1 ativo'), findsOneWidget);
    expect(find.text('tudo arquivado'), findsOneWidget);
    expect(find.text('nenhum cartão'), findsOneWidget);
    // A inicial de cada matéria, em maiúscula.
    expect(find.text('H'), findsOneWidget);
  });

  testWidgets('sem matérias, mostra o vazio e "Nova matéria" cria uma', (
    tester,
  ) async {
    await abrirApp(tester, {
      'GET /api/materias': emSequencia([
        () => json(200, []),
        () => json(200, [materia('m9', 'Biologia', 0, 0)]),
      ]),
      'POST /api/materias': () => json(201, materia('m9', 'Biologia', 0, 0)),
    });
    expect(find.text('Nenhuma matéria ainda'), findsOneWidget);

    await tester.tap(botao('Nova matéria'));
    await tester.pumpAndSettle();
    await tester.tap(botao('Criar'));
    await tester.pump();
    expect(find.text('Informe o nome.'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '  Biologia ');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    final post = requisicoes.singleWhere((r) => r.method == 'POST');
    expect(jsonDecode(post.body), {'nome': 'Biologia'});
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.text('Biologia'), findsOneWidget);
    expect(find.text('nenhum cartão'), findsOneWidget);
    expect(find.text('Nenhuma matéria ainda'), findsNothing);
  });

  testWidgets('criar a matéria recarrega a lista, e uma resposta antiga que '
      'chega depois é descartada', (tester) async {
    final recargaLenta = Completer<http.Response>();
    await abrirApp(tester, {
      'GET /api/materias': emSequencia([
        () => json(200, [materia('m1', 'Redes', 6, 2)]),
        () => recargaLenta.future,
        () => json(200, [
          materia('m1', 'Redes', 7, 2),
          materia('m9', 'Biologia', 0, 0),
        ]),
      ]),
      'POST /api/materias': () => json(201, materia('m9', 'Biologia', 0, 0)),
    });

    // Puxar para recarregar; a resposta fica pendente.
    await tester.fling(find.text('Redes'), const Offset(0, 300), 1000);
    for (var i = 0; i < 3; i++) {
      await tester.pump(const Duration(seconds: 1));
    }
    expect(listagens(), 2);

    // Com o indicador girando, pumpAndSettle não termina: avança o tempo à mão.
    await tester.tap(botao('Nova matéria'));
    await tester.pump(const Duration(seconds: 1));
    await tester.enterText(find.byType(TextField), 'Biologia');
    await tester.tap(botao('Criar'));
    await tester.pump(const Duration(seconds: 1));
    expect(listagens(), 3);
    expect(find.text('7 ativos'), findsOneWidget);

    // A recarga que saiu antes da criação chega por último, com dados velhos.
    recargaLenta.complete(json(200, [materia('m1', 'Redes', 6, 2)]));
    await tester.pumpAndSettle();

    expect(find.text('7 ativos'), findsOneWidget);
    expect(find.text('Biologia'), findsOneWidget);
  });

  testWidgets('erro ao criar a matéria fica no diálogo', (tester) async {
    await abrirApp(tester, {
      'GET /api/materias': () => json(200, []),
      'POST /api/materias': () => json(500, {'mensagem': 'Erro interno.'}),
    });

    await tester.tap(botao('Nova matéria'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Biologia');
    await tester.tap(botao('Criar'));
    await tester.pumpAndSettle();

    expect(find.byType(AlertDialog), findsOneWidget);
    expect(find.text('Erro interno.'), findsOneWidget);
    expect(tester.widget<FilledButton>(botao('Criar')).onPressed, isNotNull);
  });

  testWidgets('falha ao carregar mostra o erro e "Tentar de novo" recarrega', (
    tester,
  ) async {
    await abrirApp(tester, {
      'GET /api/materias': emSequencia([
        () => json(500, {'mensagem': 'Erro interno.'}),
        () => json(200, [materia('m1', 'Redes', 6, 2)]),
      ]),
    });

    expect(find.text('Erro interno.'), findsOneWidget);
    expect(
      tester.widget<FilledButton>(botao('Nova matéria')).onPressed,
      isNull,
    );

    await tester.tap(find.text('Tentar de novo'));
    await tester.pumpAndSettle();

    expect(find.text('Erro interno.'), findsNothing);
    expect(find.text('Redes'), findsOneWidget);
    expect(find.text('6 ativos'), findsOneWidget);
  });

  testWidgets('puxar a lista para baixo recarrega', (tester) async {
    await abrirApp(tester, {
      'GET /api/materias': emSequencia([
        () => json(200, [materia('m1', 'Redes', 6, 2)]),
        () => json(200, [materia('m1', 'Redes', 7, 2)]),
      ]),
    });

    await tester.fling(find.text('Redes'), const Offset(0, 300), 1000);
    await tester.pumpAndSettle();

    expect(find.text('7 ativos'), findsOneWidget);
  });

  testWidgets('a matéria mostra os totais e recarrega ao voltar de outra '
      'tela', (tester) async {
    await abrirApp(tester, {
      'GET /api/materias': emSequencia([
        () => json(200, [materia('m1', 'Redes', 6, 2)]),
        () => json(200, [materia('m1', 'Redes', 5, 3)]),
        () => json(200, [materia('m1', 'Redes', 4, 3)]),
      ]),
    });

    await tester.tap(find.text('Redes'));
    await tester.pumpAndSettle();
    expect(find.text('6 ativos'), findsOneWidget);
    expect(find.text('2 arquivados'), findsOneWidget);
    expect(
      find.text(
        'Estude no seu ritmo: vire o cartão e avance. Sem notas, sem prazos.',
      ),
      findsOneWidget,
    );
    // Abrir a matéria usa os totais que a lista acabou de trazer.
    expect(listagens(), 1);

    await tester.tap(find.text('Estudar cartões'));
    await tester.pumpAndSettle();
    expect(find.byType(TelaEstudo), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(find.text('5 ativos'), findsOneWidget);
    expect(find.text('3 arquivados'), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(find.text('Minhas matérias'), findsOneWidget);
    expect(find.text('4 ativos'), findsOneWidget);
  });

  testWidgets('falha ao recarregar a matéria mostra o erro e "Tentar de novo" '
      'recupera', (tester) async {
    await abrirApp(tester, {
      'GET /api/materias': emSequencia([
        () => json(200, [materia('m1', 'Redes', 6, 2)]),
        () => json(500, {'mensagem': 'Erro interno.'}),
        () => json(200, [materia('m1', 'Redes', 5, 3)]),
      ]),
    });
    await tester.tap(find.text('Redes'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Estudar cartões'));
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(find.text('Erro interno.'), findsOneWidget);
    // Sem totais velhos na tela.
    expect(find.text('6 ativos'), findsNothing);

    await tester.tap(find.text('Tentar de novo'));
    await tester.pumpAndSettle();

    expect(find.text('Erro interno.'), findsNothing);
    expect(find.text('5 ativos'), findsOneWidget);
    expect(find.text('3 arquivados'), findsOneWidget);
  });

  testWidgets('matéria apagada em outro lugar: ao recarregar, volta para a '
      'lista', (tester) async {
    await abrirApp(tester, {
      'GET /api/materias': emSequencia([
        () => json(200, [materia('m1', 'Redes', 6, 2)]),
        () => json(200, []),
      ]),
    });
    await tester.tap(find.text('Redes'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Estudar cartões'));
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(find.text('Minhas matérias'), findsOneWidget);
    expect(find.text('Nenhuma matéria ainda'), findsOneWidget);
  });

  testWidgets('matéria apagada, mas o usuário já está saindo dela: a lista '
      'continua aberta', (tester) async {
    final recarga = Completer<http.Response>();
    await abrirApp(tester, {
      'GET /api/materias': emSequencia([
        () => json(200, [materia('m1', 'Redes', 6, 2)]),
        () => recarga.future,
        () => json(200, []),
      ]),
    });
    await tester.tap(find.text('Redes'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Estudar cartões'));
    await tester.pumpAndSettle();
    await tester.pageBack();
    // Com a barra de progresso girando, pumpAndSettle não termina: avança até a tela sair.
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.byType(TelaEstudo), findsNothing);

    // Volta para a lista enquanto a recarga da matéria ainda não chegou.
    await tester.pageBack();
    await tester.pump();
    recarga.complete(json(200, []));
    await tester.pumpAndSettle();

    expect(find.text('Minhas matérias'), findsOneWidget);
    expect(find.text('Nenhuma matéria ainda'), findsOneWidget);
  });

  testWidgets('sem arquivados, o Arquivo fica desabilitado', (tester) async {
    await abrirApp(tester, {
      'GET /api/materias': () => json(200, [materia('m1', 'Redes', 1, 0)]),
    });

    await tester.tap(find.text('Redes'));
    await tester.pumpAndSettle();

    expect(find.text('1 ativo'), findsOneWidget);
    expect(find.text('nenhum arquivado'), findsOneWidget);
    expect(
      tester.widget<ListTile>(find.widgetWithText(ListTile, 'Arquivo')).enabled,
      isFalse,
    );
    await tester.tap(find.text('Arquivo'));
    await tester.pumpAndSettle();
    expect(find.text('Em breve'), findsNothing);
  });

  testWidgets('a tela que fecha pode deixar um aviso no topo da matéria', (
    tester,
  ) async {
    await abrirApp(tester, {
      'GET /api/materias': emSequencia([
        () => json(200, [materia('m1', 'Redes', 0, 0)]),
        () => json(200, [materia('m1', 'Redes', 8, 0)]),
      ]),
    });
    await tester.tap(find.text('Redes'));
    await tester.pumpAndSettle();
    expect(find.text('nenhum ativo'), findsOneWidget);

    await tester.tap(find.text('Adicionar cartão'));
    await tester.pumpAndSettle();
    // Como o fluxo de adicionar cartão (#42) vai fazer ao salvar.
    tester
        .state<NavigatorState>(find.byType(Navigator))
        .pop('8 cartões salvos em Estudar cartões');
    await tester.pumpAndSettle();

    expect(find.text('8 cartões salvos em Estudar cartões'), findsOneWidget);
    expect(find.text('8 ativos'), findsOneWidget);

    // Voltar sem aviso tira o anterior.
    await tester.tap(find.text('Estudar cartões'));
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.text('8 cartões salvos em Estudar cartões'), findsNothing);
  });

  testWidgets('401 ao carregar a lista volta ao login, e a resposta que chega '
      'depois não mexe na tela que saiu', (tester) async {
    final materias = Completer<http.Response>();
    await abrirApp(tester, {
      'GET /api/usuarios/me': () => http.Response('', 401),
      'GET /api/materias': () => materias.future,
    });

    expect(find.byType(TelaLogin), findsOneWidget);
    expect(find.text('Sua sessão expirou. Entre de novo.'), findsOneWidget);

    // A lista já saiu da árvore quando a outra resposta chega.
    materias.complete(http.Response('', 401));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(TelaLogin), findsOneWidget);
  });

  testWidgets('401 ao criar a matéria fecha o diálogo e volta ao login', (
    tester,
  ) async {
    await abrirApp(tester, {
      'GET /api/materias': () => json(200, []),
      'POST /api/materias': () => http.Response('', 401),
    });

    await tester.tap(botao('Nova matéria'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Biologia');
    await tester.tap(botao('Criar'));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.byType(TelaLogin), findsOneWidget);
  });
}
