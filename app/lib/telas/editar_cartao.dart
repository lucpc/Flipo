import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../widgets/botao_enviar.dart';
import '../widgets/coluna_central.dart';
import '../widgets/formulario_cartao.dart';
import '../widgets/mensagem_de_erro.dart';

/// O que a [TelaEditarCartao] fez com o cartão, já gravado na API. A tela fecha devolvendo um
/// destes, ou null se o usuário voltou sem gravar nada.
///
/// Quem abre a edição aplica o resultado à própria lista com [aplicarEm]:
/// - Gerenciar cartões (todos os cartões): `resultado.aplicarEm(cartoes)`;
/// - a sessão de estudo (#13, só ativos): `resultado.aplicarEm(cartoes, arquivados: false)`,
///   e o cartão arquivado ou excluído sai do baralho;
/// - o Arquivo (#23, só arquivados): `resultado.aplicarEm(cartoes, arquivados: true)`,
///   e o cartão desarquivado ou excluído sai do baralho.
///
/// O [aviso], se houver, é o que a tela da matéria mostra no topo ao voltar (quadro 03e).
sealed class ResultadoDaEdicao {
  const ResultadoDaEdicao();

  /// O id do cartão editado.
  String get id;

  /// O cartão como ficou; null se foi excluído.
  Cartao? get cartao;

  String? get aviso => null;

  /// [cartoes] com o cartão [id] trocado pela versão nova, ou fora da lista se foi excluído ou,
  /// com [arquivados], se não está mais nesse estado. Os demais seguem na mesma ordem.
  List<Cartao> aplicarEm(List<Cartao> cartoes, {bool? arquivados}) {
    final novo = cartao;
    final fica =
        novo != null && (arquivados == null || novo.arquivado == arquivados);
    return [
      for (final c in cartoes)
        if (c.id != id) c else if (fica) novo,
    ];
  }
}

/// Pergunta e resposta salvas; o estado de arquivado não mudou.
final class CartaoEditado extends ResultadoDaEdicao {
  const CartaoEditado(this.cartao);

  @override
  final Cartao cartao;

  @override
  String get id => cartao.id;
}

/// Foi para o Arquivo.
final class CartaoArquivado extends ResultadoDaEdicao {
  const CartaoArquivado(this.cartao);

  @override
  final Cartao cartao;

  @override
  String get id => cartao.id;

  @override
  String get aviso => 'Cartão arquivado — agora está no Arquivo';
}

/// Voltou para os ativos.
final class CartaoDesarquivado extends ResultadoDaEdicao {
  const CartaoDesarquivado(this.cartao);

  @override
  final Cartao cartao;

  @override
  String get id => cartao.id;
}

/// Apagado de vez.
final class CartaoExcluido extends ResultadoDaEdicao {
  const CartaoExcluido(this.id);

  @override
  final String id;

  @override
  Cartao? get cartao => null;
}

enum _Acao { salvar, arquivar, excluir }

/// Edição de um cartão salvo (docs/01-fluxos-de-usuario.md, seção 6, quadros 06a–06c): salvar o
/// texto, arquivar ou desarquivar, ou excluir. Cada ação grava na hora e fecha a tela devolvendo
/// o [ResultadoDaEdicao]; se falha, a tela fica aberta com o erro.
///
/// Arquivar só acontece aqui, por escolha do usuário: é decisão de produto fechada. Arquivar ou
/// desarquivar com o texto mudado salva o texto antes, no mesmo toque; excluir descarta o texto
/// não salvo.
class TelaEditarCartao extends StatefulWidget {
  const TelaEditarCartao({
    super.key,
    required this.api,
    required this.nomeDaMateria,
    required this.cartao,
  });

  final ApiClient api;

  /// Fica abaixo do título, como em "Redes de Computadores · arquivado".
  final String nomeDaMateria;
  final Cartao cartao;

  @override
  State<TelaEditarCartao> createState() => _TelaEditarCartaoState();
}

class _TelaEditarCartaoState extends State<TelaEditarCartao> {
  final _formulario = GlobalKey<FormularioCartaoState>();
  // O cartão como está na API. Muda sem a tela fechar só se salvar o texto deu certo e arquivar
  // em seguida falhou: aí voltar devolve um [CartaoEditado] com ele.
  late Cartao _salvo = widget.cartao;
  _Acao? _emAndamento;
  String? _erro;

  bool get _salvoSemFechar => !identical(_salvo, widget.cartao);

