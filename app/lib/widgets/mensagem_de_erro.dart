import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import '../api/api_client.dart';

/// Mensagem para mostrar ao usuário quando uma chamada à API falha.
String textoDoErro(Object erro) => switch (erro) {
  ApiException(:final mensagem) => mensagem,
  http.ClientException() => 'Não foi possível conectar ao servidor.',
  // Ex.: falha de TLS, resposta que não é JSON, erro do armazenamento seguro. Sem isto, a tela
  // ficaria carregando para sempre.
  _ => 'Algo deu errado. Tente de novo.',
};

/// Texto de erro abaixo de um formulário ou no lugar de um conteúdo que não carregou.
class MensagemDeErro extends StatelessWidget {
  const MensagemDeErro(this.mensagem, {super.key});

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
