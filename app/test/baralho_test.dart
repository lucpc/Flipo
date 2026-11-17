import 'package:flipo/api/api_client.dart';
import 'package:flipo/theme.dart';
import 'package:flipo/widgets/baralho.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Cartao cartao(String id) => Cartao(
    id: id,
    pergunta: 'Pergunta $id',
    resposta: 'Resposta $id',
    origem: 'manual',
    arquivado: true,
  );

  final rotulo = find.text('pergunta');
  final rotuloResposta = find.text('resposta');
  final proximo = find.text('Próximo cartão');
  final estudarDeNovo = find.text('Estudar de novo');
  final fim = find.text('Fim do Arquivo');

  /// O Baralho dentro de um dono que guarda a lista, como a revisão do Arquivo (#23) vai fazer.
  /// Com [comAcao], "Desarquivar este cartão" tira o cartão atual da lista.
  Future<void> abrirBaralho(
    WidgetTester tester,
    List<Cartao> cartoes, {
    bool comAcao = false,
    Widget Function(Cartao)? canto,
    Color? Function(ColorScheme)? borda,
    bool animacoesReduzidas = false,
  }) async {
    var lista = cartoes;
    await tester.pumpWidget(
      MaterialApp(
        theme: temaFlipo,
        builder: (context, filho) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(disableAnimations: animacoesReduzidas),
          child: filho!,
        ),
        home: StatefulBuilder(
          builder: (context, setState) => Baralho(
            cartoes: lista,
            titulo: 'Arquivo',
            posicao: (n, total) => 'Redes · cartão $n de $total',
            tituloDoFim: 'Fim do Arquivo',
            apoioDoFim: 'Sem nota.',
            corDaBorda: borda?.call(Theme.of(context).colorScheme),
            cantoDoCartao: canto,
            acaoDoCartao: comAcao
                ? (atual) => OutlinedButton(
                    onPressed: () =>
                        setState(() => lista = [...lista]..remove(atual)),
                    child: const Text('Desarquivar este cartão'),
                  )
                : null,
          ),
        ),
      ),
    );
  }

  Future<void> tocar(WidgetTester tester, Finder alvo) async {
    await tester.tap(alvo);
    await tester.pumpAndSettle();
  }

  // O Material que desenha o cartão (e a borda dele).
  Material materialDoCartao(WidgetTester tester) => tester.widget<Material>(
    find
        .ancestor(
          of: find.text('toque para virar'),
          matching: find.byType(Material),
        )
        .first,
  );

  testWidgets('tirar o cartão atual da lista mostra o próximo pela pergunta, '
      'na mesma posição', (tester) async {
    await abrirBaralho(tester, [
      cartao('a'),
      cartao('b'),
      cartao('c'),
    ], comAcao: true);
    await tocar(tester, find.text('Pergunta a'));
    expect(rotuloResposta, findsOneWidget);

    await tocar(tester, find.text('Desarquivar este cartão'));

    expect(find.text('Redes · cartão 1 de 2'), findsOneWidget);
    expect(find.text('Pergunta b'), findsOneWidget);
    expect(rotulo, findsOneWidget);
    expect(find.textContaining('Resposta'), findsNothing);
  });

  testWidgets('tirar o último cartão da lista leva ao fim, que ainda deixa '
      'recomeçar', (tester) async {
    await abrirBaralho(tester, [cartao('a'), cartao('b')], comAcao: true);
    await tocar(tester, proximo);
    expect(find.text('Redes · cartão 2 de 2'), findsOneWidget);

    await tocar(tester, find.text('Desarquivar este cartão'));

    expect(fim, findsOneWidget);
    expect(find.textContaining('cartão 1 de'), findsNothing);
    await tocar(tester, estudarDeNovo);
    expect(find.text('Redes · cartão 1 de 1'), findsOneWidget);
    expect(find.text('Pergunta a'), findsOneWidget);
  });

  testWidgets('com a lista vazia, o fim não oferece "Estudar de novo"', (
    tester,
  ) async {
    await abrirBaralho(tester, [cartao('a')], comAcao: true);

    await tocar(tester, find.text('Desarquivar este cartão'));

    expect(fim, findsOneWidget);
    expect(find.text('Sem nota.'), findsOneWidget);
    expect(estudarDeNovo, findsNothing);
    expect(find.text('Voltar para a matéria'), findsOneWidget);
  });

  testWidgets('a ação abaixo do cartão fica entre o cartão e "Próximo '
      'cartão"', (tester) async {
    await abrirBaralho(tester, [cartao('a')], comAcao: true);

    final acao = tester.getRect(find.text('Desarquivar este cartão'));
    expect(
      acao.top,
      greaterThan(tester.getRect(find.text('toque para virar')).bottom),
    );
    expect(acao.bottom, lessThan(tester.getRect(proximo).top));
  });

  testWidgets('o canto do cartão recebe o cartão atual, fica no alto à '
      'direita, e tocar nele não vira', (tester) async {
    final tocados = <String>[];
    await abrirBaralho(
      tester,
      [cartao('a'), cartao('b')],
      canto: (atual) => IconButton(
        tooltip: 'Editar ${atual.pergunta}',
        onPressed: () => tocados.add(atual.id),
        icon: const Icon(Icons.more_vert),
      ),
    );
    final cartaoNaTela = tester.getRect(
      find.ancestor(of: rotulo, matching: find.byType(Material)).first,
    );
    final canto = tester.getRect(find.byIcon(Icons.more_vert));
    expect(cartaoNaTela.contains(canto.topLeft), isTrue);
    expect(canto.left, greaterThan(cartaoNaTela.center.dx));
    expect(canto.bottom, lessThan(cartaoNaTela.center.dy));

    await tocar(tester, find.byTooltip('Editar Pergunta a'));

    expect(tocados, ['a']);
    expect(rotulo, findsOneWidget);
    await tocar(tester, proximo);
    expect(find.byTooltip('Editar Pergunta b'), findsOneWidget);
  });

  testWidgets('"Estudar de novo" volta à pergunta mesmo se o cartão estava '
      'virado', (tester) async {
    await abrirBaralho(tester, [cartao('a')]);
    await tocar(tester, find.text('Pergunta a'));
    await tocar(tester, proximo);

    await tocar(tester, estudarDeNovo);

    expect(rotulo, findsOneWidget);
    expect(find.text('Pergunta a'), findsOneWidget);
  });

  testWidgets('a borda é a discreta do tema, ou a cor pedida', (tester) async {
    await abrirBaralho(tester, [cartao('a')]);
    expect(
      (materialDoCartao(tester).shape! as RoundedRectangleBorder).side.color,
      temaFlipo.colorScheme.outlineVariant,
    );

    await abrirBaralho(tester, [cartao('a')], borda: (cores) => cores.tertiary);
    expect(
      (materialDoCartao(tester).shape! as RoundedRectangleBorder).side.color,
      temaFlipo.colorScheme.tertiary,
    );
  });

  testWidgets('o cartão é um botão, e o rótulo dele muda ao virar', (
    tester,
  ) async {
    final semantica = tester.ensureSemantics();
    await abrirBaralho(tester, [cartao('a')]);

    expect(
      tester.getSemantics(rotulo),
      isSemantics(
        label: 'pergunta\nPergunta a\ntoque para virar',
        isButton: true,
        isLiveRegion: true,
        hasTapAction: true,
      ),
    );
    await tocar(tester, rotulo);
    expect(
      tester.getSemantics(rotuloResposta),
      isSemantics(
        label: 'resposta\nResposta a\ntoque para virar',
        isButton: true,
        isLiveRegion: true,
      ),
    );
    semantica.dispose();
  });

  testWidgets('vira com animação; com animações reduzidas, vira de uma vez', (
    tester,
  ) async {
    await abrirBaralho(tester, [cartao('a')]);
    await tester.tap(rotulo);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    // No meio do giro, ainda na pergunta.
    expect(rotulo, findsOneWidget);
    await tester.pumpAndSettle();
    expect(rotuloResposta, findsOneWidget);

    await abrirBaralho(tester, [cartao('b')], animacoesReduzidas: true);
    await tester.tap(rotulo);
    await tester.pump();
    expect(rotuloResposta, findsOneWidget);
  });

  testWidgets('sem avaliação de nenhum tipo, nem no cartão nem no fim', (
    tester,
  ) async {
    final avaliacao = find.textContaining(
      RegExp('acertei|errei|como você se saiu', caseSensitive: false),
    );
    await abrirBaralho(tester, [cartao('a')], comAcao: true);
    expect(avaliacao, findsNothing);
    await tocar(tester, rotulo);
    expect(avaliacao, findsNothing);
    await tocar(tester, proximo);
    expect(fim, findsOneWidget);
    expect(avaliacao, findsNothing);
  });
}
