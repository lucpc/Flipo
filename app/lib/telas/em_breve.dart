import 'package:flutter/material.dart';

/// Destino provisório das entradas da tela da matéria cujos fluxos ainda não existem: sessão de
/// estudo (#47), Arquivo (#23) e adicionar cartão (#42). Cada issue troca esta tela pela de
/// verdade.
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
