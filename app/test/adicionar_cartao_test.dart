import 'dart:async';
import 'dart:convert';

import 'package:flipo/api/api_client.dart';
import 'package:flipo/main.dart';
import 'package:flipo/telas/autenticacao.dart';
import 'package:flipo/telas/materias.dart';
import 'package:flipo/telas/revisao.dart';
import 'package:flipo/theme.dart';
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

  /// O cartão criado, como o backend devolve para o `POST .../cartoes`.
  http.Response criado(http.Request requisicao) {
    final corpo = jsonDecode(requisicao.body) as Map<String, dynamic>;
    return json(201, {
      'id': 'c${requisicoes.length}',
      ...corpo,
      'origem': 'manual',
      'arquivado': false,
    });
  }

  List<Object?> corposDosPosts() => [
    for (final r in requisicoes)
      if (r.method == 'POST') jsonDecode(r.body),
  ];

  // `FilledButton.icon` cria uma subclasse, que `find.byType` não acha.
  Finder botao(String rotulo) => find.ancestor(
    of: find.text(rotulo),
    matching: find.byWidgetPredicate((w) => w is FilledButton),
  );

  bool habilitado(WidgetTester tester, Finder f) =>
      tester.widget<ButtonStyleButton>(f).onPressed != null;

  /// A ação [rotulo] (Aceitar, Editar, Descartar) do cartão da Revisão com a [pergunta].
  Finder acao(String pergunta, String rotulo) => find.descendant(
    of: find.ancestor(of: find.text(pergunta), matching: find.byType(Card)),
    matching: find.ancestor(
      of: find.text(rotulo),
      matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
    ),
  );

  Finder estadoDe(String pergunta, String estado) => find.descendant(
    of: find.ancestor(of: find.text(pergunta), matching: find.byType(Card)),
    matching: find.text(estado),
  );

  final campoPergunta = find.widgetWithText(TextFormField, 'Pergunta');
  final campoResposta = find.widgetWithText(TextFormField, 'Resposta');

  group('pelo app', () {
    /// Abre o app logado, com a matéria Redes, e entra em "Adicionar cartão". [respostas] mapeia
    /// "MÉTODO caminho" para a resposta do backend falso.
    Future<void> abrirAdicionar(
      WidgetTester tester,
      Map<String, FutureOr<http.Response> Function(http.Request)> respostas,
    ) async {
      final todas = <String, FutureOr<http.Response> Function(http.Request)>{
        'GET /api/usuarios/me': (_) =>
            json(200, {'id': 'u1', 'nome': 'Ana', 'email': 'ana@exemplo.com'}),
        ...respostas,
      };
      await tester.pumpWidget(
        FlipoApp(
          httpClient: MockClient((requisicao) async {
            requisicoes.add(requisicao);
            return todas['${requisicao.method} ${requisicao.url.path}']!(
              requisicao,
            );
          }),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Redes'));
      await tester.pumpAndSettle();
      await tester.tap(botao('Adicionar cartão'));
      await tester.pumpAndSettle();
    }

    Map<String, Object> redes(int ativos) => {
      'id': 'm1',
      'nome': 'Redes',
      'totalAtivos': ativos,
      'totalArquivados': 0,
    };

    /// `GET /api/materias`: antes de salvar, sem cartões; nas recargas seguintes, com um.
    FutureOr<http.Response> Function(http.Request) listagem() {
      var chamadas = 0;
      return (_) => json(200, [redes(chamadas++ == 0 ? 0 : 1)]);
    }

    Future<void> irParaRevisao(WidgetTester tester) async {
      await tester.tap(find.text('Criar manualmente'));
      await tester.pumpAndSettle();
      await tester.enterText(campoPergunta, '  O que é TCP? ');
      await tester.enterText(campoResposta, ' Um protocolo confiável. ');
      await tester.tap(botao('Ir para revisão'));
      await tester.pumpAndSettle();
    }

    testWidgets('mostra os dois caminhos, com Gerar com IA desabilitado', (
      tester,
    ) async {
      await abrirAdicionar(tester, {'GET /api/materias': listagem()});

      expect(find.text('Como você quer criar?'), findsOneWidget);
      expect(find.text('Digite a pergunta e a resposta'), findsOneWidget);
      expect(
        find.text('A partir de um texto colado ou de um PDF'),
        findsOneWidget,
      );
      expect(find.text('em breve'), findsOneWidget);
      expect(
        find.text(
          'Nos dois caminhos você revisa tudo antes de salvar — nenhum '
          'cartão entra na matéria sem passar pela revisão.',
        ),
        findsOneWidget,
      );
      ListTile caminho(String titulo) =>
          tester.widget<ListTile>(find.widgetWithText(ListTile, titulo));
      expect(caminho('Criar manualmente').enabled, isTrue);
      expect(caminho('Gerar com IA').enabled, isFalse);

      await tester.tap(find.text('Gerar com IA'));
      await tester.pumpAndSettle();
      expect(find.text('Como você quer criar?'), findsOneWidget);
    });

    testWidgets('o formulário manual valida e leva à Revisão com o cartão já '
        'aceito, sem salvar nada', (tester) async {
      await abrirAdicionar(tester, {'GET /api/materias': listagem()});
      await tester.tap(find.text('Criar manualmente'));
      await tester.pumpAndSettle();
      expect(find.text('Novo cartão'), findsOneWidget);
      expect(find.text('Criação manual'), findsOneWidget);
      expect(
        find.text('Você confere o cartão na revisão antes de salvar.'),
        findsOneWidget,
      );

      await tester.tap(botao('Ir para revisão'));
      await tester.pump();
      expect(find.text('Informe a pergunta.'), findsOneWidget);
      expect(find.text('Informe a resposta.'), findsOneWidget);

      // Só espaços não conta.
      await tester.enterText(campoPergunta, '   ');
      await tester.enterText(campoResposta, 'Um protocolo confiável.');
      await tester.tap(botao('Ir para revisão'));
      await tester.pump();
      expect(find.text('Informe a pergunta.'), findsOneWidget);
      expect(find.text('Informe a resposta.'), findsNothing);
      expect(find.text('Revisar cartão (1)'), findsNothing);

      await tester.enterText(campoPergunta, '  O que é TCP? ');
      await tester.tap(botao('Ir para revisão'));
      await tester.pumpAndSettle();

      expect(find.text('Revisar cartão (1)'), findsOneWidget);
      expect(find.text('Nada foi salvo ainda'), findsOneWidget);
      expect(find.text('O que é TCP?'), findsOneWidget);
      expect(find.text('Um protocolo confiável.'), findsOneWidget);
      expect(find.text('manual'), findsOneWidget);
      expect(estadoDe('O que é TCP?', 'aceito'), findsOneWidget);
      expect(find.text('1 aceito'), findsOneWidget);
      final confirmar = botao('Confirmar revisão (1 cartão)');
      expect(habilitado(tester, confirmar), isTrue);
      expect(corposDosPosts(), isEmpty);

      // Voltar pede confirmação (o cartão aceito se perderia), e o formulário continua
      // preenchido.
      await tester.pageBack();
      await tester.pumpAndSettle();
      await tester.tap(find.text('Sair'));
      await tester.pumpAndSettle();
      expect(find.text('Novo cartão'), findsOneWidget);
      expect(find.text('  O que é TCP? '), findsOneWidget);
      expect(corposDosPosts(), isEmpty);
    });

    testWidgets('confirmar salva o aceito, volta para a matéria e mostra o '
        'aviso no singular', (tester) async {
      await abrirAdicionar(tester, {
        'GET /api/materias': listagem(),
        'POST /api/materias/m1/cartoes': criado,
      });
      await irParaRevisao(tester);

      // Sem tocar em Aceitar: o cartão manual já chega aceito.
      await tester.tap(botao('Confirmar revisão (1 cartão)'));
      await tester.pumpAndSettle();

      expect(corposDosPosts(), [
        {'pergunta': 'O que é TCP?', 'resposta': 'Um protocolo confiável.'},
      ]);
      expect(find.byType(TelaMateria), findsOneWidget);
      expect(find.text('Como você quer criar?'), findsNothing);
      expect(find.text('1 cartão salvo em Estudar cartões'), findsOneWidget);
      // A matéria recarregou os totais.
      expect(find.text('1 ativo'), findsOneWidget);
    });

    testWidgets('401 ao salvar volta ao login sem erro', (tester) async {
      await abrirAdicionar(tester, {
        'GET /api/materias': listagem(),
        'POST /api/materias/m1/cartoes': (_) => http.Response('', 401),
      });
      await irParaRevisao(tester);
      await tester.tap(botao('Confirmar revisão (1 cartão)'));
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(find.byType(TelaLogin), findsOneWidget);
      expect(find.text('Sua sessão expirou. Entre de novo.'), findsOneWidget);
    });

    testWidgets('401 que chega depois de a Revisão sair não mexe nela', (
      tester,
    ) async {
      final resposta = Completer<http.Response>();
      await abrirAdicionar(tester, {
        'GET /api/materias': listagem(),
        'POST /api/materias/m1/cartoes': (_) => resposta.future,
      });
      await irParaRevisao(tester);
      await tester.tap(botao('Confirmar revisão (1 cartão)'));
      await tester.pump();

      // Outra requisição expirou a sessão enquanto o salvamento estava no ar.
      tester
          .state<NavigatorState>(find.byType(Navigator))
          .popUntil((rota) => rota.isFirst);
      await const FlutterSecureStorage().delete(key: 'flipo_token');
      await tester.pumpAndSettle();
      resposta.complete(http.Response('', 401));
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(find.byType(TelaMinhasMaterias), findsOneWidget);
    });
  });

  group('a Revisão', () {
    // O que cada chamada de `salvar` recebeu: (pergunta, resposta, origem) de cada aceito.
    late List<List<(String, String, String)>> recebidos;
    // Se não for null, `salvar` lança isto.
    Object? falha;
    String? aviso;
    late bool fechou;

    setUp(() {
      recebidos = [];
      falha = null;
      aviso = null;
      fechou = false;
    });

    Future<void> salvarFalso(List<SugestaoDeCartao> aceitos) async {
      recebidos.add([
        for (final c in aceitos) (c.pergunta, c.resposta, c.origem),
      ]);
      if (falha case final erro?) throw erro;
    }

    SugestaoDeCartao sugestao(String n, {String origem = 'ia'}) =>
        SugestaoDeCartao(
          pergunta: 'Pergunta $n',
          resposta: 'Resposta $n',
          origem: origem,
        );

    /// Abre a Revisão por cima de uma tela vazia, que guarda o que ela devolve ao fechar.
    Future<void> abrirRevisao(
      WidgetTester tester,
      List<SugestaoDeCartao> sugestoes,
    ) async {
      // Alta o bastante para todos os cartões estarem na tela (a lista só monta os visíveis).
      tester.view.physicalSize = const Size(480, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          theme: temaFlipo,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  aviso = await Navigator.of(context).push<String>(
                    MaterialPageRoute(
                      builder: (_) => TelaRevisao(
                        sugestoes: sugestoes,
                        salvar: salvarFalso,
                      ),
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

    Future<void> tocar(
      WidgetTester tester,
      String pergunta,
      String rotulo,
    ) async {
      await tester.tap(acao(pergunta, rotulo));
      await tester.pumpAndSettle();
    }

    testWidgets('aceitar, descartar e editar mudam o estado, o resumo e o '
        'botão', (tester) async {
      final semantica = tester.ensureSemantics();
      await abrirRevisao(tester, [
        sugestao('1'),
        sugestao('2'),
        sugestao('3', origem: 'manual'),
      ]);
      expect(find.text('Revisar sugestões (3)'), findsOneWidget);
      expect(
        find.text('Nada foi salvo ainda — aceite, edite ou descarte'),
        findsOneWidget,
      );
      expect(find.text('IA'), findsNWidgets(2));
      expect(find.text('manual'), findsOneWidget);
      expect(find.text('3 pendentes'), findsOneWidget);
      expect(
        habilitado(tester, botao('Confirmar revisão (0 cartões)')),
        isFalse,
      );

      await tocar(tester, 'Pergunta 1', 'Aceitar');
      expect(estadoDe('Pergunta 1', 'aceito'), findsOneWidget);
      expect(habilitado(tester, acao('Pergunta 1', 'Aceitar')), isFalse);
      // Desabilitado, mas preenchido e anunciado como selecionado, não acinzentado.
      Color? fundo(String pergunta) => tester
          .widget<ButtonStyleButton>(acao(pergunta, 'Aceitar'))
          .style
          ?.backgroundColor
          ?.resolve({WidgetState.disabled});
      expect(fundo('Pergunta 1'), temaFlipo.colorScheme.primary);
      expect(fundo('Pergunta 2'), isNull);
      expect(
        tester.getSemantics(acao('Pergunta 1', 'Aceitar')),
        isSemantics(label: 'Aceitar', isButton: true, isSelected: true),
      );
      expect(
        tester.getSemantics(acao('Pergunta 2', 'Aceitar')),
        isSemantics(label: 'Aceitar', isButton: true, isSelected: false),
      );
      expect(find.text('1 aceito · 2 pendentes'), findsOneWidget);
      expect(habilitado(tester, botao('Confirmar revisão (1 cartão)')), isTrue);

      await tocar(tester, 'Pergunta 2', 'Descartar');
      expect(estadoDe('Pergunta 2', 'descartado'), findsOneWidget);
      expect(habilitado(tester, acao('Pergunta 2', 'Descartar')), isFalse);
      expect(find.text('1 aceito · 1 descartado · 1 pendente'), findsOneWidget);

      // Editar abre o mesmo formulário, já preenchido, com a mesma validação.
      await tocar(tester, 'Pergunta 3', 'Editar');
      expect(find.text('Editar cartão'), findsOneWidget);
      expect(find.text('Pergunta 3'), findsOneWidget);
      expect(find.text('Resposta 3'), findsOneWidget);
      await tester.enterText(campoPergunta, ' ');
      await tester.tap(botao('Salvar'));
      await tester.pump();
      expect(find.text('Informe a pergunta.'), findsOneWidget);
      await tester.enterText(campoPergunta, ' Pergunta 3, revista ');
      await tester.tap(botao('Salvar'));
      await tester.pumpAndSettle();

      expect(find.text('Pergunta 3'), findsNothing);
      expect(
        estadoDe('Pergunta 3, revista', 'editado e aceito'),
        findsOneWidget,
      );
      expect(find.text('2 aceitos · 1 descartado'), findsOneWidget);
      expect(
        habilitado(tester, botao('Confirmar revisão (2 cartões)')),
        isTrue,
      );

      // Descartar de novo um aceito tira da conta.
      await tocar(tester, 'Pergunta 1', 'Descartar');
      expect(find.text('1 aceito · 2 descartados'), findsOneWidget);
      expect(find.text('Confirmar revisão (1 cartão)'), findsOneWidget);
      expect(recebidos, isEmpty);
      semantica.dispose();
    });

    testWidgets('cancelar a edição não muda nada, e editar um descartado o '
        'aceita', (tester) async {
      await abrirRevisao(tester, [sugestao('1')]);
      await tocar(tester, 'Pergunta 1', 'Descartar');

      await tocar(tester, 'Pergunta 1', 'Editar');
      await tester.enterText(campoPergunta, 'Outra pergunta');
      await tester.pageBack();
      await tester.pumpAndSettle();

      expect(estadoDe('Pergunta 1', 'descartado'), findsOneWidget);
      expect(find.text('Outra pergunta'), findsNothing);
      expect(find.text('1 descartado'), findsOneWidget);

      await tocar(tester, 'Pergunta 1', 'Editar');
      await tester.enterText(campoPergunta, 'Outra pergunta');
      await tester.tap(botao('Salvar'));
      await tester.pumpAndSettle();

      expect(estadoDe('Outra pergunta', 'editado e aceito'), findsOneWidget);
      expect(find.text('1 aceito'), findsOneWidget);
      expect(habilitado(tester, botao('Confirmar revisão (1 cartão)')), isTrue);
    });

    testWidgets('confirmar salva só os aceitos, em ordem, e devolve o aviso no '
        'plural', (tester) async {
      await abrirRevisao(tester, [
        sugestao('1'),
        sugestao('2'),
        sugestao('3'),
        sugestao('4', origem: 'manual'),
      ]);
      await tocar(tester, 'Pergunta 1', 'Aceitar');
      await tocar(tester, 'Pergunta 2', 'Descartar');
      // A 3 fica pendente.
      await tocar(tester, 'Pergunta 4', 'Editar');
      await tester.enterText(campoResposta, 'Resposta 4, revista');
      await tester.tap(botao('Salvar'));
      await tester.pumpAndSettle();

      await tester.tap(botao('Confirmar revisão (2 cartões)'));
      await tester.pumpAndSettle();

      // Uma chamada só, com os aceitos; a edição mantém a origem.
      expect(recebidos, [
        [
          ('Pergunta 1', 'Resposta 1', 'ia'),
          ('Pergunta 4', 'Resposta 4, revista', 'manual'),
        ],
      ]);
      expect(fechou, isTrue);
      expect(aviso, '2 cartões salvos em Estudar cartões');
    });

    testWidgets('falha ao salvar mostra o erro, deixa tudo aceito e tentar de '
        'novo envia os mesmos', (tester) async {
      falha = ApiException(500, 'Erro interno.');
      await abrirRevisao(tester, [sugestao('1'), sugestao('2'), sugestao('3')]);
      await tocar(tester, 'Pergunta 1', 'Aceitar');
      await tocar(tester, 'Pergunta 3', 'Aceitar');

      await tester.tap(botao('Confirmar revisão (2 cartões)'));
      await tester.pumpAndSettle();

      expect(fechou, isFalse);
      expect(find.text('Erro interno.'), findsOneWidget);
      expect(find.text('Revisar sugestões (3)'), findsOneWidget);
      expect(estadoDe('Pergunta 1', 'aceito'), findsOneWidget);
      expect(estadoDe('Pergunta 2', 'pendente'), findsOneWidget);
      expect(estadoDe('Pergunta 3', 'aceito'), findsOneWidget);
      expect(
        habilitado(tester, botao('Confirmar revisão (2 cartões)')),
        isTrue,
      );

      falha = null;
      await tester.tap(botao('Confirmar revisão (2 cartões)'));
      await tester.pumpAndSettle();

      final aceitos = [
        ('Pergunta 1', 'Resposta 1', 'ia'),
        ('Pergunta 3', 'Resposta 3', 'ia'),
      ];
      expect(recebidos, [aceitos, aceitos]);
      expect(fechou, isTrue);
      expect(aviso, '2 cartões salvos em Estudar cartões');
    });

    testWidgets('sair com aceitos não salvos pede confirmação', (tester) async {
      await abrirRevisao(tester, [sugestao('1'), sugestao('2')]);
      await tocar(tester, 'Pergunta 1', 'Aceitar');

      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.text('Sair sem salvar?'), findsOneWidget);
      await tester.tap(find.text('Continuar revisando'));
      await tester.pumpAndSettle();
      expect(fechou, isFalse);
      expect(estadoDe('Pergunta 1', 'aceito'), findsOneWidget);

      await tester.pageBack();
      await tester.pumpAndSettle();
      await tester.tap(find.text('Sair'));
      await tester.pumpAndSettle();
      expect(fechou, isTrue);
      expect(aviso, isNull);
      expect(recebidos, isEmpty);
    });

    testWidgets('sair sem aceitos não pergunta', (tester) async {
      await abrirRevisao(tester, [sugestao('1'), sugestao('2')]);
      await tocar(tester, 'Pergunta 2', 'Descartar');

      await tester.pageBack();
      await tester.pumpAndSettle();

      expect(find.byType(AlertDialog), findsNothing);
      expect(fechou, isTrue);
      expect(aviso, isNull);
    });
  });
}
