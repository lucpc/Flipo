import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import '../api/api_client.dart';
import '../widgets/coluna_central.dart';

/// Login (docs/01-fluxos-de-usuario.md, seção 1, quadro 01 do Figma). [aoEntrar] avisa o app que
/// o token foi salvo, para trocar para a área logada.
class TelaLogin extends StatefulWidget {
  const TelaLogin({super.key, required this.api, required this.aoEntrar});

  final ApiClient api;
  final VoidCallback aoEntrar;

  @override
  State<TelaLogin> createState() => _TelaLoginState();
}

class _TelaLoginState extends State<TelaLogin> {
  final _formulario = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _senha = TextEditingController();
  bool _enviando = false;
  String? _erro;

  @override
  void dispose() {
    _email.dispose();
    _senha.dispose();
    super.dispose();
  }

  Future<void> _entrar() async {
    if (_enviando || !_formulario.currentState!.validate()) return;
    final aoEntrar = widget.aoEntrar;
    setState(() {
      _enviando = true;
      _erro = null;
    });

    final erro = await _erroDe(
      () => widget.api.login(_email.text.trim(), _senha.text),
    );
    if (erro == null) {
      aoEntrar();
      return;
    }
    if (mounted) {
      setState(() {
        _enviando = false;
        _erro = erro;
      });
    }
  }

