import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../widgets/baralho.dart';
import '../widgets/coluna_central.dart';
import '../widgets/mensagem_de_erro.dart';
import 'adicionar_cartao.dart';

/// Sessão de estudo (docs/01-fluxos-de-usuario.md, seção 5, quadros 05a–05e): busca só os cartões
/// ativos e entrega a lista ao [Baralho]. Nada é gravado durante a sessão.
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

  @override
  Widget build(BuildContext context) {
    final cartoes = _cartoes;
    if (cartoes != null && cartoes.isNotEmpty) {
      return Baralho(
        cartoes: cartoes,
        titulo: widget.materia.nome,
        posicao: (numero, total) => 'cartão $numero de $total',
        tituloDoFim: 'Você passou pelos cartões ativos',
        apoioDoFim: 'Sem nota, sem placar. Volte quando quiser.',
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
