# Fluxos de usuário

As telas seguem o protótipo interativo feito para a disciplina de PIU (Figma, quadros 01 a 07c,
tela de celular 390×844). O código entre parênteses nas seções (ex: 04c) é o nome do quadro no
Figma. O mesmo app Flutter roda em Android, iOS e web (ver `04-arquitetura-tecnica.md`): o layout
é pensado primeiro para o celular e, em telas largas, a coluna de conteúdo fica centralizada.

Princípio que atravessa todas as telas: criar e estudar com poucos toques. O estudante não pode
perder com a ferramenta o tempo que deveria estar estudando.

## Mapa de telas (visão geral)

```
Login ─ Criar conta
  └─ Minhas matérias  (avatar → Conta: chave de IA · Sair · Excluir conta)
       └─ Matéria selecionada  (menu ⋮: Editar matéria · Gerenciar cartões · Apagar matéria)
            ├─ Estudar cartões  (ativos)
            ├─ Arquivo          (revisão dos arquivados, com "Desarquivar" por cartão)
            └─ Adicionar cartão
                 ├─ Criação manual ──┐
                 └─ Gerar com IA ────┼─→ Revisão → Salvo na matéria (ativos)
```

## 1. Login e Criar conta (01)

- Login com e-mail e senha, abaixo do nome "Flipo" e da frase "flashcards livres, no seu ritmo".
- Link "Criar conta" abre o cadastro (nome, e-mail, senha) — `POST /api/auth/registro`. Depois
  de criar a conta, o usuário já entra logado.
- Não há dashboard: depois do login, a primeira tela é "Minhas matérias".

## 2. Minhas matérias (02)

- Cabeçalho "Minhas matérias" com a saudação "Olá, {nome}! O que vamos estudar?" e o avatar
  (inicial do nome), que abre a tela de Conta (seção 8).
- Cada matéria aparece com a inicial do nome como ícone e o total de cartões **ativos**
  (ex: "6 ativos"). Sem nenhum ativo mas com arquivados, mostrar "tudo arquivado"; sem nenhum
  cartão, "nenhum cartão".
- Botão "+ Nova matéria" fixo no rodapé.
- Sem limite artificial de quantidade de matérias (o protótipo original tinha `MAX_MATERIAS = 5`
  por limitação de canvas — essa restrição não se aplica mais).

## 3. Matéria selecionada (03, 03a, 03b)

Ao abrir uma matéria, o usuário vê:
- **Estudar cartões** — entrada principal, com o total de ativos (ex: "6 ativos"). Abre a
  sessão de estudo (ver seção 5).
- **Arquivo** — entrada com o mesmo formato, logo abaixo, com o total de arquivados
  (ex: "2 arquivados"). Abre a revisão dos cartões arquivados (ver seção 7). Sem arquivados,
  a entrada fica desabilitada com texto neutro ("nenhum arquivado").
- Texto de apoio discreto: "Estude no seu ritmo: vire o cartão e avance. Sem notas, sem prazos."
- **+ Adicionar cartão** — botão principal, fixo no rodapé. Abre a escolha entre manual e IA
  (ver seção 4).

Os totais refletem o estado atual: ao voltar de qualquer fluxo que cria, arquiva, desarquiva ou
exclui cartões, a tela mostra os números atualizados.

Ações de gestão ficam fora do corpo da tela, num menu de três pontos (⋮) no cabeçalho, longe
das ações principais:
- **Editar matéria** — renomear a matéria.
- **Gerenciar cartões** — lista de todos os cartões da matéria (ativos e arquivados), de onde
  se abre a edição de cada um (seção 6). Tela de apoio, de acesso secundário.
- **Apagar matéria** — ação destrutiva, separada visualmente das demais no menu, sempre com
  confirmação (03b).

Depois de salvar cartões ou de arquivar um cartão, a tela da matéria mostra um aviso discreto
no topo (ex: "8 cartões salvos em Estudar cartões") e os totais já atualizados (03c, 03d, 03e).

## 4. Adicionar cartão (04)

Ponto de entrada único, "Como você quer criar?", com dois caminhos de mesmo peso:
**Criar manualmente** e **Gerar com IA**. Uma nota lembra: "Nos dois caminhos você revisa tudo
antes de salvar — nenhum cartão entra na matéria sem passar pela revisão."

### 4a. Criação manual (04a)
Formulário com campo de pergunta e campo de resposta. Ao confirmar, vai direto para a
tela de Revisão (seção 4c) com um único cartão pré-carregado (04b).

### 4b. Gerar com IA (04c, 04d)
- Usuário cola um texto ou anexa um PDF. O texto do PDF é extraído e vai para o mesmo campo,
  onde o usuário o lê e corta o que não interessa antes de gerar. Se passar do limite do campo
  (20 000 caracteres), o contador avisa e o botão só ativa depois do corte. PDF digitalizado, sem
  texto selecionável, não tem o que extrair: o app avisa e o usuário pode colar o texto à mão.
- **Quantidade de cartões**: seletor com − e +, padrão 10.
- **Nível**: "definição simples" (padrão) ou "aplicação" (aplicação/raciocínio).
- Botão "Gerar sugestões". Enquanto a IA trabalha, uma tela de progresso avisa: "A IA está
  lendo o seu texto. Nada será salvo até você revisar cada cartão." (04d)
- A IA retorna uma lista de sugestões (pergunta/resposta) — **nada é salvo ainda**.

### 4c. Revisão (tela comum aos dois caminhos, 04b, 04e, 04f)
- Cabeçalho "Revisar sugestões (N)" com o lembrete "Nada foi salvo ainda — aceite, edite ou
  descarte".
