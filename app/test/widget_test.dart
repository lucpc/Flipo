import 'package:flipo/main.dart';
import 'package:flipo/widgets/coluna_central.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Future<void> abrirNaTela(WidgetTester tester, Size tamanho) async {
    tester.view.physicalSize = tamanho;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(const FlipoApp());
  }

  // Conteúdo logo abaixo da coluna (a própria ColunaCentral ocupa a tela toda para centralizar).
  final conteudo = find
      .descendant(
        of: find.byType(ColunaCentral),
        matching: find.byType(Padding),
      )
      .first;

  testWidgets('no celular, a coluna ocupa a largura toda', (tester) async {
    await abrirNaTela(tester, const Size(390, 844));

    expect(find.text('Flipo'), findsOneWidget);
    expect(find.text('flashcards livres, no seu ritmo'), findsOneWidget);
    expect(tester.getSize(conteudo).width, 390);
  });

  testWidgets(
    'em tela larga, a coluna fica centralizada com largura limitada',
    (tester) async {
      await abrirNaTela(tester, const Size(1280, 800));

      expect(tester.getSize(conteudo).width, ColunaCentral.larguraMaxima);
      expect(tester.getCenter(conteudo).dx, 640);
    },
  );
}
