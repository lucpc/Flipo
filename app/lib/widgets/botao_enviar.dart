import 'package:flutter/material.dart';

/// Botão principal de um envio: enquanto [enviando], fica desabilitado e mostra um indicador de
/// carregamento no lugar do rótulo. Sem [aoPressionar], fica desabilitado.
class BotaoEnviar extends StatelessWidget {
  const BotaoEnviar({
    super.key,
    required this.rotulo,
    this.icone,
    required this.enviando,
    required this.aoPressionar,
  });

  final String rotulo;

  /// Ícone depois do rótulo; some enquanto envia.
  final IconData? icone;
  final bool enviando;
  final VoidCallback? aoPressionar;

  @override
  Widget build(BuildContext context) {
    final icone = this.icone;
    return FilledButton.icon(
      onPressed: enviando ? null : aoPressionar,
      icon: icone == null || enviando ? null : Icon(icone),
      iconAlignment: IconAlignment.end,
      label: enviando ? IndicadorDeEnvio(rotulo) : Text(rotulo),
    );
  }
}

/// O indicador que ocupa o lugar do rótulo de um botão enquanto a ação dele é enviada. O leitor de
/// tela continua anunciando o [rotulo].
class IndicadorDeEnvio extends StatelessWidget {
  const IndicadorDeEnvio(this.rotulo, {super.key});

  final String rotulo;

  @override
  Widget build(BuildContext context) {
    return SizedBox.square(
      dimension: 20,
      child: CircularProgressIndicator(strokeWidth: 2, semanticsLabel: rotulo),
    );
  }
}
