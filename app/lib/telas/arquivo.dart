import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../widgets/aviso.dart';
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
  static const _textoDoAviso = 'Cartão devolvido para os ativos';
  static const _duracaoDoAviso = Duration(seconds: 4);

  // null enquanto carrega ou depois de uma falha.
  List<Cartao>? _cartoes;
  String? _erro;
  // Por id: o usuário pode avançar enquanto um cartão desarquiva, e desarquivar o seguinte.
  final _desarquivando = <String>{};
  // A falha fica com o cartão dela: se o usuário já avançou, aparece quando ele voltar a esse
  // cartão, que segue no Arquivo.
  final _falhas = <String, String>{};
  // O usuário pediu para sair com um desarquivar em andamento: a tela fecha quando ele terminar.
  var _sairAoTerminar = false;
  // O aviso do quadro 07c, que some sozinho depois de [_duracaoDoAviso].
  var _avisoVisivel = false;
  Timer? _timerDoAviso;

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  @override
  void dispose() {
    _timerDoAviso?.cancel();
    super.dispose();
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

  // Chamar dentro de um setState.
  void _mostrarAviso() {
    _avisoVisivel = true;
    _timerDoAviso?.cancel();
    _timerDoAviso = Timer(_duracaoDoAviso, () {
      if (mounted) setState(() => _avisoVisivel = false);
    });
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
        _mostrarAviso();
      });
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted) return;
      setState(() {
        _desarquivando.remove(cartao.id);
        _falhas[cartao.id] = textoDoErro(e);
      });
    }
    _sairSePedido();
  }

  // Fecha a tela pedida enquanto desarquivava, quando o último desarquivar termina, dê certo ou
  // não: os totais da matéria já são os novos. Só se esta tela está no topo: num 401 o app já a
  // tirou para voltar ao login; com a edição aberta por cima, o usuário mudou de ideia.
  void _sairSePedido() {
    if (!mounted || !_sairAoTerminar || _desarquivando.isNotEmpty) return;
    _sairAoTerminar = false;
    if (ModalRoute.of(context)?.isCurrent ?? false) Navigator.of(context).pop();
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
    setState(() {
      _cartoes = resultado.aplicarEm(cartoes, arquivados: true);
      if (resultado is CartaoDesarquivado) _mostrarAviso();
    });
  }

  Widget _botaoDeEditar(Cartao cartao) {
    final cores = Theme.of(context).colorScheme;
    return IconButton(
      tooltip: 'Editar cartão',
      onPressed: _desarquivando.contains(cartao.id)
          ? null
          : () => _editar(cartao),
      style: IconButton.styleFrom(
        backgroundColor: cores.surfaceContainerHighest,
        foregroundColor: cores.onSurfaceVariant,
      ),
      icon: const Icon(Icons.more_vert),
    );
  }

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
          icon: enviando ? null : const Icon(Icons.replay),
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
      // Enquanto desarquiva, qualquer saída (o voltar do AppBar, o do sistema ou "Voltar para a
      // matéria") espera: a matéria recarregaria os totais antes da mudança.
      return PopScope(
        canPop: _desarquivando.isEmpty,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) _sairAoTerminar = true;
        },
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
          rotuloDeRecomecar: 'Revisar de novo',
          corDaBorda: Theme.of(context).colorScheme.tertiary,
          aviso: _avisoVisivel ? const Aviso(_textoDoAviso) : null,
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
