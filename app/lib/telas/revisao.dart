import 'package:flutter/material.dart';

import '../widgets/botao_enviar.dart';
import '../widgets/coluna_central.dart';
import '../widgets/formulario_cartao.dart';
import '../widgets/mensagem_de_erro.dart';

/// Um cartão ainda não salvo, à espera da revisão: o digitado na criação manual ou uma sugestão
/// da IA.
class SugestaoDeCartao {
  const SugestaoDeCartao({
    required this.pergunta,
    required this.resposta,
    required this.origem,
  });

  final String pergunta;
  final String resposta;

  /// `manual` ou `ia`, como o campo `origem` da API.
  final String origem;
}

/// Grava os [aceitos] (na ordem da tela) de uma vez: tudo ou nada. Se falha, lança o erro e nada
/// foi salvo. A criação manual tem sempre um cartão só (`POST .../cartoes`); a da IA usa o
/// `POST .../cartoes/lote`.
typedef SalvarCartoes = Future<void> Function(List<SugestaoDeCartao> aceitos);

enum _Estado {
  pendente('pendente'),
  aceito('aceito'),
  editado('editado e aceito'),
  descartado('descartado');

  const _Estado(this.rotulo);

  final String rotulo;

  bool get foiAceito => this == _Estado.aceito || this == _Estado.editado;
}

class _Item {
  _Item(this.sugestao, this.estado);

  SugestaoDeCartao sugestao;
  _Estado estado;
}

/// Revisão (docs/01-fluxos-de-usuario.md, seção 4c, quadros 04b, 04e e 04f do Figma), comum à
/// criação manual e à geração por IA. Nada é salvo antes de "Confirmar revisão", e só os aceitos.
/// Fecha devolvendo o aviso para a tela da matéria ("N cartões salvos em Estudar cartões"), ou
/// null se nada foi salvo.
///
/// Os padrões são os das sugestões da IA (04e). A criação manual (04b) troca o cabeçalho e já
/// abre com o cartão aceito: quem acabou de digitá-lo não precisa aceitar de novo.
class TelaRevisao extends StatefulWidget {
  const TelaRevisao({
    super.key,
    required this.sugestoes,
    required this.salvar,
    this.titulo = 'Revisar sugestões',
    this.lembrete = 'Nada foi salvo ainda — aceite, edite ou descarte',
    this.comecarAceitas = false,
  });

  final List<SugestaoDeCartao> sugestoes;
  final SalvarCartoes salvar;

  /// Título do cabeçalho, seguido de quantos cartões há na tela: "Revisar sugestões (N)".
  final String titulo;

  /// Texto de apoio abaixo do título.
  final String lembrete;

  /// Se as sugestões entram aceitas em vez de pendentes.
  final bool comecarAceitas;

  @override
  State<TelaRevisao> createState() => _TelaRevisaoState();
}

class _TelaRevisaoState extends State<TelaRevisao> {
  late final _itens = [
    for (final s in widget.sugestoes)
      _Item(s, widget.comecarAceitas ? _Estado.aceito : _Estado.pendente),
  ];
  bool _salvando = false;
  String? _erro;

  int _contar(bool Function(_Estado) teste) =>
      _itens.where((i) => teste(i.estado)).length;

  Future<void> _editar(_Item item) async {
    final editado = await Navigator.of(context).push<SugestaoDeCartao>(
      MaterialPageRoute(builder: (_) => _TelaEditarSugestao(item.sugestao)),
    );
    if (editado == null || !mounted) return;
    setState(() {
      item.sugestao = editado;
      item.estado = _Estado.editado;
    });
  }

  Future<void> _confirmar() async {
    final aceitos = [
      for (final i in _itens)
        if (i.estado.foiAceito) i.sugestao,
    ];
    if (_salvando || aceitos.isEmpty) return;
    setState(() {
      _salvando = true;
      _erro = null;
    });

    try {
      await widget.salvar(aceitos);
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted) return;
      // Nada foi salvo: os cartões seguem aceitos, prontos para tentar de novo.
      setState(() {
        _salvando = false;
        _erro = textoDoErro(e);
      });
      return;
    }
    if (!mounted) return;
    Navigator.of(context).pop(
      '${_contagem(aceitos.length, 'cartão salvo', 'cartões salvos')} '
      'em Estudar cartões',
    );
  }

  /// Voltar: com aceitos ainda não salvos, confirma antes, porque a revisão se perde.
  Future<void> _sair() async {
    if (_salvando) return;
    if (_contar((e) => e.foiAceito) > 0) {
      final sair = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Sair sem salvar?'),
          content: const Text(
            'Os cartões aceitos ainda não foram salvos e vão se perder.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Continuar revisando'),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Sair'),
            ),
          ],
        ),
      );
      // null: fechou o diálogo tocando fora, ou um 401 fechou tudo.
      if (sair != true || !mounted) return;
    }
    Navigator.of(context).pop();
  }

  String _resumo() => [
    (_contar((e) => e.foiAceito), 'aceito', 'aceitos'),
    (_contar((e) => e == _Estado.descartado), 'descartado', 'descartados'),
    (_contar((e) => e == _Estado.pendente), 'pendente', 'pendentes'),
  ].where((p) => p.$1 > 0).map((p) => _contagem(p.$1, p.$2, p.$3)).join(' · ');

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final aceitos = _contar((e) => e.foiAceito);
    return PopScope<String>(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _sair();
      },
      child: Scaffold(
        appBar: AppBar(title: Text('${widget.titulo} (${_itens.length})')),
        body: SafeArea(
          child: ColunaCentral(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 8, 24, 0),
                  child: Text(widget.lembrete, style: _textoDeApoio(tema)),
                ),
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      for (final item in _itens)
                        _CartaoSugestao(
                          item: item,
                          aoAceitar: _salvando || item.estado.foiAceito
                              ? null
                              : () => setState(
                                  () => item.estado = _Estado.aceito,
                                ),
                          aoEditar: _salvando ? null : () => _editar(item),
                          aoDescartar:
                              _salvando || item.estado == _Estado.descartado
                              ? null
                              : () => setState(
                                  () => item.estado = _Estado.descartado,
                                ),
                        ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        _resumo(),
                        textAlign: TextAlign.center,
                        style: _textoDeApoio(tema),
                      ),
                      MensagemDeErro(_erro),
                      const SizedBox(height: 16),
                      BotaoEnviar(
                        rotulo:
                            'Confirmar revisão '
                            '(${_contagem(aceitos, 'cartão', 'cartões')})',
                        enviando: _salvando,
                        aoPressionar: aceitos == 0 ? null : _confirmar,
                      ),
                    ],
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

