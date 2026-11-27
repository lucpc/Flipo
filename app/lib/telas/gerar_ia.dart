import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../widgets/botao_enviar.dart';
import '../widgets/coluna_central.dart';
import '../widgets/mensagem_de_erro.dart';
import 'revisao.dart';

/// Limite do texto do `gerar-ia`, em unidades UTF-16 (docs/03-contrato-api.md): o `String.length`
/// do Dart, que conta um emoji como 2, igual ao backend.
const limiteDoTexto = 20000;

const _quantidadeMinima = 1;
const _quantidadeMaxima = 20;

/// Gerar com IA (docs/01-fluxos-de-usuario.md, seção 4b, quadros 04c e 04d do Figma): o texto
/// colado, a quantidade e o nível; enquanto a IA gera, a tela de progresso (04d). As sugestões
/// seguem para a Revisão, que só grava (pelo `lote`) o que o usuário aceitar. Fecha devolvendo o
/// aviso da Revisão para a tela da matéria; sem salvar, o usuário volta a este formulário como o
/// deixou.
class TelaGerarIa extends StatefulWidget {
  const TelaGerarIa({super.key, required this.api, required this.materiaId});

  final ApiClient api;
  final String materiaId;

  @override
  State<TelaGerarIa> createState() => _TelaGerarIaState();
}

class _TelaGerarIaState extends State<TelaGerarIa> {
  // Fica aqui, e não no campo, para o texto sobreviver à tela de progresso.
  final _texto = TextEditingController();
  var _quantidade = 10;
  var _nivel = NivelDeCartao.definicao;
  bool _gerando = false;
  String? _erro;
  // Muda a cada geração e quando o usuário volta durante uma: a resposta de uma geração que não é
  // mais a atual é ignorada.
  var _geracao = 0;

  @override
  void initState() {
    super.initState();
    // O contador e o botão acompanham o texto.
    _texto.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _texto.dispose();
    super.dispose();
  }

  Future<void> _gerar() async {
    final texto = _texto.text.trim();
    if (_gerando || texto.isEmpty) return;
    final geracao = ++_geracao;
    setState(() {
      _gerando = true;
      _erro = null;
    });

    final List<PerguntaEResposta> sugestoes;
    try {
      sugestoes = await widget.api.gerarIa(
        widget.materiaId,
        texto,
        _quantidade,
        _nivel,
      );
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted || geracao != _geracao) return;
      setState(() {
        _gerando = false;
        _erro = _textoDoErro(e);
      });
      return;
    }
    if (!mounted || geracao != _geracao) return;
    setState(() => _gerando = false);

