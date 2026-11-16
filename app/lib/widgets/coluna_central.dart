import 'package:flutter/widgets.dart';

/// Coluna de conteúdo do app. O layout é pensado para o celular (referência 390×844); em telas
/// largas (web, tablet) o conteúdo fica centralizado com largura limitada, em vez de esticar.
class ColunaCentral extends StatelessWidget {
  const ColunaCentral({super.key, required this.child});

  static const larguraMaxima = 480.0;

  final Widget child;

  @override
  Widget build(BuildContext context) {
    // A largura ocupa a tela toda até o limite; a altura segue o pai.
    return Align(
      alignment: Alignment.topCenter,
      child: SizedBox(width: larguraMaxima, child: child),
    );
  }
}
