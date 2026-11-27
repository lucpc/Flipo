import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flipo/api/api_client.dart';
import 'package:flipo/main.dart';
import 'package:flipo/telas/autenticacao.dart';
import 'package:flipo/telas/gerar_ia.dart';
import 'package:flipo/telas/materias.dart';
import 'package:flipo/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

typedef Resposta = FutureOr<http.Response> Function(http.Request);

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

  /// O que o backend devolve para o `gerar-ia`: três sugestões.
  http.Response sugestoes(http.Request _) => json(200, [
    for (final n in [1, 2, 3])
      {'pergunta': 'Pergunta $n', 'resposta': 'Resposta $n'},
  ]);

  /// Os cartões gravados, como o backend devolve para o `lote`.
  http.Response gravados(http.Request requisicao) {
    final corpo = jsonDecode(requisicao.body) as Map<String, dynamic>;
    final cartoes = corpo['cartoes'] as List<dynamic>;
    return json(201, [
      for (final (i, c) in cartoes.indexed)
        {
          'id': 'c$i',
          ...c as Map<String, dynamic>,
          'origem': 'ia',
          'arquivado': false,
        },
    ]);
  }

  Map<String, Object> redes(int ativos) => {
    'id': 'm1',
    'nome': 'Redes',
    'totalAtivos': ativos,
    'totalArquivados': 0,
  };

  /// `GET /api/materias`: antes de salvar, sem cartões; nas recargas seguintes, com dois.
  Resposta listagem() {
    var chamadas = 0;
    return (_) => json(200, [redes(chamadas++ == 0 ? 0 : 2)]);
  }

  const gerarIa = 'POST /api/materias/m1/cartoes/gerar-ia';
  const lote = 'POST /api/materias/m1/cartoes/lote';

  List<Object?> corposDe(String chamada) => [
    for (final r in requisicoes)
      if ('${r.method} ${r.url.path}' == chamada) jsonDecode(r.body),
  ];

  // `FilledButton.icon` cria uma subclasse, que `find.byType` não acha.
  Finder botao(String rotulo) => find.ancestor(
    of: find.text(rotulo),
    matching: find.byWidgetPredicate((w) => w is FilledButton),
  );

  bool habilitado(WidgetTester tester, Finder f) =>
      tester.widget<ButtonStyleButton>(f).onPressed != null;

  bool iconeHabilitado(WidgetTester tester, String tooltip) =>
      tester
          .widget<IconButton>(
            find.ancestor(
              of: find.byTooltip(tooltip),
              matching: find.byType(IconButton),
            ),
          )
          .onPressed !=
      null;

  final campoTexto = find.widgetWithText(TextField, 'Texto');

  final anexar = find.ancestor(
    of: find.text('Anexar PDF'),
    matching: find.byWidgetPredicate((w) => w is OutlinedButton),
  );

  String textoDoCampo(WidgetTester tester) =>
      tester.widget<TextField>(campoTexto).controller!.text;

  NivelDeCartao nivelEscolhido(WidgetTester tester) => tester
      .widget<SegmentedButton<NivelDeCartao>>(
        find.byType(SegmentedButton<NivelDeCartao>),
      )
      .selected
      .single;

  /// O número entre − e + do seletor de quantidade.
  String quantidade(WidgetTester tester) => tester
      .widget<Text>(
        find.descendant(
          of: find.ancestor(
            of: find.byTooltip('Menos cartões'),
            matching: find.byType(Row),
          ),
          matching: find.byWidgetPredicate(
            (w) => w is Text && int.tryParse(w.data ?? '') != null,
          ),
        ),
      )
      .data!;

  Future<void> tocarVezes(WidgetTester tester, String tooltip, int n) async {
    for (var i = 0; i < n; i++) {
      await tester.tap(find.byTooltip(tooltip));
      await tester.pump();
    }
  }

  /// Abre o app logado, num celular de 390×844, entra na matéria Redes e em "Gerar com IA".
  /// [respostas] mapeia "MÉTODO caminho" para a resposta do backend falso.
  Future<void> abrirGerarIa(
    WidgetTester tester,
    Map<String, Resposta> respostas,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final todas = <String, Resposta>{
      'GET /api/usuarios/me': (_) =>
          json(200, {'id': 'u1', 'nome': 'Ana', 'email': 'ana@exemplo.com'}),
      'GET /api/materias': listagem(),
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
    await tester.tap(find.text('Gerar com IA'));
    await tester.pumpAndSettle();
  }

  /// Preenche o formulário com um texto, 12 cartões e o nível Aplicação.
  Future<void> preencher(WidgetTester tester) async {
    await tester.enterText(campoTexto, '  Texto sobre TCP.  ');
    await tocarVezes(tester, 'Mais cartões', 2);
    await tester.tap(find.text('Aplicação'));
    await tester.pumpAndSettle();
  }

  void esperarFormularioPreenchido(WidgetTester tester) {
    expect(find.text('Gerar com IA'), findsOneWidget);
    expect(textoDoCampo(tester), '  Texto sobre TCP.  ');
    expect(quantidade(tester), '12');
    expect(nivelEscolhido(tester), NivelDeCartao.aplicacao);
    expect(habilitado(tester, botao('Gerar sugestões')), isTrue);
  }

  const progresso =
      'A IA está lendo o seu texto. Nada será salvo até você revisar cada '
      'cartão.';

  testWidgets('abre com os padrões e só habilita gerar com texto', (
    tester,
  ) async {
    await abrirGerarIa(tester, {});

    expect(
      find.text('A partir de um texto colado ou de um PDF'),
      findsOneWidget,
    );
    expect(find.text('0 / 20.000'), findsOneWidget);
    expect(quantidade(tester), '10');
    expect(nivelEscolhido(tester), NivelDeCartao.definicao);
    expect(habilitado(tester, botao('Gerar sugestões')), isFalse);
    expect(habilitado(tester, anexar), isTrue);

    await tester.enterText(campoTexto, '  \n ');
    await tester.pump();
    expect(habilitado(tester, botao('Gerar sugestões')), isFalse);

    await tester.enterText(campoTexto, 'TCP');
    await tester.pump();
    expect(find.text('3 / 20.000'), findsOneWidget);
    expect(habilitado(tester, botao('Gerar sugestões')), isTrue);
    expect(requisicoes.where((r) => r.method == 'POST'), isEmpty);
  });

  testWidgets('a quantidade vai de 1 a 20', (tester) async {
    await abrirGerarIa(tester, {});

    await tocarVezes(tester, 'Menos cartões', 9);
    expect(quantidade(tester), '1');
    expect(iconeHabilitado(tester, 'Menos cartões'), isFalse);
    expect(iconeHabilitado(tester, 'Mais cartões'), isTrue);

    await tocarVezes(tester, 'Mais cartões', 19);
    expect(quantidade(tester), '20');
    expect(iconeHabilitado(tester, 'Mais cartões'), isFalse);
    expect(iconeHabilitado(tester, 'Menos cartões'), isTrue);
  });

  testWidgets('o texto para em 20 000 unidades UTF-16', (tester) async {
    await abrirGerarIa(tester, {});

    await tester.enterText(campoTexto, 'a' * (limiteDoTexto + 1));
    await tester.pump();
    expect(textoDoCampo(tester).length, limiteDoTexto);
    expect(find.text('20.000 / 20.000'), findsOneWidget);

    // Um emoji conta 2, como no backend, e não é partido ao meio.
    await tester.enterText(campoTexto, '${'a' * (limiteDoTexto - 1)}😀');
    await tester.pump();
    expect(textoDoCampo(tester), 'a' * (limiteDoTexto - 1));

    // Uma bandeira (dois indicadores regionais, 4 unidades) também não é partida: sem espaço
    // para ela inteira, sai inteira.
    await tester.enterText(campoTexto, '${'a' * (limiteDoTexto - 3)}🇧🇷');
    await tester.pump();
    expect(textoDoCampo(tester), 'a' * (limiteDoTexto - 3));

    // Colado no meio: o que excede sai do fim do trecho colado, e o resto fica.
    final antes = 'b' * (limiteDoTexto - 5);
    await tester.enterText(campoTexto, antes);
    tester.testTextInput.updateEditingValue(
      TextEditingValue(
        text: '${antes.substring(0, 10)}XXXXXXXXXX${antes.substring(10)}',
        selection: const TextSelection.collapsed(offset: 20),
      ),
    );
    await tester.pump();
    expect(
      textoDoCampo(tester),
      '${antes.substring(0, 10)}XXXXX${antes.substring(10)}',
    );

    // Cheio, o que for digitado não entra.
    tester.testTextInput.updateEditingValue(
      TextEditingValue(
        text: 'Y${textoDoCampo(tester)}',
        selection: const TextSelection.collapsed(offset: 1),
      ),
    );
    await tester.pump();
    expect(textoDoCampo(tester).length, limiteDoTexto);
    expect(textoDoCampo(tester), startsWith('bbbbbbbbbbXXXXX'));
  });

  testWidgets('no limite, a composição do teclado (IME) é mantida, recortada', (
    tester,
  ) async {
    await abrirGerarIa(tester, {});
    final antes = 'a' * (limiteDoTexto - 2);
    await tester.enterText(campoTexto, antes);
    await tester.pump();

    // O teclado compõe "かんじ" no fim; só cabem 2 unidades.
    tester.testTextInput.updateEditingValue(
      TextEditingValue(
        text: '$antesかんじ',
        selection: TextSelection.collapsed(offset: antes.length + 3),
        composing: TextRange(start: antes.length, end: antes.length + 3),
      ),
    );
    await tester.pump();

    final campo = tester.widget<EditableText>(find.byType(EditableText));
    expect(campo.controller.text, '$antesかん');
    expect(
      campo.controller.value.composing,
      TextRange(start: antes.length, end: limiteDoTexto),
    );
  });

  testWidgets('gera com os valores escolhidos, mostra o progresso e abre a '
      'Revisão com as sugestões pendentes, sem salvar nada', (tester) async {
    final resposta = Completer<http.Response>();
    await abrirGerarIa(tester, {gerarIa: (_) => resposta.future});
    await preencher(tester);

    await tester.tap(botao('Gerar sugestões'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Gerando sugestões'), findsOneWidget);
    expect(find.text(progresso), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    // Texto sem os espaços das pontas; quantidade inteira e nível na forma canônica.
    expect(corposDe(gerarIa), [
      {'texto': 'Texto sobre TCP.', 'quantidade': 12, 'nivel': 'aplicacao'},
    ]);

    resposta.complete(sugestoes(requisicoes.last));
    await tester.pumpAndSettle();

    expect(find.text('Revisar sugestões (3)'), findsOneWidget);
    expect(
      find.text('Nada foi salvo ainda — aceite, edite ou descarte'),
      findsOneWidget,
    );
    expect(find.text('IA'), findsNWidgets(3));
    expect(find.text('3 pendentes'), findsOneWidget);
    expect(find.text('Pergunta 1'), findsOneWidget);
    expect(find.text('Resposta 3'), findsOneWidget);
    expect(corposDe(lote), isEmpty);

    // Voltar da Revisão sem aceitar nada devolve o formulário como estava.
    await tester.pageBack();
    await tester.pumpAndSettle();
    esperarFormularioPreenchido(tester);
    expect(corposDe(lote), isEmpty);
  });

  testWidgets('dois toques seguidos em gerar fazem uma geração só', (
    tester,
  ) async {
    await abrirGerarIa(tester, {gerarIa: sugestoes});
    await preencher(tester);

    await tester.tap(botao('Gerar sugestões'));
    await tester.tap(botao('Gerar sugestões'), warnIfMissed: false);
    await tester.pumpAndSettle();

    expect(corposDe(gerarIa), hasLength(1));
  });

  testWidgets('sem mexer, manda 10 cartões no nível definição', (tester) async {
    await abrirGerarIa(tester, {gerarIa: sugestoes});
    await tester.enterText(campoTexto, 'Texto sobre UDP.');
    await tester.pump();

    await tester.tap(botao('Gerar sugestões'));
    await tester.pumpAndSettle();

    expect(corposDe(gerarIa), [
      {'texto': 'Texto sobre UDP.', 'quantidade': 10, 'nivel': 'definicao'},
    ]);
    expect(find.text('Revisar sugestões (3)'), findsOneWidget);
  });

  testWidgets('confirmar grava pelo lote só os aceitos, volta para a matéria '
      'e mostra o aviso', (tester) async {
    await abrirGerarIa(tester, {gerarIa: sugestoes, lote: gravados});
    await preencher(tester);
    await tester.tap(botao('Gerar sugestões'));
    await tester.pumpAndSettle();

    /// Toca a ação [rotulo] do cartão da Revisão com a [pergunta], rolando até ela.
    Future<void> tocar(String pergunta, String rotulo) async {
      final acao = find.descendant(
        of: find.ancestor(of: find.text(pergunta), matching: find.byType(Card)),
        matching: find.ancestor(
          of: find.text(rotulo),
          matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
        ),
      );
      await tester.ensureVisible(acao);
      await tester.pumpAndSettle();
      await tester.tap(acao);
      await tester.pumpAndSettle();
    }

    await tocar('Pergunta 1', 'Aceitar');
    await tocar('Pergunta 2', 'Descartar');
    await tocar('Pergunta 3', 'Editar');
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Resposta'),
      ' Resposta 3, revista ',
    );
    await tester.tap(botao('Salvar'));
    await tester.pumpAndSettle();

    await tester.tap(botao('Confirmar revisão (2 cartões)'));
    await tester.pumpAndSettle();

    // Uma geração e um lote, só com os aceitos, na ordem da tela e sem `origem`.
    expect(corposDe(gerarIa), hasLength(1));
    expect(corposDe(lote), [
      {
        'cartoes': [
          {'pergunta': 'Pergunta 1', 'resposta': 'Resposta 1'},
          {'pergunta': 'Pergunta 3', 'resposta': 'Resposta 3, revista'},
        ],
      },
    ]);
    expect(find.byType(TelaMateria), findsOneWidget);
    expect(find.text('Como você quer criar?'), findsNothing);
    expect(find.text('2 cartões salvos em Estudar cartões'), findsOneWidget);
    expect(find.text('2 ativos'), findsOneWidget);
  });

  for (final (status, mensagem) in [
    (502, 'A IA não conseguiu gerar os cartões desta vez. Tente de novo.'),
    (504, 'A IA não conseguiu gerar os cartões desta vez. Tente de novo.'),
    (503, 'A geração por IA não está disponível no servidor no momento.'),
    (404, 'Matéria não encontrada. Ela pode ter sido apagada.'),
  ]) {
    testWidgets('$status volta ao formulário preenchido, com a mensagem', (
      tester,
    ) async {
      await abrirGerarIa(tester, {
        // O corpo do backend é genérico; a tela usa o próprio texto.
        gerarIa: (_) => json(status, {'mensagem': 'Mensagem do servidor.'}),
      });
      await preencher(tester);

      await tester.tap(botao('Gerar sugestões'));
      await tester.pumpAndSettle();

      esperarFormularioPreenchido(tester);
      expect(find.text(mensagem), findsOneWidget);
      expect(find.text('Mensagem do servidor.'), findsNothing);
      expect(find.text(progresso), findsNothing);
      expect(corposDe(lote), isEmpty);
    });
  }

  testWidgets('sem conexão, mostra o erro de rede; tentar de novo gera e '
      'limpa o erro', (tester) async {
    var tentativas = 0;
    await abrirGerarIa(tester, {
      gerarIa: (requisicao) => tentativas++ == 0
          ? throw http.ClientException('sem rede')
          : sugestoes(requisicao),
    });
    await preencher(tester);

    await tester.tap(botao('Gerar sugestões'));
    await tester.pumpAndSettle();
    esperarFormularioPreenchido(tester);
    expect(find.text('Não foi possível conectar ao servidor.'), findsOneWidget);

    await tester.tap(botao('Gerar sugestões'));
    await tester.pumpAndSettle();
    expect(find.text('Revisar sugestões (3)'), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    esperarFormularioPreenchido(tester);
    expect(find.text('Não foi possível conectar ao servidor.'), findsNothing);
  });

  testWidgets('voltar durante a geração volta ao formulário, e a resposta que '
      'chega depois é ignorada', (tester) async {
    final respostas = [Completer<http.Response>(), Completer<http.Response>()];
    var chamadas = 0;
    await abrirGerarIa(tester, {gerarIa: (_) => respostas[chamadas++].future});
    await preencher(tester);

    await tester.tap(botao('Gerar sugestões'));
    await tester.pump();
    expect(find.text(progresso), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    esperarFormularioPreenchido(tester);

    // A primeira geração termina com sucesso depois de cancelada: nada acontece.
    respostas[0].complete(sugestoes(requisicoes.last));
    await tester.pumpAndSettle();
    esperarFormularioPreenchido(tester);
    expect(find.textContaining('Revisar sugestões'), findsNothing);

    // Nem o erro de uma geração cancelada aparece.
    await tester.tap(botao('Gerar sugestões'));
    await tester.pump();
    await tester.pageBack();
    await tester.pumpAndSettle();
    respostas[1].complete(json(502, {'mensagem': 'Falhou.'}));
    await tester.pumpAndSettle();
    esperarFormularioPreenchido(tester);
    expect(
      find.text(
        'A IA não conseguiu gerar os cartões desta vez. Tente de novo.',
      ),
      findsNothing,
    );
    expect(corposDe(lote), isEmpty);
  });

  testWidgets('sair da tela durante a geração não abre nada depois', (
    tester,
  ) async {
    final resposta = Completer<http.Response>();
    await abrirGerarIa(tester, {gerarIa: (_) => resposta.future});
    await preencher(tester);
    await tester.tap(botao('Gerar sugestões'));
    await tester.pump();

    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.text('Como você quer criar?'), findsOneWidget);

    resposta.complete(sugestoes(requisicoes.last));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Como você quer criar?'), findsOneWidget);
    expect(find.textContaining('Revisar sugestões'), findsNothing);
  });

  testWidgets('401 durante a geração volta ao login', (tester) async {
    await abrirGerarIa(tester, {gerarIa: (_) => http.Response('', 401)});
    await preencher(tester);

    await tester.tap(botao('Gerar sugestões'));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(TelaLogin), findsOneWidget);
    expect(find.text('Sua sessão expirou. Entre de novo.'), findsOneWidget);
  });

  group('anexar PDF', () {
    const extrair = 'POST /api/pdf/extrair-texto';
    const lendo = 'Lendo o PDF…';
    const truncado = 'O PDF é longo: só o começo dele foi lido.';
    const substituir = 'Substituir o texto atual pelo do PDF?';
    late int escolhas;

    setUp(() => escolhas = 0);

    /// O seletor de arquivos falso: o usuário escolhe "aula.pdf".
    Future<PdfEscolhido?> escolherAula() async {
      escolhas++;
      final bytes = utf8.encode('%PDF-1.7');
      return (nome: 'aula.pdf', tamanho: bytes.length, ler: () async => bytes);
    }

    http.Response textoDoPdf(
      String texto, {
      int paginas = 3,
      bool truncado = false,
    }) => json(200, {'texto': texto, 'paginas': paginas, 'truncado': truncado});

    /// Abre o Gerar com IA direto, num celular de 390×844, com o seletor [escolherPdf] no lugar
    /// do plugin. [respostas] mapeia "MÉTODO caminho" para a resposta do backend falso.
    Future<void> abrir(
      WidgetTester tester,
      Map<String, Resposta> respostas, {
      Future<PdfEscolhido?> Function()? escolherPdf,
    }) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          theme: temaFlipo,
          home: TelaGerarIa(
            api: ApiClient(
              httpClient: MockClient((requisicao) async {
                requisicoes.add(requisicao);
                return respostas['${requisicao.method} ${requisicao.url.path}']!(
                  requisicao,
                );
              }),
            ),
            materiaId: 'm1',
            escolherPdf: escolherPdf ?? escolherAula,
          ),
        ),
      );
    }

    Future<void> tocarAnexar(WidgetTester tester) async {
      await tester.ensureVisible(anexar);
      await tester.tap(anexar);
      await tester.pump();
    }

    Iterable<http.Request> extracoes() =>
        requisicoes.where((r) => '${r.method} ${r.url.path}' == extrair);

    testWidgets('com o campo vazio, o texto do PDF vai para o campo, com o '
        'nome e as páginas, e é o que vai para a IA', (tester) async {
      final resposta = Completer<http.Response>();
      await abrir(tester, {
        extrair: (_) => resposta.future,
        gerarIa: sugestoes,
      });

      await tocarAnexar(tester);
      expect(find.text(lendo), findsOneWidget);
      expect(habilitado(tester, anexar), isFalse);
      expect(extracoes(), hasLength(1));
      expect(extracoes().single.headers['Authorization'], 'Bearer jwt-ana');

      resposta.complete(textoDoPdf('Camada de transporte.'));
      await tester.pumpAndSettle();

      expect(find.text(substituir), findsNothing);
      expect(textoDoCampo(tester), 'Camada de transporte.');
      expect(find.text('aula.pdf · 3 páginas'), findsOneWidget);
      expect(find.text(truncado), findsNothing);
      expect(find.text(lendo), findsNothing);
      expect(habilitado(tester, anexar), isTrue);
      expect(corposDe(gerarIa), isEmpty);

      await tester.tap(botao('Gerar sugestões'));
      await tester.pumpAndSettle();
      expect(corposDe(gerarIa), [
        {
          'texto': 'Camada de transporte.',
          'quantidade': 10,
          'nivel': 'definicao',
        },
      ]);
      expect(find.text('Revisar sugestões (3)'), findsOneWidget);
    });

    testWidgets(
      'com texto no campo, pergunta antes; substituir troca o texto',
      (tester) async {
        final resposta = Completer<http.Response>();
        await abrir(tester, {extrair: (_) => resposta.future});
        await tester.enterText(campoTexto, 'Meu texto');
        await tester.pump();

        await tocarAnexar(tester);
        // Lendo o PDF, nem anexar outro nem gerar.
        expect(find.text(lendo), findsOneWidget);
        expect(habilitado(tester, anexar), isFalse);
        expect(habilitado(tester, botao('Gerar sugestões')), isFalse);

        resposta.complete(textoDoPdf('Texto do PDF', paginas: 1));
        await tester.pumpAndSettle();
        expect(find.text(substituir), findsOneWidget);
        expect(textoDoCampo(tester), 'Meu texto');

        await tester.tap(find.text('Substituir'));
        await tester.pumpAndSettle();
        expect(find.text(substituir), findsNothing);
        expect(textoDoCampo(tester), 'Texto do PDF');
        expect(find.text('aula.pdf · 1 página'), findsOneWidget);
        expect(habilitado(tester, botao('Gerar sugestões')), isTrue);
      },
    );

    testWidgets('recusar a substituição deixa o texto do campo como estava', (
      tester,
    ) async {
      await abrir(tester, {extrair: (_) => textoDoPdf('Texto do PDF')});
      await tester.enterText(campoTexto, 'Meu texto');
      await tester.pump();

      await tocarAnexar(tester);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();

      expect(find.text(substituir), findsNothing);
      expect(textoDoCampo(tester), 'Meu texto');
      expect(find.textContaining('aula.pdf'), findsNothing);
    });

    testWidgets('PDF truncado: avisa que só o começo foi lido', (tester) async {
      await abrir(tester, {
        extrair: (_) => textoDoPdf('Começo do livro', truncado: true),
      });

      await tocarAnexar(tester);
      await tester.pumpAndSettle();

      expect(textoDoCampo(tester), 'Começo do livro');
      expect(find.text(truncado), findsOneWidget);
    });

    testWidgets('cortar o texto do PDF mantém o nome e o aviso; esvaziar o '
        'campo os tira, e não voltam com outro texto', (tester) async {
      await abrir(tester, {
        extrair: (_) =>
            textoDoPdf('Começo do livro', paginas: 12, truncado: true),
      });
      await tocarAnexar(tester);
      await tester.pumpAndSettle();
      expect(find.text('aula.pdf · 12 páginas'), findsOneWidget);

      await tester.enterText(campoTexto, 'Começo');
      await tester.pump();
      expect(find.text('aula.pdf · 12 páginas'), findsOneWidget);
      expect(find.text(truncado), findsOneWidget);

      await tester.enterText(campoTexto, '  ');
      await tester.pump();
      expect(find.textContaining('aula.pdf'), findsNothing);
      expect(find.text(truncado), findsNothing);

      await tester.enterText(campoTexto, 'Outro texto, colado');
      await tester.pump();
      expect(find.textContaining('aula.pdf'), findsNothing);
      expect(find.text(truncado), findsNothing);
    });

    testWidgets('o erro do PDF some quando o usuário edita o campo, não '
        'quando só move o cursor', (tester) async {
      const semTexto =
          'Este PDF não tem texto selecionável (talvez seja digitalizado). '
          'Cole o texto à mão.';
      await abrir(tester, {
        extrair: (_) => json(422, {'mensagem': 'Mensagem do servidor.'}),
      });
      await tester.enterText(campoTexto, 'Meu texto');
      await tester.pump();
      await tocarAnexar(tester);
      await tester.pumpAndSettle();
      expect(find.text(semTexto), findsOneWidget);

      tester.testTextInput.updateEditingValue(
        const TextEditingValue(
          text: 'Meu texto',
          selection: TextSelection.collapsed(offset: 3),
        ),
      );
      await tester.pump();
      expect(find.text(semTexto), findsOneWidget);

      await tester.enterText(campoTexto, 'Meu texto colado à mão');
      await tester.pump();
      expect(find.text(semTexto), findsNothing);
    });

    testWidgets('acima de 20 000, o campo aceita o texto do PDF e só gera '
        'depois do corte; apagar não leva outro trecho', (tester) async {
      final pdf = '${'A' * 10000}${'B' * 10010}';
      await abrir(tester, {
        extrair: (_) => textoDoPdf(pdf),
        gerarIa: sugestoes,
      });
      await tocarAnexar(tester);
      await tester.pumpAndSettle();

      const corte = 'Corte o texto para até 20.000 caracteres';
      TextStyle? estiloDoContador() =>
          tester.widget<TextField>(campoTexto).decoration!.counterStyle;
      expect(textoDoCampo(tester), pdf);
      expect(find.text('20.010 / 20.000'), findsOneWidget);
      expect(estiloDoContador(), temaFlipo.inputDecorationTheme.errorStyle);
      expect(find.text(corte), findsOneWidget);
      expect(habilitado(tester, botao('Gerar sugestões')), isFalse);

      await tester.showKeyboard(campoTexto);
      void editar(String texto, int cursor) =>
          tester.testTextInput.updateEditingValue(
            TextEditingValue(
              text: texto,
              selection: TextSelection.collapsed(offset: cursor),
            ),
          );

      // Mover o cursor não mexe no texto.
      editar(pdf, 5000);
      await tester.pump();
      expect(textoDoCampo(tester), pdf);

      // Apagar 5 "A" no meio leva só esses 5.
      final semCincoA = '${'A' * 9995}${'B' * 10010}';
      editar(semCincoA, 9995);
      await tester.pump();
      expect(textoDoCampo(tester), semCincoA);
      expect(habilitado(tester, botao('Gerar sugestões')), isFalse);

      // Trocar um caractere por outro (mesmo tamanho) passa.
      final trocado = '${'A' * 9994}C${'B' * 10010}';
      editar(trocado, 9995);
      await tester.pump();
      expect(textoDoCampo(tester), trocado);

      // Ainda acima do limite, o que aumentaria o texto não entra.
      editar('${'A' * 9994}CX${'B' * 10010}', 9996);
      await tester.pump();
      expect(textoDoCampo(tester), trocado);

      // Cortado até o limite, o aviso some e gerar ativa.
      final cortado = '${'A' * 9994}C${'B' * 10005}';
      editar(cortado, cortado.length);
      await tester.pump();
      expect(textoDoCampo(tester), cortado);
      expect(find.text('20.000 / 20.000'), findsOneWidget);
      expect(find.text(corte), findsNothing);
      expect(habilitado(tester, botao('Gerar sugestões')), isTrue);

      await tester.ensureVisible(botao('Gerar sugestões'));
      await tester.pumpAndSettle();
      await tester.tap(botao('Gerar sugestões'));
      await tester.pumpAndSettle();
      expect(corposDe(gerarIa), [
        {'texto': cortado, 'quantidade': 10, 'nivel': 'definicao'},
      ]);
    });

    for (final (descricao, resposta, mensagem) in <(String, Resposta, String)>[
      (
        '400 com a mensagem do servidor',
        (_) => json(400, {
          'mensagem': 'Este PDF é pesado demais para extrair o texto.',
        }),
        'Este PDF é pesado demais para extrair o texto.',
      ),
      (
        '400 sem JSON (página de um proxy)',
        (_) => http.Response(
          '<html><body>Bad Request</body></html>',
          400,
          headers: {'content-type': 'text/html'},
        ),
        'Não foi possível ler este PDF. Ele pode ser inválido, estar '
            'corrompido ou protegido por senha.',
      ),
      (
        '400 com a mensagem vazia',
        (_) => json(400, {'mensagem': '  '}),
        'Não foi possível ler este PDF. Ele pode ser inválido, estar '
            'corrompido ou protegido por senha.',
      ),
      (
        '413',
        (_) => json(413, {'mensagem': 'Mensagem do servidor.'}),
        'O arquivo passa de 10 MB. Escolha um PDF menor.',
      ),
      (
        '422',
        (_) => json(422, {'mensagem': 'Mensagem do servidor.'}),
        'Este PDF não tem texto selecionável (talvez seja digitalizado). '
            'Cole o texto à mão.',
      ),
      (
        '503',
        (_) => json(503, {'mensagem': 'Mensagem do servidor.'}),
        'O servidor está ocupado lendo outros PDFs. Tente de novo em '
            'instantes.',
      ),
      (
        'sem conexão',
        (_) => throw http.ClientException('sem rede'),
        'Não foi possível conectar ao servidor.',
      ),
    ]) {
      testWidgets('$descricao: mostra a mensagem e o texto fica intacto; um '
          'PDF lido depois limpa a mensagem', (tester) async {
        var chamadas = 0;
        await abrir(tester, {
          extrair: (requisicao) => chamadas++ == 0
              ? resposta(requisicao)
              : textoDoPdf('Texto do PDF'),
        });
        await tester.enterText(campoTexto, 'Meu texto');
        await tester.pump();

        await tocarAnexar(tester);
        await tester.pumpAndSettle();

        expect(find.text(mensagem), findsOneWidget);
        expect(find.text('Mensagem do servidor.'), findsNothing);
        expect(find.text(substituir), findsNothing);
        expect(find.text(lendo), findsNothing);
        expect(textoDoCampo(tester), 'Meu texto');
        expect(habilitado(tester, anexar), isTrue);
        expect(habilitado(tester, botao('Gerar sugestões')), isTrue);

        await tocarAnexar(tester);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Substituir'));
        await tester.pumpAndSettle();
        expect(find.text(mensagem), findsNothing);
        expect(textoDoCampo(tester), 'Texto do PDF');
      });
    }

    const muitoGrande = 'O arquivo passa de 10 MB. Escolha um PDF menor.';

    testWidgets('acima de 10 MiB, nem lê nem envia o arquivo: avisa e o texto '
        'fica intacto', (tester) async {
      var leituras = 0;
      await abrir(
        tester,
        {},
        escolherPdf: () async => (
          nome: 'livro.pdf',
          tamanho: tamanhoMaximoDoPdf + 1,
          ler: () async {
            leituras++;
            return Uint8List(tamanhoMaximoDoPdf + 1);
          },
        ),
      );
      await tester.enterText(campoTexto, 'Meu texto');
      await tester.pump();

      await tocarAnexar(tester);
      await tester.pumpAndSettle();

      expect(find.text(muitoGrande), findsOneWidget);
      expect(leituras, 0);
      expect(requisicoes, isEmpty);
      expect(find.text(lendo), findsNothing);
      expect(textoDoCampo(tester), 'Meu texto');
      expect(habilitado(tester, anexar), isTrue);
    });

    testWidgets('sem o tamanho informado, confere depois de ler: acima de '
        '10 MiB não envia', (tester) async {
      await abrir(
        tester,
        {},
        escolherPdf: () async => (
          nome: 'livro.pdf',
          tamanho: null,
          ler: () async => Uint8List(tamanhoMaximoDoPdf + 1),
        ),
      );

      await tocarAnexar(tester);
      await tester.pumpAndSettle();

      expect(find.text(muitoGrande), findsOneWidget);
      expect(requisicoes, isEmpty);
      expect(find.text(lendo), findsNothing);
    });

    testWidgets('exatamente 10 MiB é enviado', (tester) async {
      await abrir(
        tester,
        {extrair: (_) => textoDoPdf('Texto do livro')},
        escolherPdf: () async => (
          nome: 'livro.pdf',
          tamanho: tamanhoMaximoDoPdf,
          ler: () async => Uint8List(tamanhoMaximoDoPdf),
        ),
      );

      await tocarAnexar(tester);
      await tester.pumpAndSettle();

      expect(extracoes(), hasLength(1));
      expect(find.text(muitoGrande), findsNothing);
      expect(textoDoCampo(tester), 'Texto do livro');
    });

    testWidgets('cancelar o seletor não faz nada', (tester) async {
      await abrir(
        tester,
        {},
        escolherPdf: () async {
          escolhas++;
          return null;
        },
      );
      await tester.enterText(campoTexto, 'Meu texto');
      await tester.pump();

      await tocarAnexar(tester);
      await tester.pumpAndSettle();

      expect(escolhas, 1);
      expect(requisicoes, isEmpty);
      expect(find.text(lendo), findsNothing);
      expect(find.text(substituir), findsNothing);
      expect(textoDoCampo(tester), 'Meu texto');
      expect(habilitado(tester, anexar), isTrue);
    });
  });
}
