import 'package:file_picker/file_picker.dart';
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

/// Maior PDF aceito pelo `extrair-texto`, em bytes (docs/03-contrato-api.md). O app confere antes
/// de enviar: com um arquivo muito maior, o servidor fecha a conexão no meio do envio, e o app só
/// veria um erro de rede.
const tamanhoMaximoDoPdf = 10 * 1024 * 1024;

const _quantidadeMinima = 1;
const _quantidadeMaxima = 20;

/// Um PDF escolhido pelo usuário: o nome, o tamanho em bytes (null se o sistema não informou) e
/// como ler o conteúdo. O conteúdo só é lido depois de conferido o tamanho.
typedef PdfEscolhido = ({
  String nome,
  int? tamanho,
  Future<List<int>> Function() ler,
});

/// O seletor de arquivos do sistema, só com PDFs. Null se o usuário cancelar.
Future<PdfEscolhido?> _escolherPdfDoAparelho() async {
  final arquivo = await FilePicker.pickFile(
    type: FileType.custom,
    allowedExtensions: const ['pdf'],
  );
  if (arquivo == null) return null;
  return (
    nome: arquivo.name,
    // O tamanho que o seletor informou ou o do arquivo no disco (no navegador, o do Blob): não
    // lê o conteúdo.
    tamanho: await arquivo.length(),
    ler: arquivo.readAsBytes,
  );
}

/// O PDF escolhido passa do [tamanhoMaximoDoPdf]: nem é enviado.
class _PdfGrandeDemais implements Exception {
  const _PdfGrandeDemais();
}

/// Gerar com IA (docs/01-fluxos-de-usuario.md, seção 4b, quadros 04c e 04d do Figma): o texto
/// colado ou extraído de um PDF, a quantidade e o nível; enquanto a IA gera, a tela de progresso
/// (04d). O texto do PDF vai para o campo, onde o usuário o lê e corta antes de gerar. As
/// sugestões seguem para a Revisão, que só grava (pelo `lote`) o que o usuário aceitar. Fecha
/// devolvendo o aviso da Revisão para a tela da matéria; sem salvar, o usuário volta a este
/// formulário como o deixou.
class TelaGerarIa extends StatefulWidget {
  const TelaGerarIa({
    super.key,
    required this.api,
    required this.materiaId,
    this.escolherPdf = _escolherPdfDoAparelho,
  });

  final ApiClient api;
  final String materiaId;

  /// Abre o seletor de PDF. Os testes passam o seu, para não depender do plugin.
  final Future<PdfEscolhido?> Function() escolherPdf;

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
  bool _lendoPdf = false;
  String? _erroDoPdf;
  // O último PDF cujo texto foi para o campo.
  ({String nome, int paginas, bool truncado})? _pdfLido;
  // Muda a cada geração e quando o usuário volta durante uma: a resposta de uma geração que não é
  // mais a atual é ignorada.
  var _geracao = 0;

  @override
  void initState() {
    super.initState();
    var textoAnterior = _texto.text;
    // O contador e o botão acompanham o texto.
    _texto.addListener(
      () => setState(() {
        // Só mover o cursor ou focar o campo não conta como edição.
        if (_texto.text == textoAnterior) return;
        textoAnterior = _texto.text;
        _erroDoPdf = null;
        // Cortar o texto do PDF é o uso esperado e mantém a informação; esvaziar o campo, não.
        if (_texto.text.trim().isEmpty) _pdfLido = null;
      }),
    );
  }

  @override
  void dispose() {
    _texto.dispose();
    super.dispose();
  }

