import 'package:flutter/material.dart';

/// Campos de pergunta e resposta de um cartão e o botão que os confirma. É o mesmo formulário na
/// criação manual (quadro 04a do Figma), na edição de uma sugestão da Revisão (04f) e na edição
/// de um cartão salvo (seção 6 de docs/01-fluxos-de-usuario.md), para a validação não divergir
/// entre os fluxos. Quem usa põe o formulário numa área que rola.
class FormularioCartao extends StatefulWidget {
  const FormularioCartao({
    super.key,
    this.pergunta = '',
    this.resposta = '',
    required this.rotulo,
    required this.aoConfirmar,
  });

  /// Valores iniciais dos campos.
  final String pergunta;
  final String resposta;

  /// Texto do botão.
  final String rotulo;

  /// Recebe os dois textos já sem espaços nas pontas, só quando ambos estão preenchidos.
  final void Function(String pergunta, String resposta) aoConfirmar;

  @override
  State<FormularioCartao> createState() => _FormularioCartaoState();
}

class _FormularioCartaoState extends State<FormularioCartao> {
  final _formulario = GlobalKey<FormState>();
  late final _pergunta = TextEditingController(text: widget.pergunta);
  late final _resposta = TextEditingController(text: widget.resposta);

  @override
  void dispose() {
    _pergunta.dispose();
    _resposta.dispose();
    super.dispose();
  }

  void _confirmar() {
    if (!_formulario.currentState!.validate()) return;
    widget.aoConfirmar(_pergunta.text.trim(), _resposta.text.trim());
  }

  // O CartaoRequest do backend só exige os dois preenchidos (@NotBlank): as colunas são `text`,
  // sem limite de tamanho.
  static String? Function(String?) _obrigatorio(String mensagem) =>
      (valor) => (valor == null || valor.trim().isEmpty) ? mensagem : null;

  @override
  Widget build(BuildContext context) {
    return Form(
      key: _formulario,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            controller: _pergunta,
            decoration: const InputDecoration(labelText: 'Pergunta'),
            keyboardType: TextInputType.multiline,
            minLines: 2,
            maxLines: null,
            textCapitalization: TextCapitalization.sentences,
            validator: _obrigatorio('Informe a pergunta.'),
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _resposta,
            decoration: const InputDecoration(labelText: 'Resposta'),
            keyboardType: TextInputType.multiline,
            minLines: 3,
            maxLines: null,
            textCapitalization: TextCapitalization.sentences,
            validator: _obrigatorio('Informe a resposta.'),
          ),
          const SizedBox(height: 24),
          FilledButton(onPressed: _confirmar, child: Text(widget.rotulo)),
        ],
      ),
    );
  }
}
