import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../widgets/baralho.dart';
import '../widgets/coluna_central.dart';
import '../widgets/mensagem_de_erro.dart';
import 'adicionar_cartao.dart';
import 'editar_cartao.dart';

/// Sessão de estudo (docs/01-fluxos-de-usuario.md, seção 5, quadros 05a–05e): busca só os cartões
/// ativos e entrega a lista ao [Baralho]. A sessão em si não grava nada: o ⋮ do cartão abre a
/// edição (seção 6), e é ela que grava.
///
/// Fecha devolvendo o aviso de um cartão arquivado na sessão para a tela da matéria, ou null se
/// nenhum foi arquivado.
class TelaEstudo extends StatefulWidget {
  const TelaEstudo({super.key, required this.api, required this.materia});

  final ApiClient api;
  final Materia materia;

  @override
  State<TelaEstudo> createState() => _TelaEstudoState();
}

class _TelaEstudoState extends State<TelaEstudo> {
  // null enquanto carrega ou depois de uma falha.
  List<Cartao>? _cartoes;
  String? _erro;
  // Alguma edição já voltou para a sessão, ou seja, o baralho já estava na tela. Só conta com a
  // lista vazia: aí a edição tirou o último cartão, e o baralho fica, com o fim, em vez da sessão
  // vazia, que é só para quando a matéria abre sem cartões ativos.
  var _editou = false;
  // Só arquivar deixa aviso. O cartão arquivado sai do baralho e não dá para desarquivá-lo daqui,
  // então nada depois o desfaz.
  String? _aviso;

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    if (_erro != null) setState(() => _erro = null);
    try {
      final cartoes = await widget.api.listarCartoes(widget.materia.id);
      if (!mounted) return;
      setState(() => _cartoes = cartoes);
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted) return;
      setState(() => _erro = textoDoErro(e));
    }
  }

  // Atalho da sessão vazia: adiciona e, se algo foi salvo, recarrega para já estudar o cartão novo.
  Future<void> _adicionarCartao() async {
    final aviso = await Navigator.of(context).push<String>(
      MaterialPageRoute(
        builder: (_) =>
            TelaAdicionarCartao(api: widget.api, materiaId: widget.materia.id),
      ),
    );
    if (!mounted || aviso == null) return;
    setState(() => _cartoes = null);
    await _carregar();
  }

  // O baralho segue o cartão atual pelo id: editado, fica no lugar; arquivado ou excluído, sai, e
  // a mesma posição mostra o próximo.
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
    setState(() {
      _cartoes = resultado.aplicarEm(cartoes, arquivados: false);
      _editou = true;
      _aviso = resultado.aviso ?? _aviso;
    });
  }

  @override
  Widget build(BuildContext context) {
    // Com aviso, qualquer saída (o voltar, "Voltar para a matéria" ou o do sistema) o leva junto.
    return PopScope<String>(
      canPop: _aviso == null,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) Navigator.of(context).pop(_aviso);
      },
      child: _conteudo(context),
    );
  }

  Widget _conteudo(BuildContext context) {
    final cartoes = _cartoes;
    if (cartoes != null && (cartoes.isNotEmpty || _editou)) {
      final cores = Theme.of(context).colorScheme;
      return Baralho(
        cartoes: cartoes,
        titulo: widget.materia.nome,
        posicao: (numero, total) => 'cartão $numero de $total',
        tituloDoFim: 'Você passou pelos cartões ativos',
        apoioDoFim: 'Sem nota, sem placar. Volte quando quiser.',
        cantoDoCartao: (cartao) => IconButton(
          tooltip: 'Editar cartão',
          onPressed: () => _editar(cartao),
          style: IconButton.styleFrom(
            backgroundColor: cores.surfaceContainerHighest,
            foregroundColor: cores.onSurfaceVariant,
          ),
          icon: const Icon(Icons.more_vert),
        ),
      );
    }
    final tema = Theme.of(context);
    final erro = _erro;
    return Scaffold(
      appBar: AppBar(title: Text(widget.materia.nome)),
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
                  : cartoes == null
                  ? const CircularProgressIndicator()
                  : Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          'Nenhum cartão ativo nesta matéria',
                          textAlign: TextAlign.center,
                          style: tema.textTheme.titleMedium,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Adicione cartões para começar a estudar.',
                          textAlign: TextAlign.center,
                          style: tema.textTheme.bodyMedium?.copyWith(
                            color: tema.colorScheme.onSurfaceVariant,
                          ),
                        ),
                        const SizedBox(height: 24),
                        FilledButton.icon(
                          onPressed: _adicionarCartao,
                          icon: const Icon(Icons.add),
                          label: const Text('Adicionar cartão'),
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