  void _abrirCriarConta() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            TelaCriarConta(api: widget.api, aoEntrar: widget.aoEntrar),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: ColunaCentral(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Form(
                key: _formulario,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      'Flipo',
                      textAlign: TextAlign.center,
                      style: tema.textTheme.displaySmall?.copyWith(
                        color: tema.colorScheme.primary,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'flashcards livres, no seu ritmo',
                      textAlign: TextAlign.center,
                      style: tema.textTheme.bodyLarge?.copyWith(
                        color: tema.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 40),
                    _CampoEmail(controlador: _email),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _senha,
                      decoration: const InputDecoration(labelText: 'Senha'),
                      obscureText: true,
                      autofillHints: const [AutofillHints.password],
                      textInputAction: TextInputAction.done,
                      onFieldSubmitted: (_) => _entrar(),
                      validator: (valor) => (valor == null || valor.isEmpty)
                          ? 'Informe a senha.'
                          : null,
                    ),
                    _MensagemDeErro(_erro),
                    const SizedBox(height: 24),
                    _BotaoEnviar(
                      rotulo: 'Entrar',
                      enviando: _enviando,
                      aoPressionar: _entrar,
                    ),
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: _enviando ? null : _abrirCriarConta,
                      child: const Text('Criar conta'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Cadastro (nome, e-mail, senha). Depois de criar a conta, entra com as mesmas credenciais.
class TelaCriarConta extends StatefulWidget {
  const TelaCriarConta({super.key, required this.api, required this.aoEntrar});

  final ApiClient api;
  final VoidCallback aoEntrar;

  @override
  State<TelaCriarConta> createState() => _TelaCriarContaState();
}

class _TelaCriarContaState extends State<TelaCriarConta> {
  // Mesmos limites do RegistroRequest do backend: fora deles, a mensagem de validação do Spring
  // chegaria crua ao usuário ("senha: size must be between 8 and 72").
  static const _senhaMinima = 8;
  static const _senhaMaxima = 72;

  final _formulario = GlobalKey<FormState>();
  final _nome = TextEditingController();
  final _email = TextEditingController();
  final _senha = TextEditingController();
  bool _enviando = false;
  String? _erro;

  @override
  void dispose() {
    _nome.dispose();
    _email.dispose();
    _senha.dispose();
    super.dispose();
  }

  Future<void> _criarConta() async {
    if (_enviando || !_formulario.currentState!.validate()) return;
    final api = widget.api;
    final aoEntrar = widget.aoEntrar;
    final email = _email.text.trim();
    final senha = _senha.text;
    setState(() {
      _enviando = true;
      _erro = null;
    });

    var erro = await _erroDe(
      () => api.registrar(_nome.text.trim(), email, senha),
    );
    if (erro == null) {
      erro = await _erroDe(() => api.login(email, senha));
      if (erro == null) {
        aoEntrar();
        return;
      }
      // Tentar de novo aqui daria "E-mail já cadastrado": a conta existe, falta só entrar.
      erro =
          'Conta criada, mas não deu para entrar agora. '
          'Volte e entre com seu e-mail e senha.';
    }
    if (mounted) {
      setState(() {
        _enviando = false;
        _erro = erro;
      });
    }
  }

  String? _validarSenha(String? valor) {
    if (valor == null || valor.isEmpty) return 'Informe a senha.';
    if (valor.length < _senhaMinima) {
      return 'A senha precisa ter pelo menos $_senhaMinima caracteres.';
    }
    if (valor.length > _senhaMaxima) {
      return 'A senha pode ter no máximo $_senhaMaxima caracteres.';
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Criar conta')),
      body: SafeArea(
        child: ColunaCentral(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: _formulario,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextFormField(
                    controller: _nome,
                    decoration: const InputDecoration(labelText: 'Nome'),
                    textCapitalization: TextCapitalization.words,
                    autofillHints: const [AutofillHints.name],
                    textInputAction: TextInputAction.next,
                    validator: (valor) =>
                        (valor == null || valor.trim().isEmpty)
                        ? 'Informe seu nome.'
                        : null,
                  ),
                  const SizedBox(height: 16),
                  _CampoEmail(controlador: _email),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _senha,
                    decoration: const InputDecoration(
                      labelText: 'Senha',
                      helperText: 'Pelo menos $_senhaMinima caracteres',
                    ),
                    obscureText: true,
                    autofillHints: const [AutofillHints.newPassword],
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => _criarConta(),
                    validator: _validarSenha,
                  ),
                  _MensagemDeErro(_erro),
                  const SizedBox(height: 24),
                  _BotaoEnviar(
                    rotulo: 'Criar conta',
                    enviando: _enviando,
                    aoPressionar: _criarConta,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Roda [acao] e devolve a mensagem para mostrar ao usuário, ou null se deu certo.
Future<String?> _erroDe(Future<void> Function() acao) async {
  try {
    await acao();
    return null;
  } on ApiException catch (e) {
    return e.mensagem;
  } on http.ClientException {
    return 'Não foi possível conectar ao servidor.';
  }
}

// Forma básica (algo@algo.algo). A validação de verdade é a do backend, cuja mensagem aparece em
// _MensagemDeErro.
final _formatoDeEmail = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');

class _CampoEmail extends StatelessWidget {
  const _CampoEmail({required this.controlador});

  final TextEditingController controlador;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controlador,
      decoration: const InputDecoration(labelText: 'E-mail'),
      keyboardType: TextInputType.emailAddress,
      autocorrect: false,
      autofillHints: const [AutofillHints.email],
      textInputAction: TextInputAction.next,
      validator: (valor) {
        final email = valor?.trim() ?? '';
        if (email.isEmpty) return 'Informe o e-mail.';
        if (!_formatoDeEmail.hasMatch(email)) return 'E-mail inválido.';
        return null;
      },
    );
  }
}

class _MensagemDeErro extends StatelessWidget {
  const _MensagemDeErro(this.mensagem);

  final String? mensagem;

  @override
  Widget build(BuildContext context) {
    final mensagem = this.mensagem;
    if (mensagem == null) return const SizedBox.shrink();
    final tema = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: Text(
        mensagem,
        style: tema.textTheme.bodyMedium?.merge(
          tema.inputDecorationTheme.errorStyle,
        ),
      ),
    );
  }
}

class _BotaoEnviar extends StatelessWidget {
  const _BotaoEnviar({
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
