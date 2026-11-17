import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../widgets/coluna_central.dart';
import '../widgets/formulario_cartao.dart';
import 'revisao.dart';

/// Adicionar cartão (docs/01-fluxos-de-usuario.md, seção 4, quadro 04 do Figma): os dois caminhos
/// de criação, que terminam na Revisão. Fecha devolvendo o aviso da Revisão para a tela da
/// matéria, ou null se nada foi salvo.
class TelaAdicionarCartao extends StatelessWidget {
  const TelaAdicionarCartao({
    super.key,
    required this.api,
    required this.materiaId,
  });

  final ApiClient api;
  final String materiaId;

  Future<void> _criarManualmente(BuildContext context) async {
    final aviso = await Navigator.of(context).push<String>(
      MaterialPageRoute(
        builder: (_) => _TelaCriarManual(api: api, materiaId: materiaId),
      ),
    );
    // Salvou: volta direto para a matéria. Sem aviso, o usuário voltou por conta própria (ou um
    // 401 já fechou tudo).
    if (aviso != null && context.mounted) Navigator.of(context).pop(aviso);
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Adicionar cartão')),
      body: SafeArea(
        child: ColunaCentral(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(8, 8, 8, 16),
                child: Text(
                  'Como você quer criar?',
                  style: tema.textTheme.titleLarge,
                ),
              ),
              _Caminho(
                icone: Icons.edit_note,
                titulo: 'Criar manualmente',
                descricao: 'Digite a pergunta e a resposta',
                aoTocar: () => _criarManualmente(context),
              ),
              // Chega com o E5 (#16).
              const _Caminho(
                icone: Icons.auto_awesome_outlined,
                titulo: 'Gerar com IA',
                descricao: 'A partir de um texto colado ou de um PDF',
                aoTocar: null,
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  'Nos dois caminhos você revisa tudo antes de salvar — '
                  'nenhum cartão entra na matéria sem passar pela revisão.',
                  textAlign: TextAlign.center,
                  style: tema.textTheme.bodyMedium?.copyWith(
                    color: tema.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Um dos dois caminhos, com o mesmo peso visual. Sem [aoTocar], fica desabilitado e marcado
/// "em breve".
class _Caminho extends StatelessWidget {
  const _Caminho({
    required this.icone,
    required this.titulo,
    required this.descricao,
    required this.aoTocar,
  });

  final IconData icone;
  final String titulo;
  final String descricao;
  final VoidCallback? aoTocar;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Card(
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Icon(icone),
        title: Text(titulo),
        subtitle: Text(descricao),
        trailing: aoTocar == null
            ? Text(
                'em breve',
                style: tema.textTheme.labelSmall?.copyWith(
                  color: tema.colorScheme.onSurfaceVariant,
                ),
              )
            : const Icon(Icons.chevron_right),
        enabled: aoTocar != null,
        onTap: aoTocar,
      ),
    );
  }
}

/// Criação manual (quadro 04a): pergunta e resposta, que seguem para a Revisão como um cartão só,
/// já aceito (04b). Voltar da Revisão sem salvar devolve o formulário como estava.
class _TelaCriarManual extends StatelessWidget {
  const _TelaCriarManual({required this.api, required this.materiaId});

  final ApiClient api;
  final String materiaId;

  Future<void> _revisar(
    BuildContext context,
    String pergunta,
    String resposta,
  ) async {
    final aviso = await Navigator.of(context).push<String>(
      MaterialPageRoute(
        builder: (_) => TelaRevisao(
          sugestoes: [
            SugestaoDeCartao(
              pergunta: pergunta,
              resposta: resposta,
              origem: 'manual',
            ),
          ],
          salvar: (cartoes) => api.criarCartao(
            materiaId,
            cartoes.single.pergunta,
            cartoes.single.resposta,
          ),
          titulo: 'Revisar cartão',
          lembrete: 'Nada foi salvo ainda',
          comecarAceitas: true,
        ),
      ),
    );
    if (aviso != null && context.mounted) Navigator.of(context).pop(aviso);
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Novo cartão')),
      body: SafeArea(
        child: ColunaCentral(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'Criação manual',
                  style: tema.textTheme.bodyMedium?.copyWith(
                    color: tema.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 16),
                FormularioCartao(
                  rotulo: 'Ir para revisão',
                  icone: Icons.chevron_right,
                  ajuda: 'Você confere o cartão na revisão antes de salvar.',
                  aoConfirmar: (pergunta, resposta) =>
                      _revisar(context, pergunta, resposta),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
