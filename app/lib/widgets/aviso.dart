import 'package:flutter/material.dart';

/// Aviso discreto no topo de uma tela, ex.: o que a tela da matéria mostra ao voltar de um fluxo
/// (quadros 03c–03e) ou o Arquivo depois de desarquivar (07c). O leitor de tela o anuncia ao
/// aparecer.
class Aviso extends StatelessWidget {
  const Aviso(this.texto, {super.key});

  final String texto;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: tema.colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Icon(
              Icons.check_circle_outline,
              size: 20,
              color: tema.colorScheme.primary,
            ),
            const SizedBox(width: 12),
            Expanded(child: Text(texto, style: tema.textTheme.bodyMedium)),
          ],
        ),
      ),
    );
  }
}