    final aviso = await Navigator.of(context).push<String>(
      MaterialPageRoute(
        builder: (_) => TelaRevisao(
          sugestoes: [
            for (final s in sugestoes)
              SugestaoDeCartao(
                pergunta: s.pergunta,
                resposta: s.resposta,
                origem: 'ia',
              ),
          ],
          salvar: (aceitos) => widget.api.criarCartoesEmLote(widget.materiaId, [
            for (final c in aceitos)
              (pergunta: c.pergunta, resposta: c.resposta),
          ]),
        ),
      ),
    );
    // Salvou: volta direto para a matéria. Sem aviso, o usuário voltou da Revisão para cá.
    if (aviso != null && mounted) Navigator.of(context).pop(aviso);
  }

  /// Voltar durante a geração a cancela: volta ao formulário, e a resposta não tem efeito.
  void _cancelar() => setState(() {
    _geracao++;
    _gerando = false;
  });

  String _textoDoErro(Object erro) => switch (erro) {
    // 504: um proxy na frente do backend pode desistir antes dele (a geração leva até ~2 min).
    ApiException(status: 502 || 504) =>
      'A IA não conseguiu gerar os cartões desta vez. Tente de novo.',
    ApiException(status: 503) =>
      'A geração por IA não está disponível no servidor no momento.',
    ApiException(status: 404) =>
      'Matéria não encontrada. Ela pode ter sido apagada.',
    _ => textoDoErro(erro),
  };

  @override
  Widget build(BuildContext context) {
    return PopScope<Object?>(
      canPop: !_gerando,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _cancelar();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(_gerando ? 'Gerando sugestões' : 'Gerar com IA'),
        ),
        body: SafeArea(
          child: ColunaCentral(
            child: _gerando ? const _Progresso() : _formulario(context),
          ),
        ),
      ),
    );
  }

  Widget _formulario(BuildContext context) {
    final tema = Theme.of(context);
    final caracteres = _texto.text.length;
    final noLimite = caracteres >= limiteDoTexto;
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('A partir de um texto colado', style: _textoDeApoio(tema)),
          const SizedBox(height: 16),
          TextField(
            controller: _texto,
            decoration: InputDecoration(
              labelText: 'Texto',
              hintText: 'Cole aqui o material de estudo',
              alignLabelWithHint: true,
              counterText:
                  '${_milhares(caracteres)} / ${_milhares(limiteDoTexto)}',
              counterStyle: noLimite
                  ? tema.inputDecorationTheme.errorStyle
                  : null,
            ),
            keyboardType: TextInputType.multiline,
            minLines: 6,
            maxLines: 12,
            textCapitalization: TextCapitalization.sentences,
            inputFormatters: const [_LimiteUtf16(limiteDoTexto)],
          ),
          const SizedBox(height: 24),
          Text('Quantidade de cartões', style: tema.textTheme.titleSmall),
          const SizedBox(height: 8),
          Row(
            children: [
              IconButton.outlined(
                onPressed: _quantidade > _quantidadeMinima
                    ? () => setState(() => _quantidade--)
                    : null,
                tooltip: 'Menos cartões',
                icon: const Icon(Icons.remove),
              ),
              SizedBox(
                width: 56,
                child: Text(
                  '$_quantidade',
                  textAlign: TextAlign.center,
                  style: tema.textTheme.titleLarge,
                ),
              ),
              IconButton.outlined(
                onPressed: _quantidade < _quantidadeMaxima
                    ? () => setState(() => _quantidade++)
                    : null,
                tooltip: 'Mais cartões',
                icon: const Icon(Icons.add),
              ),
            ],
          ),
          const SizedBox(height: 24),
          Text('Nível', style: tema.textTheme.titleSmall),
          const SizedBox(height: 8),
          SegmentedButton<NivelDeCartao>(
            segments: const [
              ButtonSegment(
                value: NivelDeCartao.definicao,
                label: Text('Definição simples'),
              ),
              ButtonSegment(
                value: NivelDeCartao.aplicacao,
                label: Text('Aplicação'),
              ),
            ],
            selected: {_nivel},
            onSelectionChanged: (nivel) =>
                setState(() => _nivel = nivel.single),
          ),
          MensagemDeErro(_erro),
          const SizedBox(height: 24),
          Text(
            'Você revisa cada sugestão antes de salvar.',
            textAlign: TextAlign.center,
            style: _textoDeApoio(tema),
          ),
          const SizedBox(height: 12),
          BotaoEnviar(
            rotulo: 'Gerar sugestões',
            icone: Icons.auto_awesome_outlined,
            enviando: false,
            aoPressionar: _texto.text.trim().isEmpty ? null : _gerar,
          ),
        ],
      ),
    );
  }
}

/// Gerando sugestões (quadro 04d).
class _Progresso extends StatelessWidget {
  const _Progresso();

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 24),
            Semantics(
              liveRegion: true,
              child: Text(
                'A IA está lendo o seu texto. Nada será salvo até você '
                'revisar cada cartão.',
                textAlign: TextAlign.center,
                style: tema.textTheme.bodyLarge,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Impede o texto de passar de [limite] unidades UTF-16. O `maxLength` do Flutter conta
/// caracteres visíveis (um emoji conta 1), e o backend recusaria um texto que ele deixa passar.
/// O que excede sai do fim do trecho colado ou digitado, recuando até o início de um caractere
/// visível (emoji, bandeira, sequência com ZWJ) para não deixar um pedaço solto; o resto do texto
/// fica como estava. A composição do IME em andamento é mantida, recortada no mesmo ponto.
class _LimiteUtf16 extends TextInputFormatter {
  const _LimiteUtf16(this.limite);

  final int limite;

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue antigo,
    TextEditingValue novo,
  ) {
    final texto = novo.text;
    if (texto.length <= limite) return novo;
    // O trecho inserido termina no cursor.
    final cursor = novo.selection.isValid ? novo.selection.end : texto.length;
    final maximo = cursor - (texto.length - limite);
    if (maximo < 0) return antigo;
    var corte = 0;
    for (final caractere in texto.substring(0, cursor).characters) {
      if (corte + caractere.length > maximo) break;
      corte += caractere.length;
    }
    final composicao = novo.composing;
    return TextEditingValue(
      text: texto.substring(0, corte) + texto.substring(cursor),
      selection: TextSelection.collapsed(offset: corte),
      composing: composicao.isValid && composicao.start < corte
          ? TextRange(
              start: composicao.start,
              end: composicao.end < corte ? composicao.end : corte,
            )
          : TextRange.empty,
    );
  }
}

/// 20000 → "20.000".
String _milhares(int n) =>
    '$n'.replaceAllMapped(RegExp(r'\B(?=(\d{3})+$)'), (_) => '.');

TextStyle? _textoDeApoio(ThemeData tema) => tema.textTheme.bodyMedium?.copyWith(
  color: tema.colorScheme.onSurfaceVariant,
);