  Future<void> _gerar() async {
    final texto = _texto.text.trim();
    if (_gerando || _lendoPdf || texto.isEmpty) return;
    if (_texto.text.length > limiteDoTexto) return;
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

  /// Escolhe um PDF e põe o texto dele no campo, perguntando antes se o campo já tinha texto. Num
  /// erro, ou se o usuário cancela o seletor ou a substituição, o campo fica como estava.
  Future<void> _anexarPdf() async {
    final PdfEscolhido pdf;
    final TextoDoPdf lido;
    try {
      final escolhido = await widget.escolherPdf();
      // Cancelou o seletor: nada muda.
      if (escolhido == null || !mounted) return;
      pdf = escolhido;
      if ((pdf.tamanho ?? 0) > tamanhoMaximoDoPdf) {
        throw const _PdfGrandeDemais();
      }
      setState(() {
        _lendoPdf = true;
        _erroDoPdf = null;
      });
      final bytes = await pdf.ler();
      // Sem o tamanho informado pelo sistema, a conferência fica para depois da leitura.
      if (bytes.length > tamanhoMaximoDoPdf) throw const _PdfGrandeDemais();
      lido = await widget.api.extrairTextoDePdf(bytes, pdf.nome);
    } catch (e) {
      // Num 401 o app já está voltando ao login, e esta tela pode ter saído.
      if (!mounted) return;
      setState(() {
        _lendoPdf = false;
        _erroDoPdf = _textoDoErroDoPdf(e);
      });
      return;
    }
    if (!mounted) return;
    setState(() => _lendoPdf = false);

    if (_texto.text.trim().isNotEmpty && !await _confirmarSubstituicao()) {
      return;
    }
    if (!mounted) return;
    // Definido por código, o texto não passa pelo [_LimiteUtf16]: pode ficar acima do limite,
    // e o usuário o corta no campo.
    _texto.text = lido.texto;
    setState(
      () => _pdfLido = (
        nome: pdf.nome,
        paginas: lido.paginas,
        truncado: lido.truncado,
      ),
    );
  }

  Future<bool> _confirmarSubstituicao() async =>
      await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Substituir o texto atual pelo do PDF?'),
          actions: [
            OutlinedButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancelar'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Substituir'),
            ),
          ],
        ),
      ) ??
      false;

  String _textoDoErroDoPdf(Object erro) => switch (erro) {
    // A mensagem do 400 é escrita para o usuário e diz qual é o caso (docs/03-contrato-api.md).
    // Sem ela (corpo vazio, página de erro de um proxy), vale o texto do app.
    ApiException(status: 400, doServidor: true, :final mensagem)
        when mensagem.trim().isNotEmpty =>
      mensagem,
    ApiException(status: 400) =>
      'Não foi possível ler este PDF. Ele pode ser inválido, estar corrompido '
          'ou protegido por senha.',
    _PdfGrandeDemais() || ApiException(status: 413) =>
      'O arquivo passa de 10 MB. Escolha um PDF menor.',
    ApiException(status: 422) =>
      'Este PDF não tem texto selecionável (talvez seja digitalizado). Cole o '
          'texto à mão.',
    ApiException(status: 503) =>
      'O servidor está ocupado lendo outros PDFs. Tente de novo em instantes.',
    _ => textoDoErro(erro),
  };

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
    final pdfLido = _pdfLido;
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'A partir de um texto colado ou de um PDF',
            style: _textoDeApoio(tema),
          ),
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
              // Só o texto de um PDF passa do limite: o que é digitado ou colado para nele.
              errorText: caracteres > limiteDoTexto
                  ? 'Corte o texto para até ${_milhares(limiteDoTexto)} '
                        'caracteres'
                  : null,
            ),
            keyboardType: TextInputType.multiline,
            minLines: 6,
            maxLines: 12,
            textCapitalization: TextCapitalization.sentences,
            inputFormatters: const [_LimiteUtf16(limiteDoTexto)],
          ),
          const SizedBox(height: 8),
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: OutlinedButton.icon(
              onPressed: _lendoPdf ? null : _anexarPdf,
              icon: const Icon(Icons.attach_file),
              label: const Text('Anexar PDF'),
            ),
          ),
          if (_lendoPdf)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Row(
                children: [
                  const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  const SizedBox(width: 8),
                  Semantics(
                    liveRegion: true,
                    child: Text('Lendo o PDF…', style: _textoDeApoio(tema)),
                  ),
                ],
              ),
            )
          else if (pdfLido != null) ...[
            const SizedBox(height: 8),
            Text(
              '${pdfLido.nome} · ${pdfLido.paginas} '
              '${pdfLido.paginas == 1 ? 'página' : 'páginas'}',
              style: _textoDeApoio(tema),
            ),
            if (pdfLido.truncado)
              Text(
                'O PDF é longo: só o começo dele foi lido.',
                style: _textoDeApoio(tema),
              ),
          ],
          MensagemDeErro(_erroDoPdf),
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
            aoPressionar:
                _lendoPdf ||
                    _texto.text.trim().isEmpty ||
                    caracteres > limiteDoTexto
                ? null
                : _gerar,
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
///
/// O texto de um PDF entra por código e pode deixar o campo acima do limite. Aí, o que não
/// aumenta o texto passa sempre (apagar, mover o cursor), e o que aumenta é recusado: cortar
/// pelo cursor removeria texto que o usuário não apagou.
class _LimiteUtf16 extends TextInputFormatter {
  const _LimiteUtf16(this.limite);

  final int limite;

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue antigo,
    TextEditingValue novo,
  ) {
    final texto = novo.text;
    if (texto.length <= limite || texto.length <= antigo.text.length) {
      return novo;
    }
    if (antigo.text.length > limite) return antigo;
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
