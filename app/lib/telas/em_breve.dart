import 'package:flutter/material.dart';

/// Destino provisório das entradas da tela da matéria cujos fluxos ainda não existem: hoje só o
/// Arquivo (#23), que troca esta tela pela de verdade.
class TelaEmBreve extends StatelessWidget {
  const TelaEmBreve({super.key, required this.titulo});

  final String titulo;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(titulo)),
      body: Center(
        child: Text(
          'Em breve',
          style: tema.textTheme.bodyLarge?.copyWith(
            color: tema.colorScheme.onSurfaceVariant,
          ),
        ),
      ),
    );
  }
}