- Cada cartão mostra o selo de origem (IA ou manual), o estado (pendente, aceito, editado e
  aceito, descartado) e três ações rápidas: aceitar, editar, descartar. Editar abre o mesmo
  formulário de edição (04f) e, ao salvar, o cartão fica "editado e aceito".
- Rodapé com o resumo ("8 aceitos · 1 descartado · 1 pendente") e o botão
  "Confirmar revisão (N cartões)", onde N é o número de aceitos.
- Só ao confirmar a revisão os cartões aceitos são persistidos, sempre na lista de **ativos**
  da matéria, com o campo `origem` marcado como `manual` ou `ia` conforme o caso. Pendentes e
  descartados não são salvos.
- Esta tela reaproveita o mesmo componente de edição de cartão usado em outras partes do app
  (evita duplicar lógica de validação entre os fluxos manual e IA).

## 5. Estudar cartões (05a–05e)

- A sessão usa **apenas os cartões ativos** da matéria (arquivados nunca aparecem aqui).
- Cabeçalho com o nome da matéria e a posição: "cartão 1 de 6". É só posição, não placar.
- Interação: tocar no cartão vira ele (pergunta ↔ resposta), com o rótulo "pergunta" ou
  "resposta" e a dica "toque para virar"; o botão "Próximo cartão" avança.
- **Não há avaliação obrigatória por cartão.** O fluxo é passivo — ver e avançar, sem fricção.
- Um ícone discreto (menu de três pontos) no canto do cartão abre a edição (seção 6) sem
  perder o lugar na sessão.
- Fim da sessão (05e): "Você passou pelos cartões ativos — Sem nota, sem placar. Volte quando
  quiser.", com "Estudar de novo" (recomeça do primeiro cartão) e "Voltar para a matéria".

## 6. Edição de cartão (06a, 06b, 06c)

Acessível da sessão de estudo e da revisão do Arquivo (ícone ⋮ no cartão) e da lista
"Gerenciar cartões" (seção 3).

Tela de edição contém:
- Campo de pergunta e campo de resposta (editáveis)
- Botão principal "Salvar" — grava as alterações de conteúdo
- Botão "Arquivar cartão" ou "Desarquivar cartão" (dependendo do estado atual), com o texto de
  apoio "Arquivados saem do estudo e ficam no Arquivo da matéria. Dá para desarquivar quando
  quiser." Se a pergunta ou a resposta foram alteradas e ainda não salvas, o mesmo toque salva o
  texto antes de arquivar ou desarquivar.
- Link discreto "Excluir cartão" — remove o cartão permanentemente, sempre com confirmação. A
  confirmação sugere arquivar como alternativa: "'{pergunta}' será apagado de vez. Se você só
  quer tirá-lo do estudo, use 'Arquivar cartão'." (06b)

## 7. Arquivo (revisão dos cartões arquivados, 07a–07d)

- Acessado pela entrada "Arquivo" na tela da matéria (seção 3), do mesmo jeito que "Estudar
  cartões".
- Cabeçalho "Arquivo" com a matéria e a posição ("Redes de Computadores · cartão 1 de 2"). O
  cartão usa a borda no acento íris, para diferenciar visualmente da sessão de estudo.
- Usa apenas os cartões com `arquivado = true` e funciona como a sessão de estudo: tocar vira
  o cartão, "Próximo cartão" avança, sem avaliação obrigatória.
- Abaixo do cartão, logo acima de "Próximo cartão", o botão **"Desarquivar este cartão"**
  devolve o cartão para os ativos. O cartão sai do Arquivo e a revisão segue para o próximo,
  com o aviso "Cartão devolvido para os ativos" entre o cabeçalho e o cartão, que some sozinho
  em alguns segundos (07c). Se era o último, a revisão termina.
- Fim da revisão: "Você passou pelos cartões arquivados — Sem nota, sem placar. Volte quando
  quiser.", com "Revisar de novo" (recomeça do primeiro cartão) e "Voltar para a matéria". Se o
  Arquivo ficou vazio (o último cartão foi desarquivado ou excluído), "Nenhum cartão no Arquivo —
  Os cartões desarquivados voltam para 'Estudar cartões'.", só com "Voltar para a matéria".
- O ícone ⋮ de edição (seção 6) também está disponível aqui — é por ele que se exclui um
  cartão arquivado.
- O Arquivo serve para relembrar de vez em quando o que já foi dominado e trazer de volta o que
  foi esquecido; o foco do dia a dia continua sendo "Estudar cartões".

## 8. Conta

Aberta pelo avatar em "Minhas matérias":
- **Chave de IA (BYOK)** — configurar ou remover a própria chave de API. A tela só mostra o
  provedor configurado, nunca a chave.
- **Sair**.
- **Excluir conta** — apaga a conta e todos os dados do usuário (matérias, cartões, chaves),
  com confirmação. Exigência das lojas de aplicativos para apps que permitem criar conta.

## Decisões de UX que não devem ser reabertas sem justificativa

- Nenhuma tela pede ao usuário para avaliar sua confiança/desempenho em um cartão, e nenhuma
  mostra placar, streak ou "atrasados".
- Arquivar nunca é automático nem sugerido pelo sistema — é sempre uma ação deliberada do
  usuário, disparada a partir da edição do cartão. Desarquivar também é sempre deliberado,
  mas pode ser feito direto na revisão do Arquivo (seção 7), cartão a cartão.
- Ações de gestão da matéria (editar, gerenciar cartões, apagar) ficam no menu ⋮, nunca como
  botão de destaque.
- Ação destrutiva (apagar matéria, excluir cartão, excluir conta) sempre pede confirmação.
- "Arquivar" se refere ao **cartão**, nunca à matéria (evitar reintroduzir essa ambiguidade
  em nomes de botão ou de endpoint).
