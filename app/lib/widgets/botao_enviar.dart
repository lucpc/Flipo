import 'package:flutter/material.dart';

/// Botão principal de um envio: enquanto [enviando], fica desabilitado e mostra um indicador de
/// carregamento no lugar do rótulo.
class BotaoEnviar extends StatelessWidget {
  const BotaoEnviar({
    super.key,
    required this.rotulo,
    required this.enviando,
    required this.aoPressionar,
  });

  final String rotulo;
  final bool enviando;
  final VoidCallback aoPressionar;

  @override
  Widget build(BuildContext context) {
    return FilledButton(
      onPressed: enviando ? null : aoPressionar,
      child: enviando
          ? SizedBox.square(
              dimension: 20,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                semanticsLabel: rotulo,
              ),
            )
          : Text(rotulo),
    );
  }
}
