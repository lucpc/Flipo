import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../widgets/botao_enviar.dart';
import '../widgets/coluna_central.dart';
import '../widgets/mensagem_de_erro.dart';

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

    String? erro;
    var entrou = false;
    try {
      erro = await _erroDe(
        () => widget.api.login(_email.text.trim(), _senha.text),
      );
      if (erro == null) {
        _entrouCom(aoEntrar);
        entrou = true;
      }
    } finally {
      // Depois de entrar, o botão segue carregando enquanto a tela sai.
      if (mounted && !entrou) {
        setState(() {
          _enviando = false;
          _erro = erro;
        });
      }
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
                child: AutofillGroup(
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
                      // O LoginRequest do backend só exige e-mail preenchido.
                      _CampoEmail(controlador: _email, validarFormato: false),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _senha,
                        decoration: const InputDecoration(labelText: 'Senha'),
                        obscureText: true,
                        autofillHints: const [AutofillHints.password],
                        textInputAction: TextInputAction.done,
                        onFieldSubmitted: (_) => _entrar(),
                        validator: (valor) =>
                            (valor == null || valor.trim().isEmpty)
                            ? 'Informe a senha.'
                            : null,
                      ),
                      MensagemDeErro(_erro),
                      const SizedBox(height: 24),
                      BotaoEnviar(
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
  // Mesmos limites do RegistroRequest do backend: no mínimo 8 caracteres e no máximo 72 bytes em
  // UTF-8, o limite do BCrypt (letras acentuadas ocupam 2 bytes).
  static const _senhaMinima = 8;
  static const _senhaMaximaEmBytes = 72;

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

    String? erro;
    var entrou = false;
    try {
      erro = await _erroDe(
        () => api.registrar(_nome.text.trim(), email, senha),
      );
      if (erro == null) {
        erro = await _erroDe(() => api.login(email, senha));
        if (erro == null) {
          _entrouCom(aoEntrar);
          entrou = true;
        } else {
          // Tentar de novo aqui daria "E-mail já cadastrado": a conta existe, falta só entrar.
          erro =
              'Conta criada, mas não deu para entrar agora. '
              'Volte e entre com seu e-mail e senha.';
        }
      }
    } finally {
      // Depois de entrar, o botão segue carregando enquanto a tela sai.
      if (mounted && !entrou) {
        setState(() {
          _enviando = false;
          _erro = erro;
        });
      }
    }
  }

  String? _validarSenha(String? valor) {
    if (valor == null || valor.trim().isEmpty) return 'Informe a senha.';
    if (valor.length < _senhaMinima) {
      return 'A senha precisa ter pelo menos $_senhaMinima caracteres.';
    }
    if (utf8.encode(valor).length > _senhaMaximaEmBytes) {
      return 'A senha pode ter no máximo $_senhaMaximaEmBytes bytes (letras acentuadas contam 2).';
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
              child: AutofillGroup(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextFormField(
                      controller: _nome,
                      decoration: const InputDecoration(labelText: 'Nome'),
                      textCapitalization: TextCapitalization.words,
                      autofillHints: const [AutofillHints.name],
                      textInputAction: TextInputAction.next,
                      validator: (valor) {
                        final nome = valor?.trim() ?? '';
                        if (nome.isEmpty) return 'Informe seu nome.';
                        if (nome.length > _tamanhoMaximo) {
                          return 'O nome pode ter no máximo $_tamanhoMaximo caracteres.';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    _CampoEmail(controlador: _email, validarFormato: true),
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
                    MensagemDeErro(_erro),
                    const SizedBox(height: 24),
                    BotaoEnviar(
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
      ),
    );
  }
}

/// Roda [acao] e devolve a mensagem para mostrar ao usuário, ou null se deu certo.
Future<String?> _erroDe(Future<void> Function() acao) async {
  try {
    await acao();
    return null;
  } catch (e) {
    return textoDoErro(e);
  }
}

/// Pede ao sistema para oferecer salvar e-mail e senha no gerenciador de senhas, e entra.
void _entrouCom(VoidCallback aoEntrar) {
  TextInput.finishAutofillContext();
  aoEntrar();
}

// Limite de nome e e-mail no RegistroRequest do backend.
const _tamanhoMaximo = 255;

// Forma básica (algo@algo.algo). A validação de verdade é a do backend, cuja mensagem aparece em
// MensagemDeErro.
final _formatoDeEmail = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');

class _CampoEmail extends StatelessWidget {
  const _CampoEmail({required this.controlador, required this.validarFormato});

  final TextEditingController controlador;

  /// Forma e tamanho só valem no cadastro; no login, basta o e-mail estar preenchido.
  final bool validarFormato;

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
        if (!validarFormato) return null;
        if (!_formatoDeEmail.hasMatch(email)) return 'E-mail inválido.';
        if (email.length > _tamanhoMaximo) {
          return 'O e-mail pode ter no máximo $_tamanhoMaximo caracteres.';
        }
        return null;
      },
    );
  }
}
