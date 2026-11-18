import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../widgets/baralho.dart';
import '../widgets/botao_enviar.dart';
import '../widgets/coluna_central.dart';
import '../widgets/mensagem_de_erro.dart';
import 'editar_cartao.dart';

/// Revisão do Arquivo (docs/01-fluxos-de-usuario.md, seção 7, quadros 07a–07d): busca só os
/// cartões arquivados e entrega a lista ao [Baralho], como a sessão de estudo. "Desarquivar este
/// cartão" devolve o cartão atual aos ativos e o tira do baralho; é sempre um toque deliberado,
/// cartão a cartão. Pelo ⋮ se abre a edição, de onde também se exclui o cartão.
class TelaArquivo extends StatefulWidget {
  const TelaArquivo({super.key, required this.api, required this.materia});

  final ApiClient api;
  final Materia materia;

  @override
  State<TelaArquivo> createState() => _TelaArquivoState();
}

class _TelaArquivoState extends State<TelaArquivo> {
  // null enquanto carrega ou depois de uma falha.
  List<Cartao>? _cartoes;
  String? _erro;
  // Por id: o usuário pode avançar enquanto um cartão desarquiva, e desarquivar o seguinte.
  final _desarquivando = <String>{};
  // A falha fica com o cartão dela: se o usuário já avançou, aparece quando ele voltar a esse
  // cartão, que segue no Arquivo.
  final _falhas = <String, String>{};

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    if (_erro != null) setState(() => _erro = null);
    try {
      final cartoes = await widget.api.listarCartoes(
        widget.materia.id,
        arquivados: true,
      );
      if (!mounted) return;
      setState(() => _cartoes = cartoes);
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted) return;
      setState(() => _erro = textoDoErro(e));
    }
  }

  // Tira o cartão pelo id, e não pela posição: se o usuário avançou enquanto a requisição ia, o
  // [Baralho] segue no cartão que está na tela.
  Future<void> _desarquivar(Cartao cartao) async {
    if (_desarquivando.contains(cartao.id)) return;
    setState(() {
      _desarquivando.add(cartao.id);
      _falhas.remove(cartao.id);
    });
    try {
      await widget.api.desarquivarCartao(cartao.id);
      if (!mounted) return;
      setState(() {
        _desarquivando.remove(cartao.id);
        _cartoes = [
          for (final c in _cartoes!)
            if (c.id != cartao.id) c,
        ];
      });
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted) return;
      setState(() {
        _desarquivando.remove(cartao.id);
        _falhas[cartao.id] = textoDoErro(e);
      });
    }
  }

  Future<void> _editar(Cartao cartao) async {
    final resultado = await Navigator.of(context).push<ResultadoDaEdicao>(
      MaterialPageRoute(
        builder: (_) => TelaEditarCartao(
          api: widget.api,
          nomeDaMateria: widget.materia.nome,
          cartao: cartao,
        ),
      ),
    );
    // Sem resultado: voltou sem gravar, ou um 401 fechou tudo.
    final cartoes = _cartoes;
    if (resultado == null || cartoes == null || !mounted) return;
    // Desarquivado ou excluído, sai do baralho; editado, fica com o texto novo.
    setState(() => _cartoes = resultado.aplicarEm(cartoes, arquivados: true));
  }

  Widget _botaoDeEditar(Cartao cartao) => IconButton(
    tooltip: 'Editar cartão',
    icon: const Icon(Icons.more_vert),
    onPressed: _desarquivando.contains(cartao.id)
        ? null
        : () => _editar(cartao),
  );

  Widget _botaoDeDesarquivar(Cartao cartao) {
    final cores = Theme.of(context).colorScheme;
    final enviando = _desarquivando.contains(cartao.id);
    const rotulo = 'Desarquivar este cartão';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Borda na íris, o acento do Arquivo; o rótulo no texto do tema, que tem contraste para
        // letra pequena.
        OutlinedButton.icon(
          onPressed: enviando ? null : () => _desarquivar(cartao),
          style: OutlinedButton.styleFrom(
            foregroundColor: cores.onSurface,
            side: BorderSide(color: cores.tertiary),
          ),
          icon: enviando ? null : const Icon(Icons.restore),
          label: enviando ? const IndicadorDeEnvio(rotulo) : const Text(rotulo),
        ),
        MensagemDeErro(_falhas[cartao.id]),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final cartoes = _cartoes;
    if (cartoes != null) {
      // Vazio: nada arquivado ao abrir, ou o último saiu (desarquivado ou excluído).
      final vazio = cartoes.isEmpty;
      // Enquanto desarquiva, não fecha: a matéria recarregaria os totais antes da mudança.
      return PopScope(
        canPop: _desarquivando.isEmpty,
        child: Baralho(
          cartoes: cartoes,
          titulo: 'Arquivo',
          posicao: (numero, total) =>
              '${widget.materia.nome} · cartão $numero de $total',
          tituloDoFim: vazio
              ? 'Nenhum cartão no Arquivo'
              : 'Você passou pelos cartões arquivados',
          apoioDoFim: vazio
              ? 'Os cartões desarquivados voltam para "Estudar cartões".'
              : 'Sem nota, sem placar. Volte quando quiser.',
          corDaBorda: Theme.of(context).colorScheme.tertiary,
          cantoDoCartao: _botaoDeEditar,
          acaoDoCartao: _botaoDeDesarquivar,
        ),
      );
    }
    final erro = _erro;
    return Scaffold(
      appBar: AppBar(title: const Text('Arquivo')),
      body: SafeArea(
        child: ColunaCentral(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: erro != null
                  ? Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        MensagemDeErro(erro),
                        const SizedBox(height: 16),
                        OutlinedButton(
                          onPressed: _carregar,
                          child: const Text('Tentar de novo'),
                        ),
                      ],
                    )
                  : const CircularProgressIndicator(),
            ),
          ),
        ),
      ),
    );
  }
}
