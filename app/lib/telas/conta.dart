import 'package:flutter/material.dart';

import '../widgets/coluna_central.dart';

/// Conta (docs/01-fluxos-de-usuario.md, seção 8), aberta pelo avatar de Minhas matérias. Por ora
/// só "Sair"; "Excluir conta" chega na #44 e a chave de IA no E6.
class TelaConta extends StatelessWidget {
  const TelaConta({super.key, required this.aoSair});

  final VoidCallback aoSair;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Conta')),
      body: SafeArea(
        child: ColunaCentral(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Card(
                child: ListTile(
                  leading: const Icon(Icons.logout),
                  title: const Text('Sair'),
                  onTap: aoSair,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
