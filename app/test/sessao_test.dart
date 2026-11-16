import 'dart:async';
import 'dart:convert';

import 'package:flipo/main.dart';
import 'package:flipo/telas/autenticacao.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  const armazenamento = FlutterSecureStorage();
  late List<http.Request> requisicoes;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    requisicoes = [];
  });

  http.Response json(int status, Object corpo) => http.Response.bytes(
    utf8.encode(jsonEncode(corpo)),
    status,
    headers: {'content-type': 'application/json'},
  );

  /// Abre o app com um backend falso: [respostas] mapeia o caminho para a resposta.
  Future<void> abrirApp(
    WidgetTester tester,
    Map<String, http.Response Function()> respostas,
  ) async {
    await tester.pumpWidget(
      FlipoApp(
        httpClient: MockClient((requisicao) async {
          requisicoes.add(requisicao);
          return respostas[requisicao.url.path]!();
        }),
      ),
    );
    await tester.pumpAndSettle();
  }

  Map<String, dynamic> corpoDe(http.Request requisicao) =>
      jsonDecode(requisicao.body) as Map<String, dynamic>;

  final campoNome = find.widgetWithText(TextFormField, 'Nome');
  final campoEmail = find.widgetWithText(TextFormField, 'E-mail');
  final campoSenha = find.widgetWithText(TextFormField, 'Senha');
  final botaoEntrar = find.widgetWithText(FilledButton, 'Entrar');
  final botaoCriarConta = find.widgetWithText(FilledButton, 'Criar conta');
  final areaLogada = find.byType(TelaInicial);

  Future<void> preencherLogin(WidgetTester tester) async {
    await tester.enterText(campoEmail, ' ana@exemplo.com ');
    await tester.enterText(campoSenha, 'segredo123');
  }

  testWidgets('login certo guarda o token e entra', (tester) async {
    await abrirApp(tester, {
      '/api/auth/login': () => json(200, {'token': 'jwt-ana'}),
    });
    expect(find.byType(TelaLogin), findsOneWidget);

    await preencherLogin(tester);
    await tester.tap(botaoEntrar);
    await tester.pumpAndSettle();

    expect(requisicoes.single.method, 'POST');
    expect(corpoDe(requisicoes.single), {
      'email': 'ana@exemplo.com',
      'senha': 'segredo123',
    });
    expect(await armazenamento.read(key: 'flipo_token'), 'jwt-ana');
    expect(areaLogada, findsOneWidget);
  });

  testWidgets('"concluído" no teclado, na senha, também entra', (tester) async {
    await abrirApp(tester, {
      '/api/auth/login': () => json(200, {'token': 'jwt-ana'}),
    });

    await preencherLogin(tester);
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    expect(areaLogada, findsOneWidget);
  });

  testWidgets('credenciais erradas mostram a mensagem do backend e ficam no '
      'login', (tester) async {
    await abrirApp(tester, {
      '/api/auth/login': () =>
          json(401, {'mensagem': 'E-mail ou senha inválidos.'}),
    });

    await preencherLogin(tester);
    await tester.tap(botaoEntrar);
    await tester.pumpAndSettle();

    expect(find.text('E-mail ou senha inválidos.'), findsOneWidget);
    expect(find.byType(TelaLogin), findsOneWidget);
    expect(await armazenamento.read(key: 'flipo_token'), isNull);
  });

  testWidgets('campos vazios ou e-mail malformado não chegam ao backend', (
    tester,
  ) async {
    await abrirApp(tester, {});

    await tester.tap(botaoEntrar);
    await tester.pump();
    expect(find.text('Informe o e-mail.'), findsOneWidget);
    expect(find.text('Informe a senha.'), findsOneWidget);

    await tester.enterText(campoEmail, 'ana.exemplo.com');
    await tester.tap(botaoEntrar);
    await tester.pump();
    expect(find.text('E-mail inválido.'), findsOneWidget);
    expect(requisicoes, isEmpty);
  });

  testWidgets('enquanto envia, o botão carrega e não envia de novo', (
    tester,
  ) async {
    final resposta = Completer<http.Response>();
    await tester.pumpWidget(
      FlipoApp(
        httpClient: MockClient((requisicao) {
          requisicoes.add(requisicao);
          return resposta.future;
        }),
      ),
    );
    await tester.pumpAndSettle();

    await preencherLogin(tester);
    await tester.tap(botaoEntrar);
    await tester.pump();
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await tester.tap(find.byType(FilledButton));
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pump();
    expect(requisicoes, hasLength(1));

    resposta.complete(json(200, {'token': 'jwt-ana'}));
    await tester.pumpAndSettle();
    expect(areaLogada, findsOneWidget);
  });

  testWidgets('criar conta registra, entra e abre a área logada', (
    tester,
  ) async {
    await abrirApp(tester, {
      '/api/auth/registro': () =>
          json(201, {'id': 'u1', 'nome': 'Ana', 'email': 'ana@exemplo.com'}),
      '/api/auth/login': () => json(200, {'token': 'jwt-ana'}),
    });

    await tester.tap(find.widgetWithText(TextButton, 'Criar conta'));
    await tester.pumpAndSettle();
    await tester.enterText(campoNome, 'Ana');
    await preencherLogin(tester);
    await tester.tap(botaoCriarConta);
    await tester.pumpAndSettle();

    expect(requisicoes.map((r) => r.url.path), [
      '/api/auth/registro',
      '/api/auth/login',
    ]);
    expect(corpoDe(requisicoes.first), {
      'nome': 'Ana',
      'email': 'ana@exemplo.com',
      'senha': 'segredo123',
    });
    expect(corpoDe(requisicoes.last), {
      'email': 'ana@exemplo.com',
      'senha': 'segredo123',
    });
    expect(await armazenamento.read(key: 'flipo_token'), 'jwt-ana');
    expect(areaLogada, findsOneWidget);
    // Criar conta não fica na pilha: voltar não leva de novo ao cadastro.
    expect(find.byType(TelaCriarConta), findsNothing);
    expect(find.byType(BackButton), findsNothing);
  });

  testWidgets('e-mail já cadastrado mostra o erro e não entra', (tester) async {
    await abrirApp(tester, {
      '/api/auth/registro': () =>
          json(409, {'mensagem': 'E-mail já cadastrado.'}),
    });

    await tester.tap(find.widgetWithText(TextButton, 'Criar conta'));
    await tester.pumpAndSettle();
    await tester.enterText(campoNome, 'Ana');
    await preencherLogin(tester);
    await tester.tap(botaoCriarConta);
    await tester.pumpAndSettle();

    expect(find.text('E-mail já cadastrado.'), findsOneWidget);
    expect(find.byType(TelaCriarConta), findsOneWidget);
    expect(requisicoes.map((r) => r.url.path), ['/api/auth/registro']);
  });

  testWidgets('criar conta pede nome e senha com pelo menos 8 caracteres', (
    tester,
  ) async {
    await abrirApp(tester, {});

    await tester.tap(find.widgetWithText(TextButton, 'Criar conta'));
    await tester.pumpAndSettle();
    await tester.enterText(campoEmail, 'ana@exemplo.com');
    await tester.enterText(campoSenha, '1234567');
    await tester.tap(botaoCriarConta);
    await tester.pump();

    expect(find.text('Informe seu nome.'), findsOneWidget);
    expect(
      find.text('A senha precisa ter pelo menos 8 caracteres.'),
      findsOneWidget,
    );
    expect(requisicoes, isEmpty);
  });

  testWidgets('com token salvo, o app abre na área logada', (tester) async {
    FlutterSecureStorage.setMockInitialValues({'flipo_token': 'jwt-ana'});

    await abrirApp(tester, {});

    expect(areaLogada, findsOneWidget);
    expect(find.byType(TelaLogin), findsNothing);
  });

  testWidgets('"Sair" apaga o token e volta ao login', (tester) async {
    FlutterSecureStorage.setMockInitialValues({'flipo_token': 'jwt-ana'});
    await abrirApp(tester, {});

    await tester.tap(find.widgetWithText(TextButton, 'Sair'));
    await tester.pumpAndSettle();

    expect(find.byType(TelaLogin), findsOneWidget);
    expect(await armazenamento.read(key: 'flipo_token'), isNull);
  });

  testWidgets('sessão expirada (401) volta ao login, mesmo com telas abertas '
      'por cima', (tester) async {
    FlutterSecureStorage.setMockInitialValues({'flipo_token': 'jwt-vencido'});
    await abrirApp(tester, {'/api/materias': () => http.Response('', 401)});
    final api = tester.widget<TelaInicial>(areaLogada).api;
    // Uma tela por cima da área logada, como a matéria aberta a partir da lista.
    tester
        .state<NavigatorState>(find.byType(Navigator))
        .push(
          MaterialPageRoute<void>(
            builder: (_) => const Scaffold(body: Text('Matéria')),
          ),
        );
    await tester.pumpAndSettle();
    expect(find.text('Matéria'), findsOneWidget);

    await expectLater(api.listarMaterias(), throwsException);
    await tester.pumpAndSettle();

    expect(find.byType(TelaLogin), findsOneWidget);
    expect(find.text('Matéria'), findsNothing);
    expect(find.byType(BackButton), findsNothing);
  });
}
