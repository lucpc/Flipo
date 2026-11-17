import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'coluna_central.dart';

/// Baralho passivo (docs/01-fluxos-de-usuario.md, seções 5 e 7, quadros 05a–05e e 07a–07d):
/// tocar no cartão vira entre pergunta e resposta, "Próximo cartão" avança e, depois do último,
/// vem o fim. Sem avaliação de nenhum tipo: é decisão de produto fechada.
///
/// Serve à sessão de estudo e à revisão do Arquivo (#23), que troca o cabeçalho e a borda e põe
/// "Desarquivar este cartão" em [acaoDoCartao]. A lista é do dono da tela: se ele tirar dela o
/// cartão atual (ao desarquivar, arquivar ou excluir), a mesma posição passa a mostrar o próximo,
/// pela pergunta; se era o último, vem o fim; com a lista vazia, o fim não oferece recomeçar.
class Baralho extends StatefulWidget {
  const Baralho({
    super.key,
    required this.cartoes,
    required this.titulo,
    required this.posicao,
    required this.tituloDoFim,
    required this.apoioDoFim,
    this.corDaBorda,
    this.cantoDoCartao,
    this.acaoDoCartao,
  });

  final List<Cartao> cartoes;

  /// Primeira linha do cabeçalho, ex.: o nome da matéria, ou "Arquivo".
  final String titulo;

  /// Segunda linha do cabeçalho: a posição, que não é placar. Ex.: "cartão 1 de 6".
  final String Function(int numero, int total) posicao;

  final String tituloDoFim;
  final String apoioDoFim;

  /// Borda do cartão; sem ela, a discreta do tema. O Arquivo usa a terciária (íris).
  final Color? corDaBorda;

  /// Canto superior direito do cartão, para o ⋮ de edição (#13). Tocar nele não vira o cartão.
  final Widget Function(Cartao cartao)? cantoDoCartao;

  /// Ação logo abaixo do cartão, acima de "Próximo cartão". Ex.: "Desarquivar este cartão".
  final Widget Function(Cartao cartao)? acaoDoCartao;

  @override
  State<Baralho> createState() => _BaralhoState();
}

class _BaralhoState extends State<Baralho> {
  var _indice = 0;
  // O id, e não um bool: o cartão que chega à posição (avançando, ou porque o atual saiu da lista)
  // começa sempre pela pergunta.
  String? _viradoId;

  void _recomecar() => setState(() {
    _indice = 0;
    _viradoId = null;
  });

