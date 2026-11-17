import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../widgets/botao_enviar.dart';
import '../widgets/coluna_central.dart';
import '../widgets/mensagem_de_erro.dart';
import 'adicionar_cartao.dart';
import 'conta.dart';
import 'em_breve.dart';

/// Minhas matérias (docs/01-fluxos-de-usuario.md, seção 2, quadro 02 do Figma). Recarrega a lista
/// sempre que uma tela aberta daqui fecha, para os totais nunca ficarem velhos.
class TelaMinhasMaterias extends StatefulWidget {
  const TelaMinhasMaterias({
    super.key,
    required this.api,
    required this.aoSair,
  });

  final ApiClient api;
  final VoidCallback aoSair;

  @override
  State<TelaMinhasMaterias> createState() => _TelaMinhasMateriasState();
}

class _TelaMinhasMateriasState extends State<TelaMinhasMaterias> {
  Usuario? _usuario;
  // null enquanto carrega ou depois de uma falha.
  List<Materia>? _materias;
  String? _erro;
  // Só vale a resposta do pedido mais recente: uma mais antiga traria totais velhos.
  var _pedido = 0;

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    final pedido = ++_pedido;
    if (_erro != null) setState(() => _erro = null);
    try {
      final resultados = await Future.wait<Object>([
        widget.api.buscarUsuarioAtual(),
        widget.api.listarMaterias(),
      ]);
      if (!mounted || pedido != _pedido) return;
      setState(() {
        _usuario = resultados[0] as Usuario;
        _materias = resultados[1] as List<Materia>;
      });
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted || pedido != _pedido) return;
      setState(() {
        _materias = null;
        _erro = textoDoErro(e);
      });
    }
  }

  Future<void> _novaMateria() async {
    final materia = await showDialog<Materia>(
      context: context,
      builder: (_) => _DialogoNovaMateria(api: widget.api),
    );
    // Sem matéria: o usuário cancelou, ou um 401 fechou o diálogo.
    if (materia == null || !mounted) return;
    setState(() => _materias = [...?_materias, materia]);
    // Uma recarga que saiu antes da criação viria sem a matéria nova; esta a substitui.
    _carregar();
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final usuario = _usuario;
    return Scaffold(
      body: SafeArea(
        child: ColunaCentral(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 24, 16, 8),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Minhas matérias',
                            style: tema.textTheme.headlineSmall,
                          ),
                          if (usuario != null)
                            Text(
                              'Olá, ${usuario.nome}! O que vamos estudar?',
                              style: _textoDeApoio(tema),
                            ),
                        ],
                      ),
                    ),
                    // Fica disponível mesmo se a lista não carregar, para dar para sair.
                    IconButton(
                      tooltip: 'Conta',
                      onPressed: () => _abrirEVoltar(
                        context,
                        widget.api,
                        TelaConta(aoSair: widget.aoSair),
                        (_) => _carregar(),
                      ),
                      icon: CircleAvatar(
                        backgroundColor: tema.colorScheme.primary,
                        foregroundColor: tema.colorScheme.onPrimary,
                        child: usuario == null
                            ? const Icon(Icons.person)
                            : Text(_inicial(usuario.nome)),
                      ),
                    ),
                  ],
                ),
              ),
              Expanded(child: _conteudo(tema)),
              Padding(
                padding: const EdgeInsets.all(24),
                child: FilledButton.icon(
                  onPressed: _materias == null ? null : _novaMateria,
                  icon: const Icon(Icons.add),
                  label: const Text('Nova matéria'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _conteudo(ThemeData tema) {
    final erro = _erro;
    final materias = _materias;
    if (erro != null) return _Falha(mensagem: erro, aoTentarDeNovo: _carregar);
    if (materias == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return RefreshIndicator(
      onRefresh: _carregar,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        children: [
          if (materias.isEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 48),
              child: Text(
                'Nenhuma matéria ainda',
                textAlign: TextAlign.center,
                style: _textoDeApoio(tema),
              ),
            ),
          for (final materia in materias)
            _Entrada(
              icone: _Inicial(materia.nome),
              titulo: materia.nome,
              resumo: _resumoNaLista(materia),
              aoTocar: () => _abrirEVoltar(
                context,
                widget.api,
                TelaMateria(api: widget.api, materia: materia),
                (_) => _carregar(),
              ),
            ),
        ],
      ),
    );
  }
}

/// Pede o nome e cria a matéria. Fecha devolvendo a [Materia] criada; fechado de outro jeito
/// (cancelar, ou um 401 que leva ao login), devolve null.
class _DialogoNovaMateria extends StatefulWidget {
  const _DialogoNovaMateria({required this.api});

  final ApiClient api;

  @override
  State<_DialogoNovaMateria> createState() => _DialogoNovaMateriaState();
}

class _DialogoNovaMateriaState extends State<_DialogoNovaMateria> {
  // Limite do MateriaRequest do backend.
  static const _tamanhoMaximo = 255;

  final _nome = TextEditingController();
  bool _enviando = false;
  String? _erro;

  @override
  void dispose() {
    _nome.dispose();
    super.dispose();
  }

  Future<void> _criar() async {
    if (_enviando) return;
    final nome = _nome.text.trim();
    final invalido = nome.isEmpty
        ? 'Informe o nome.'
        : nome.length > _tamanhoMaximo
        ? 'O nome pode ter no máximo $_tamanhoMaximo caracteres.'
        : null;
    setState(() {
      _erro = invalido;
      _enviando = invalido == null;
    });
    if (invalido != null) return;

    try {
      final materia = await widget.api.criarMateria(nome);
      if (mounted) Navigator.of(context).pop(materia);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _enviando = false;
        _erro = textoDoErro(e);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // Enquanto envia, não fecha: a matéria seria criada sem aparecer na lista.
    return PopScope(
      canPop: !_enviando,
      child: AlertDialog(
        title: const Text('Nova matéria'),
        content: TextField(
          controller: _nome,
          autofocus: true,
          decoration: InputDecoration(
            labelText: 'Nome da matéria',
            errorText: _erro,
            errorMaxLines: 3,
          ),
          textCapitalization: TextCapitalization.sentences,
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _criar(),
        ),
        actions: [
          TextButton(
            onPressed: _enviando ? null : () => Navigator.of(context).pop(),
            child: const Text('Cancelar'),
          ),
          BotaoEnviar(
            rotulo: 'Criar',
            enviando: _enviando,
            aoPressionar: _criar,
          ),
        ],
      ),
    );
  }
}

/// Tela da matéria (seção 3, quadros 03 e 03c–03e). Abre com a matéria vinda da lista e busca os
/// totais de novo sempre que uma tela aberta daqui fecha. A tela que fecha pode devolver um aviso
/// para o topo (ex.: `Navigator.pop(context, '8 cartões salvos em Estudar cartões')`).
class TelaMateria extends StatefulWidget {
  const TelaMateria({super.key, required this.api, required this.materia});

  final ApiClient api;
  final Materia materia;

  @override
  State<TelaMateria> createState() => _TelaMateriaState();
}

class _TelaMateriaState extends State<TelaMateria> {
  late Materia _materia = widget.materia;
  String? _aviso;
  String? _erro;
  bool _carregando = false;
  var _pedido = 0;

  Future<void> _carregar() async {
    final pedido = ++_pedido;
    setState(() {
      _carregando = true;
      _erro = null;
    });
    try {
      // A API não tem GET de uma matéria só; a lista traz os mesmos totais.
      final materias = await widget.api.listarMaterias();
      if (!mounted || pedido != _pedido) return;
      final atual = materias.where((m) => m.id == _materia.id).firstOrNull;
      if (atual == null) {
        // Apagada em outro aparelho: volta para a lista, que recarrega. Só se esta tela está no
        // topo: se o usuário já está saindo dela, o pop fecharia a lista; se outra tela abriu por
        // cima, a recarga de quando ela fechar passa por aqui de novo.
        if (ModalRoute.of(context)?.isCurrent ?? false) {
          Navigator.of(context).pop();
        }
        return;
      }
      setState(() {
        _materia = atual;
        _carregando = false;
      });
    } catch (e) {
      if (!mounted || pedido != _pedido) return;
      setState(() {
        _carregando = false;
        _erro = textoDoErro(e);
      });
    }
  }

  // Estudar (#47) e Arquivo (#23) abrem a tela provisória por enquanto.
  void _abrir(String titulo) => _abrirTela(TelaEmBreve(titulo: titulo));

  void _abrirTela(Widget tela) =>
      _abrirEVoltar(context, widget.api, tela, (aviso) {
        setState(() => _aviso = aviso);
        _carregar();
      });

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final ativos = _materia.totalAtivos;
    final arquivados = _materia.totalArquivados;
    final aviso = _aviso;
    final erro = _erro;
    return Scaffold(
      appBar: AppBar(title: Text(_materia.nome)),
      body: SafeArea(
        child: ColunaCentral(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Altura fixa, para o conteúdo não pular quando a barra aparece.
              SizedBox(
                height: 4,
                child: _carregando ? const LinearProgressIndicator() : null,
              ),
              if (aviso != null) _Aviso(aviso),
              Expanded(
                child: erro != null
                    ? _Falha(mensagem: erro, aoTentarDeNovo: _carregar)
                    : ListView(
                        padding: const EdgeInsets.all(16),
                        children: [
                          _Entrada(
                            icone: const Icon(Icons.style_outlined),
                            titulo: 'Estudar cartões',
                            resumo: ativos == 0
                                ? 'nenhum ativo'
                                : _contagem(ativos, 'ativo', 'ativos'),
                            aoTocar: () => _abrir('Estudar cartões'),
                          ),
                          _Entrada(
                            icone: const Icon(Icons.inventory_2_outlined),
                            titulo: 'Arquivo',
                            resumo: arquivados == 0
                                ? 'nenhum arquivado'
                                : _contagem(
                                    arquivados,
                                    'arquivado',
                                    'arquivados',
                                  ),
                            aoTocar: arquivados == 0
                                ? null
                                : () => _abrir('Arquivo'),
                          ),
                          Padding(
                            padding: const EdgeInsets.all(16),
                            child: Text(
                              'Estude no seu ritmo: vire o cartão e avance. '
                              'Sem notas, sem prazos.',
                              textAlign: TextAlign.center,
                              style: _textoDeApoio(tema),
                            ),
                          ),
                        ],
                      ),
              ),
              Padding(
                padding: const EdgeInsets.all(24),
                child: FilledButton.icon(
                  onPressed: () => _abrirTela(
                    TelaAdicionarCartao(
                      api: widget.api,
                      materiaId: _materia.id,
                    ),
                  ),
                  icon: const Icon(Icons.add),
                  label: const Text('Adicionar cartão'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Abre [tela] por cima e, quando ela fecha, chama [aoVoltar] com o que ela devolveu. Não chama se
/// a sessão acabou com a tela aberta (401 ou "Sair"): o app já está voltando ao login, e recarregar
/// só faria uma requisição sem token.
Future<void> _abrirEVoltar(
  BuildContext context,
  ApiClient api,
  Widget tela,
  void Function(String? aviso) aoVoltar,
) async {
  final aviso = await Navigator.of(
    context,
  ).push<String>(MaterialPageRoute(builder: (_) => tela));
  final temSessao = await api.temToken().catchError((Object _) => false);
  if (context.mounted && temSessao) aoVoltar(aviso);
}

String _contagem(int n, String singular, String plural) =>
    '$n ${n == 1 ? singular : plural}';

String _resumoNaLista(Materia materia) {
  if (materia.totalAtivos > 0) {
    return _contagem(materia.totalAtivos, 'ativo', 'ativos');
  }
  return materia.totalArquivados > 0 ? 'tudo arquivado' : 'nenhum cartão';
}

String _inicial(String nome) {
  final texto = nome.trim();
  return texto.isEmpty ? '?' : texto.characters.first.toUpperCase();
}

TextStyle? _textoDeApoio(ThemeData tema) => tema.textTheme.bodyMedium?.copyWith(
  color: tema.colorScheme.onSurfaceVariant,
);

/// Uma linha tocável: matéria na lista, ou entrada da tela da matéria. Sem [aoTocar], fica
/// desabilitada.
class _Entrada extends StatelessWidget {
  const _Entrada({
    required this.icone,
    required this.titulo,
    required this.resumo,
    required this.aoTocar,
  });

  final Widget icone;
  final String titulo;
  final String resumo;
  final VoidCallback? aoTocar;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: icone,
        title: Text(titulo),
        subtitle: Text(resumo),
        trailing: const Icon(Icons.chevron_right),
        enabled: aoTocar != null,
        onTap: aoTocar,
      ),
    );
  }
}

/// Inicial do nome da matéria num quadrado arredondado, como ícone.
class _Inicial extends StatelessWidget {
  const _Inicial(this.nome);

  final String nome;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Container(
      width: 40,
      height: 40,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: tema.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        _inicial(nome),
        style: tema.textTheme.titleMedium?.copyWith(
          color: tema.colorScheme.primary,
        ),
      ),
    );
  }
}

/// Aviso discreto no topo da tela da matéria, deixado pela tela que acabou de fechar.
class _Aviso extends StatelessWidget {
  const _Aviso(this.texto);

  final String texto;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Semantics(
      liveRegion: true,
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: tema.colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Icon(
              Icons.check_circle_outline,
              size: 20,
              color: tema.colorScheme.primary,
            ),
            const SizedBox(width: 12),
            Expanded(child: Text(texto, style: tema.textTheme.bodyMedium)),
          ],
        ),
      ),
    );
  }
}

/// Conteúdo que não carregou: a mensagem e "Tentar de novo".
class _Falha extends StatelessWidget {
  const _Falha({required this.mensagem, required this.aoTentarDeNovo});

  final String mensagem;
  final VoidCallback aoTentarDeNovo;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            MensagemDeErro(mensagem),
            const SizedBox(height: 16),
            OutlinedButton(
              onPressed: aoTentarDeNovo,
              child: const Text('Tentar de novo'),
            ),
          ],
        ),
      ),
    );
  }
}
