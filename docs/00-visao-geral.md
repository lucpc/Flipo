# Visão geral do produto

## Contexto

Este projeto evolui um protótipo pessoal feito em p5.js (`FlashCardsDinamicos`) para uma
aplicação full-stack de portfolio. O protótipo original já validava o conceito central:
**liberdade para criar os próprios flashcards, organizados por matéria/área de conhecimento**,
com persistência local (localStorage), uma máquina de estados simples controlando a navegação
(variável `tela`), e uma paleta de cores própria (Rosé Pine Dawn).

Nada do conceito original é descartado — o objetivo é reconstruir a mesma ideia com uma stack
de produção, multi-usuário, e um diferencial real frente ao mercado de flashcards.

## Problema e oportunidade

Ferramentas de flashcard estabelecidas (Anki, Quizlet, RemNote, Brainscape) resolvem bem partes
do problema, mas cada uma erra em algo:

- **Anki** — repetição espaçada robusta, mas UX pesada e curva de aprendizado alta.
- **Quizlet** — bonito e social, mas algoritmo de revisão fraco e paywall em recursos essenciais.
- **RemNote/Obsidian** — conectam conhecimento, mas são complexos e voltados a power users.
- **Brainscape** — autoavaliação de confiança interessante, pouco copiada pelo mercado.

## Diferencial escolhido: criação assistida por IA, sem abrir mão da liberdade manual

O diferencial central do produto é permitir **duas formas de criar cartões, com pesos iguais**:

1. **Manual** — o fluxo original do protótipo (digitar pergunta e resposta).
2. **Gerado por IA** — o usuário cola um texto ou anexa um PDF, e a IA sugere cartões.

Os dois caminhos convergem numa **tela de revisão comum**: nenhum cartão gerado por IA entra
"cru" no sistema — o usuário sempre aceita, edita ou descarta cada sugestão antes de salvar.
Isso preserva o espírito de liberdade do projeto original (o usuário decide, o sistema sugere)
e ao mesmo tempo demonstra integração de LLM de forma útil, não decorativa.

Nos dois caminhos, criar um cartão tem que ser rápido e levar poucos toques: o estudante não
pode gastar produzindo o cartão o tempo que poderia estar estudando.

## Filosofia de revisão: liberdade sem pressão

Ao contrário de Anki/Quizlet, o produto **não impõe prazos de revisão nem exige uma avaliação
obrigatória a cada cartão**. Decisão explícita do dono do produto, documentada aqui para não ser
reaberta sem motivo forte:

- A sessão de estudo é passiva: vira o cartão, avança para o próximo. Sem perguntar
  "como você se saiu?" a cada cartão.
- Cada matéria tem uma lista de cartões ativos (padrão, estudados em "Estudar cartões") e um
  Arquivo com os cartões que o usuário já considera dominados — que pode ser revisado do
  mesmo jeito, quando o usuário quiser relembrar.
- Um cartão só é arquivado quando o usuário edita aquele cartão especificamente e escolhe
  "Arquivar" — nunca como resultado de uma resposta durante o estudo.
- Arquivar é reversível a qualquer momento ("Desarquivar"), inclusive direto na revisão do
  Arquivo, cartão a cartão.

Ver `01-fluxos-de-usuario.md` para o detalhamento tela a tela.

## Identidade visual a preservar

A paleta Rosé Pine Dawn do protótipo original deve ser levada para o rebuild como tema base
(no app Flutter, via `ThemeData`/`ColorScheme`):

```
RP_BASE    (250, 244, 237)   fundo
RP_SURFACE (255, 250, 243)   superfície
RP_OVERLAY (242, 233, 225)   overlay
RP_MUTED   (152, 147, 165)   texto discreto
RP_SUBTLE  (121, 117, 147)   texto secundário
RP_TEXT    ( 87,  82, 121)   texto primário
RP_PINE    ( 40, 105, 131)   acento 1
RP_FOAM    ( 86, 148, 159)   acento 2
RP_IRIS    (144, 122, 169)   acento 3
RP_ROSE    (215, 130, 126)   acento/alerta
RP_HL_MED  (223, 218, 217)   destaque médio
```

## Escopo do produto

O Flipo é planejado como produto final, não como MVP: o que está listado como incluído é o que
o produto tem que ter, e a ordem de implementação segue o produto (ver `Backlog.md`).

**Incluído:**
- App para Android e iOS, publicado nas lojas, e versão web — um único app Flutter
- Cadastro/login de usuário (multi-tenant — cada usuário só vê seus próprios dados)
- Excluir a própria conta e todos os dados (exigência das lojas de aplicativos)
- CRUD de matérias
- CRUD de cartões (manual)
- Geração de cartões por IA a partir de texto colado ou PDF, com tela de revisão
- Sessão de estudo simples (virar cartão, avançar)
- Arquivar/desarquivar cartão via tela de edição
- Arquivo por matéria: revisão dos cartões arquivados, com "Desarquivar" em cada cartão
- BYOK (usuário pode configurar a própria chave de API de IA)

**Descartado (decisões fechadas — não implementar):**
- Repetição espaçada com datas/algoritmo tipo SM-2 (foi cogitada e descartada — ver histórico)
- Gamificação: streaks, pontos, crachás de "atrasado", contadores de erro. Contraria a filosofia
  de estudo sem pressão (cenário C6 do relatório de PIU: a ansiedade dos decks "atrasados" é o
  que faz o usuário abandonar outras ferramentas)
- Microsserviço em Rust (cogitado, descartado por decisão do dono do produto)
- Tags além da matéria e grafo de conhecimento: a matéria já é a tag do cartão — livre, criada
  pelo próprio usuário, sem lista pré-definida — e é a única forma de organização

**Em aberto (decidir antes de implementar):**
- Colaboração/compartilhamento de matérias entre usuários

## Documentos relacionados

- `01-fluxos-de-usuario.md` — telas e navegação
- `02-modelo-de-dados.md` — schema do banco
- `03-contrato-api.md` — endpoints REST
- `04-arquitetura-tecnica.md` — stack, decisões técnicas, estratégia de IA
