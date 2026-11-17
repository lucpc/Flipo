import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../widgets/coluna_central.dart';
import '../widgets/mensagem_de_erro.dart';
import 'editar_cartao.dart';

/// Gerenciar cartões (docs/01-fluxos-de-usuario.md, seção 3): todos os cartões da matéria, os
/// ativos e depois os arquivados, cada grupo com o seu título. Tocar num cartão abre a edição
/// (seção 6), e o que ela devolve já entra na lista, sem buscar de novo.
///
/// Fecha devolvendo o aviso de uma edição para a tela da matéria (ex.: "Cartão arquivado —
/// agora está no Arquivo"), ou null se nenhuma deixou aviso que ainda vale.
class TelaGerenciarCartoes extends StatefulWidget {
  const TelaGerenciarCartoes({
    super.key,
    required this.api,
    required this.materia,
  });

  final ApiClient api;
  final Materia materia;

  @override
  State<TelaGerenciarCartoes> createState() => _TelaGerenciarCartoesState();
}

class _TelaGerenciarCartoesState extends State<TelaGerenciarCartoes> {
  // Os ativos e depois os arquivados. null enquanto carrega ou depois de uma falha.
  List<Cartao>? _cartoes;
  String? _erro;
  // O aviso e o cartão que o deixou. Uma edição depois, do mesmo cartão, o troca ou o tira (ex.:
  // arquivou e depois desarquivou); a de outro cartão sem aviso não mexe nele.
  String? _aviso;
  String? _avisoDe;

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    if (_erro != null) setState(() => _erro = null);
    try {
      final id = widget.materia.id;
      final listas = await Future.wait([
        widget.api.listarCartoes(id),
        widget.api.listarCartoes(id, arquivados: true),
      ]);
      if (!mounted) return;
      setState(() => _cartoes = [...listas[0], ...listas[1]]);
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted) return;
      setState(() => _erro = textoDoErro(e));
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
    setState(() {
      _cartoes = resultado.aplicarEm(cartoes);
      if (resultado.aviso != null || resultado.id == _avisoDe) {
        _aviso = resultado.aviso;
        _avisoDe = resultado.id;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    // Com aviso, voltar o leva junto; sem aviso, volta normal.
    return PopScope<String>(
      canPop: _aviso == null,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) Navigator.of(context).pop(_aviso);
      },
      child: Scaffold(
        appBar: AppBar(title: const Text('Gerenciar cartões')),
        body: SafeArea(
          child: ColunaCentral(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 8, 24, 0),
                  child: Text(widget.materia.nome, style: _textoDeApoio(tema)),
                ),
                Expanded(child: _conteudo(tema)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _conteudo(ThemeData tema) {
    final erro = _erro;
    final cartoes = _cartoes;
    if (erro != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              MensagemDeErro(erro),
              const SizedBox(height: 16),
              OutlinedButton(
                onPressed: _carregar,
                child: const Text('Tentar de novo'),
              ),
            ],
          ),
        ),
      );
    }
    if (cartoes == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (cartoes.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            'Nenhum cartão nesta matéria',
            textAlign: TextAlign.center,
            style: _textoDeApoio(tema),
          ),
        ),
      );
    }
    final ativos = cartoes.where((c) => !c.arquivado).toList();
    final arquivados = cartoes.where((c) => c.arquivado).toList();
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (ativos.isNotEmpty)
          _Grupo(_contagem(ativos.length, 'ativo', 'ativos')),
        for (final cartao in ativos)
          _ItemCartao(cartao, aoTocar: () => _editar(cartao)),
        if (arquivados.isNotEmpty)
          _Grupo(_contagem(arquivados.length, 'arquivado', 'arquivados')),
        for (final cartao in arquivados)
          _ItemCartao(cartao, aoTocar: () => _editar(cartao)),
      ],
    );
  }
}

/// Título de um grupo da lista, ex.: "6 ativos".
class _Grupo extends StatelessWidget {
  const _Grupo(this.titulo);

  final String titulo;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 16, 8, 8),
      child: Semantics(
        header: true,
        child: Text(titulo, style: Theme.of(context).textTheme.titleSmall),
      ),
    );
  }
}

/// Um cartão da lista: pergunta e resposta, com o ícone do estado. O arquivado usa o do Arquivo,
/// na íris, o acento que a revisão do Arquivo também usa.
class _ItemCartao extends StatelessWidget {
  const _ItemCartao(this.cartao, {required this.aoTocar});

  final Cartao cartao;
  final VoidCallback aoTocar;

  @override
  Widget build(BuildContext context) {
    final cores = Theme.of(context).colorScheme;
    return Card(
      child: ListTile(
        leading: cartao.arquivado
            ? Icon(
                Icons.inventory_2_outlined,
                color: cores.tertiary,
                semanticLabel: 'arquivado',
              )
            : Icon(
                Icons.style_outlined,
                color: cores.primary,
                semanticLabel: 'ativo',
              ),
        title: Text(
          cartao.pergunta,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: Text(
          cartao.resposta,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: aoTocar,
      ),
    );
  }
}

String _contagem(int n, String singular, String plural) =>
    '$n ${n == 1 ? singular : plural}';

TextStyle? _textoDeApoio(ThemeData tema) => tema.textTheme.bodyMedium?.copyWith(
  color: tema.colorScheme.onSurfaceVariant,
);
