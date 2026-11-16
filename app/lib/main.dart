import 'package:flutter/material.dart';

import 'theme.dart';
import 'widgets/coluna_central.dart';

void main() {
  runApp(const FlipoApp());
}

class FlipoApp extends StatelessWidget {
  const FlipoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flipo',
      theme: temaFlipo,
      debugShowCheckedModeBanner: false,
      home: const TelaInicial(),
    );
  }
}

/// Tela provisória, só para exibir o tema. O login entra no lugar dela (#40).
class TelaInicial extends StatelessWidget {
  const TelaInicial({super.key});

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: ColunaCentral(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text('Flipo', style: tema.textTheme.displaySmall),
                const SizedBox(height: 8),
                Text(
                  'flashcards livres, no seu ritmo',
                  style: tema.textTheme.bodyLarge?.copyWith(
                    color: tema.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
