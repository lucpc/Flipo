import 'package:flutter/material.dart';

// Paleta Rosé Pine Dawn do protótipo original (docs/00-visao-geral.md). Privada de propósito:
// os widgets leem as cores de Theme.of(context).colorScheme, nunca daqui.
abstract final class _RosePineDawn {
  static const base = Color.fromARGB(255, 250, 244, 237);
  static const surface = Color.fromARGB(255, 255, 250, 243);
  static const overlay = Color.fromARGB(255, 242, 233, 225);
  static const muted = Color.fromARGB(255, 152, 147, 165);
  static const subtle = Color.fromARGB(255, 121, 117, 147);
  static const text = Color.fromARGB(255, 87, 82, 121);
  static const pine = Color.fromARGB(255, 40, 105, 131);
  static const foam = Color.fromARGB(255, 86, 148, 159);
  static const iris = Color.fromARGB(255, 144, 122, 169);
  static const rose = Color.fromARGB(255, 215, 130, 126);
  static const highlightMed = Color.fromARGB(255, 223, 218, 217);
}

const _esquemaDeCores = ColorScheme(
  brightness: Brightness.light,
  primary: _RosePineDawn.pine,
  onPrimary: _RosePineDawn.surface,
  secondary: _RosePineDawn.foam,
  onSecondary: _RosePineDawn.surface,
  // Íris: borda do cartão na revisão do Arquivo (docs/01-fluxos-de-usuario.md, seção 7).
  tertiary: _RosePineDawn.iris,
  onTertiary: _RosePineDawn.surface,
  error: _RosePineDawn.rose,
  onError: _RosePineDawn.surface,
  surface: _RosePineDawn.surface,
  onSurface: _RosePineDawn.text,
  onSurfaceVariant: _RosePineDawn.subtle,
  surfaceContainerLowest: _RosePineDawn.surface,
  surfaceContainerLow: _RosePineDawn.surface,
  surfaceContainer: _RosePineDawn.overlay,
  surfaceContainerHigh: _RosePineDawn.overlay,
  surfaceContainerHighest: _RosePineDawn.overlay,
  outline: _RosePineDawn.muted,
  outlineVariant: _RosePineDawn.highlightMed,
);

final ThemeData temaFlipo = ThemeData(
  colorScheme: _esquemaDeCores,
  scaffoldBackgroundColor: _RosePineDawn.base,
  appBarTheme: const AppBarTheme(
    backgroundColor: _RosePineDawn.base,
    foregroundColor: _RosePineDawn.text,
  ),
);