  Future<void> _executar(
    _Acao acao,
    Future<ResultadoDaEdicao> Function() requisicao,
  ) async {
    if (_emAndamento != null) return;
    setState(() {
      _emAndamento = acao;
      _erro = null;
    });
    try {
      final resultado = await requisicao();
      if (mounted) Navigator.of(context).pop(resultado);
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted) return;
      setState(() {
        _emAndamento = null;
        _erro = textoDoErro(e);
      });
    }
  }

  void _salvar(String pergunta, String resposta) => _executar(
    _Acao.salvar,
    () async => CartaoEditado(
      await widget.api.editarCartao(_salvo.id, pergunta, resposta),
    ),
  );

  /// Com o texto mudado (e válido), salva antes e depois arquiva ou desarquiva. Com algum campo
  /// vazio, só mostra o erro.
  void _arquivarOuDesarquivar() {
    final valores = _formulario.currentState!.validar();
    if (valores == null) return;
    final cartao = _salvo;
    final mudou =
        valores.pergunta != cartao.pergunta ||
        valores.resposta != cartao.resposta;
    _executar(_Acao.arquivar, () async {
      if (mudou) {
        // Se arquivar falhar depois, a tela fica com o texto já salvo: tentar de novo só arquiva.
        _salvo = await widget.api.editarCartao(
          cartao.id,
          valores.pergunta,
          valores.resposta,
        );
      }
      return cartao.arquivado
          ? CartaoDesarquivado(await widget.api.desarquivarCartao(cartao.id))
          : CartaoArquivado(await widget.api.arquivarCartao(cartao.id));
    });
  }

  Future<void> _excluir() async {
    final cartao = _salvo;
    final excluir = await showDialog<bool>(
      context: context,
      builder: (context) {
        final cores = Theme.of(context).colorScheme;
        return AlertDialog(
          title: const Text('Excluir cartão?'),
          // Arquivado, o cartão já está fora do estudo: a sugestão de arquivar não cabe.
          content: Text(
            cartao.arquivado
                ? '"${cartao.pergunta}" será apagado de vez.'
                : '"${cartao.pergunta}" será apagado de vez. Se você só quer '
                      'tirá-lo do estudo, use "Arquivar cartão".',
          ),
          actions: [
            OutlinedButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancelar'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              style: FilledButton.styleFrom(
                backgroundColor: cores.error,
                foregroundColor: cores.onError,
              ),
              child: const Text('Excluir'),
            ),
          ],
        );
      },
    );
    // null: fechou o diálogo tocando fora.
    if (excluir != true || !mounted) return;
    _executar(_Acao.excluir, () async {
      await widget.api.excluirCartao(cartao.id);
      return CartaoExcluido(cartao.id);
    });
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final cores = tema.colorScheme;
    final cartao = _salvo;
    final emAndamento = _emAndamento;
    final livre = emAndamento == null;
    final apoio = tema.textTheme.bodyMedium?.copyWith(
      color: cores.onSurfaceVariant,
    );
    final rotuloArquivo = cartao.arquivado
        ? 'Desarquivar cartão'
        : 'Arquivar cartão';
    // Enquanto grava, não fecha: a lista de quem abriu ficaria diferente da API. Pelo mesmo
    // motivo, com o texto salvo mas sem arquivar, voltar devolve o cartão salvo.
    return PopScope<ResultadoDaEdicao>(
      canPop: livre && !_salvoSemFechar,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && livre && _salvoSemFechar) {
          Navigator.of(context).pop(CartaoEditado(_salvo));
        }
      },
      child: Scaffold(
        appBar: AppBar(title: const Text('Editar cartão')),
        body: SafeArea(
          child: ColunaCentral(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    cartao.arquivado
                        ? '${widget.nomeDaMateria} · arquivado'
                        : widget.nomeDaMateria,
                    style: apoio,
                  ),
                  const SizedBox(height: 16),
                  FormularioCartao(
                    key: _formulario,
                    pergunta: cartao.pergunta,
                    resposta: cartao.resposta,
                    rotulo: 'Salvar',
                    enviando: emAndamento == _Acao.salvar,
                    bloqueado: emAndamento != null,
                    erro: _erro,
                    aoConfirmar: _salvar,
                  ),
                  const SizedBox(height: 12),
                  // Borda na íris, o acento do Arquivo; o rótulo no texto do tema, que tem
                  // contraste para letra pequena.
                  OutlinedButton.icon(
                    onPressed: livre ? _arquivarOuDesarquivar : null,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: cores.onSurface,
                      side: BorderSide(color: cores.tertiary),
                    ),
                    icon: cartao.arquivado && emAndamento != _Acao.arquivar
                        ? const Icon(Icons.restore)
                        : null,
                    label: emAndamento == _Acao.arquivar
                        ? IndicadorDeEnvio(rotuloArquivo)
                        : Text(rotuloArquivo),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    cartao.arquivado
                        ? 'O cartão volta para os ativos e reaparece em '
                              '"Estudar cartões".'
                        : 'Arquivados saem do estudo e ficam no Arquivo da '
                              'matéria. Dá para desarquivar quando quiser.',
                    textAlign: TextAlign.center,
                    style: tema.textTheme.bodySmall?.copyWith(
                      color: cores.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 16),
                  Center(
                    child: TextButton(
                      onPressed: livre ? _excluir : null,
                      // O tom de erro para texto, que tem contraste para letra pequena.
                      style: TextButton.styleFrom(
                        foregroundColor:
                            tema.inputDecorationTheme.errorStyle?.color ??
                            cores.error,
                      ),
                      child: emAndamento == _Acao.excluir
                          ? const IndicadorDeEnvio('Excluir cartão')
                          : const Text('Excluir cartão'),
                    ),
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
