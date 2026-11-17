import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'api/api_client.dart';
import 'telas/autenticacao.dart';
import 'telas/materias.dart';
import 'theme.dart';

void main() {
  runApp(const FlipoApp());
}

/// Raiz do app. Guarda a sessão (há token salvo ou não) e mostra o login ou a área logada. Quando
/// a sessão muda, fecha as telas abertas por cima da raiz: um 401 no meio de qualquer fluxo, ou
/// "Sair", volta direto ao login, e o login volta direto à área logada.
class FlipoApp extends StatefulWidget {
  const FlipoApp({super.key, this.httpClient});

  /// Só para testes; no app, o [ApiClient] cria o seu.
  final http.Client? httpClient;

  @override
  State<FlipoApp> createState() => _FlipoAppState();
}

class _FlipoAppState extends State<FlipoApp> {
  final _navegador = GlobalKey<NavigatorState>();
  final _mensageiro = GlobalKey<ScaffoldMessengerState>();
  // null enquanto o token salvo ainda está sendo lido.
  final _logado = ValueNotifier<bool?>(null);
  late final _api = ApiClient(
    httpClient: widget.httpClient,
    aoExpirarSessao: _expirarSessao,
  );

  @override
  void initState() {
    super.initState();
    // Token ilegível equivale a não ter sessão: o usuário só entra de novo.
    _api.temToken().catchError((Object _) => false).then((temToken) {
      if (mounted) _logado.value ??= temToken;
    });
  }

  @override
  void dispose() {
    _logado.dispose();
    super.dispose();
  }

  void _mudarSessao(bool logado) {
    if (!mounted) return;
    _navegador.currentState?.popUntil((rota) => rota.isFirst);
    _mensageiro.currentState?.removeCurrentSnackBar();
    _logado.value = logado;
  }

  // Só no 401; "Sair" volta ao login sem aviso.
  void _expirarSessao() {
    _mudarSessao(false);
    _mensageiro.currentState?.showSnackBar(
      const SnackBar(content: Text('Sua sessão expirou. Entre de novo.')),
    );
  }

  Future<void> _sair() async {
    // Mesmo se apagar o token falhar, a tela volta ao login (o erro segue para o log).
    try {
      await _api.limparToken();
    } finally {
      _mudarSessao(false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flipo',
      theme: temaFlipo,
      debugShowCheckedModeBanner: false,
      navigatorKey: _navegador,
      scaffoldMessengerKey: _mensageiro,
      // A troca acontece dentro da rota raiz, que o Navigator mantém: trocar o `home` do
      // MaterialApp não reconstruiria uma rota que já existe.
      home: ValueListenableBuilder<bool?>(
        valueListenable: _logado,
        builder: (context, logado, _) => switch (logado) {
          null => const Scaffold(),
          false => TelaLogin(api: _api, aoEntrar: () => _mudarSessao(true)),
          true => TelaMinhasMaterias(api: _api, aoSair: _sair),
        },
      ),
    );
  }
}