/// Um cartão da Revisão: origem, estado, pergunta, resposta e as três ações. Ação sem callback
/// fica desabilitada.
class _CartaoSugestao extends StatelessWidget {
  const _CartaoSugestao({
    required this.item,
    required this.aoAceitar,
    required this.aoEditar,
    required this.aoDescartar,
  });

  final _Item item;
  final VoidCallback? aoAceitar;
  final VoidCallback? aoEditar;
  final VoidCallback? aoDescartar;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final cores = tema.colorScheme;
    final sugestao = item.sugestao;
    final estado = item.estado;
    final descartado = estado == _Estado.descartado;
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 8, 4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _Etiqueta(
                  sugestao.origem == 'ia' ? 'IA' : 'manual',
                  cor: cores.onSurfaceVariant,
                ),
                const SizedBox(width: 8),
                _Etiqueta(
                  estado.rotulo,
                  cor: estado.foiAceito
                      ? cores.primary
                      : cores.onSurfaceVariant,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    sugestao.pergunta,
                    style: tema.textTheme.titleMedium?.copyWith(
                      color: descartado ? cores.onSurfaceVariant : null,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    sugestao.resposta,
                    style: descartado
                        ? _textoDeApoio(tema)
                        : tema.textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
            OverflowBar(
              alignment: MainAxisAlignment.end,
              children: [
                // Já aceito, fica desabilitado mas preenchido, e o leitor de tela o anuncia como
                // selecionado. O MergeSemantics põe o "selecionado" no próprio nó do botão.
                MergeSemantics(
                  child: Semantics(
                    selected: estado.foiAceito,
                    child: TextButton.icon(
                      onPressed: aoAceitar,
                      style: estado.foiAceito
                          ? TextButton.styleFrom(
                              disabledBackgroundColor: cores.primary,
                              disabledForegroundColor: cores.onPrimary,
                            )
                          : null,
                      icon: const Icon(Icons.check),
                      label: const Text('Aceitar'),
                    ),
                  ),
                ),
                TextButton.icon(
                  onPressed: aoEditar,
                  icon: const Icon(Icons.edit_outlined),
                  label: const Text('Editar'),
                ),
                TextButton.icon(
                  onPressed: aoDescartar,
                  icon: const Icon(Icons.close),
                  label: const Text('Descartar'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Selo pequeno com borda: a origem e o estado de cada cartão.
class _Etiqueta extends StatelessWidget {
  const _Etiqueta(this.texto, {required this.cor});

  final String texto;
  final Color cor;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        border: Border.all(color: cor),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        texto,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(color: cor),
      ),
    );
  }
}

/// Edição de uma sugestão (quadro 04f), com o mesmo formulário da criação manual. Fecha devolvendo
/// a sugestão com o texto novo; nada é salvo aqui.
class _TelaEditarSugestao extends StatelessWidget {
  const _TelaEditarSugestao(this.sugestao);

  final SugestaoDeCartao sugestao;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Editar cartão')),
      body: SafeArea(
        child: ColunaCentral(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: FormularioCartao(
              pergunta: sugestao.pergunta,
              resposta: sugestao.resposta,
              rotulo: 'Salvar',
              aoConfirmar: (pergunta, resposta) => Navigator.of(context).pop(
                SugestaoDeCartao(
                  pergunta: pergunta,
                  resposta: resposta,
                  origem: sugestao.origem,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

String _contagem(int n, String singular, String plural) =>
    '$n ${n == 1 ? singular : plural}';

TextStyle? _textoDeApoio(ThemeData tema) => tema.textTheme.bodyMedium?.copyWith(
  color: tema.colorScheme.onSurfaceVariant,
);