  @override
  Widget build(BuildContext context) {
    final cartoes = widget.cartoes;
    final atual = _indice < cartoes.length ? cartoes[_indice] : null;
    final tema = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(widget.titulo, overflow: TextOverflow.ellipsis),
            if (atual != null)
              Text(
                widget.posicao(_indice + 1, cartoes.length),
                style: tema.textTheme.bodyMedium?.copyWith(
                  color: tema.colorScheme.onSurfaceVariant,
                ),
              ),
          ],
        ),
      ),
      body: SafeArea(
        child: ColunaCentral(
          child: atual == null ? _fim(tema) : _cartao(atual),
        ),
      ),
    );
  }

  Widget _cartao(Cartao atual) {
    final virado = _viradoId == atual.id;
    final canto = widget.cantoDoCartao;
    final acao = widget.acaoDoCartao;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 16, 24, 0),
            child: Stack(
              children: [
                Positioned.fill(
                  // Um cartão novo não herda o giro do anterior.
                  child: _CartaoQueVira(
                    key: ValueKey(atual.id),
                    cartao: atual,
                    virado: virado,
                    corDaBorda: widget.corDaBorda,
                    aoTocar: () =>
                        setState(() => _viradoId = virado ? null : atual.id),
                  ),
                ),
                if (canto != null)
                  Positioned(top: 4, right: 4, child: canto(atual)),
              ],
            ),
          ),
        ),
        if (acao != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 16, 24, 0),
            child: acao(atual),
          ),
        Padding(
          padding: const EdgeInsets.all(24),
          child: FilledButton.icon(
            onPressed: () => setState(() => _indice++),
            icon: const Icon(Icons.chevron_right),
            iconAlignment: IconAlignment.end,
            label: const Text('Próximo cartão'),
          ),
        ),
      ],
    );
  }

  Widget _fim(ThemeData tema) {
    final temCartoes = widget.cartoes.isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: Center(
            // Rola em tela baixa (paisagem, fonte grande), em vez de transbordar.
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.check_circle_outline,
                    size: 64,
                    color: tema.colorScheme.primary,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    widget.tituloDoFim,
                    textAlign: TextAlign.center,
                    style: tema.textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    widget.apoioDoFim,
                    textAlign: TextAlign.center,
                    style: tema.textTheme.bodyLarge?.copyWith(
                      color: tema.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (temCartoes) ...[
                FilledButton(
                  onPressed: _recomecar,
                  child: const Text('Estudar de novo'),
                ),
                const SizedBox(height: 12),
              ],
              OutlinedButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('Voltar para a matéria'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// O cartão, um botão que vira. O giro é um meio giro no eixo vertical: o cartão estreita até a
/// metade e alarga já com a outra face. Com animações reduzidas no sistema, troca de face direto.
class _CartaoQueVira extends StatelessWidget {
  const _CartaoQueVira({
    super.key,
    required this.cartao,
    required this.virado,
    required this.corDaBorda,
    required this.aoTocar,
  });

  final Cartao cartao;
  final bool virado;
  final Color? corDaBorda;
  final VoidCallback aoTocar;

  static const _dica = 'toque para virar';

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return TweenAnimationBuilder<double>(
      // Sem início, o primeiro quadro já está na face certa; depois, anima de onde estiver.
      tween: Tween(end: virado ? 1 : 0),
      duration: MediaQuery.disableAnimationsOf(context)
          ? Duration.zero
          : const Duration(milliseconds: 300),
      curve: Curves.easeInOut,
      builder: (context, giro, _) {
        final resposta = giro >= 0.5;
        final rotulo = resposta ? 'resposta' : 'pergunta';
        final texto = resposta ? cartao.resposta : cartao.pergunta;
        return Transform(
          alignment: Alignment.center,
          transform: Matrix4.diagonal3Values(
            math.cos(giro * math.pi).abs(),
            1,
            1,
          ),
          // Um nó só, que muda de rótulo ao virar: o leitor de tela anuncia a face nova.
          child: MergeSemantics(
            child: Semantics(
              button: true,
              liveRegion: true,
              child: Material(
                color: tema.colorScheme.surface,
                clipBehavior: Clip.antiAlias,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(20),
                  side: BorderSide(
                    color: corDaBorda ?? tema.colorScheme.outlineVariant,
                    width: 1.5,
                  ),
                ),
                child: InkWell(
                  onTap: aoTocar,
                  // O rótulo do botão é a face inteira. Sem o rótulo explícito, a rolagem
                  // criaria um nó à parte e o texto ficaria fora do que é anunciado.
                  child: Semantics(
                    label: '$rotulo\n$texto\n$_dica',
                    child: ExcludeSemantics(
                      // Rola a face toda quando não cabe (texto longo, tela baixa, fonte grande).
                      child: LayoutBuilder(
                        builder: (context, limites) => SingleChildScrollView(
                          padding: const EdgeInsets.all(24),
                          child: ConstrainedBox(
                            constraints: BoxConstraints(
                              minHeight: math.max(0, limites.maxHeight - 48),
                            ),
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                Text(
                                  rotulo,
                                  style: tema.textTheme.labelLarge?.copyWith(
                                    color: tema.colorScheme.onSurfaceVariant,
                                  ),
                                ),
                                Padding(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 16,
                                  ),
                                  child: Text(
                                    texto,
                                    textAlign: TextAlign.center,
                                    style: tema.textTheme.titleLarge,
                                  ),
                                ),
                                Text(
                                  _dica,
                                  textAlign: TextAlign.center,
                                  style: tema.textTheme.bodySmall?.copyWith(
                                    color: tema.colorScheme.onSurfaceVariant,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
