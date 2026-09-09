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

## Filosofia de revisão: liberdade sem pressão

Ao contrário de Anki/Quizlet, o produto **não impõe prazos de revisão nem exige uma avaliação
obrigatória a cada cartão**. Decisão explícita do dono do produto, documentada aqui para não ser
reaberta sem motivo forte:

- A sessão de estudo é passiva: vira o cartão, avança para o próximo. Sem perguntar
  "como você se saiu?" a cada cartão.
- Cada matéria tem uma lista de cartões ativos (padrão, de fácil acesso) e uma lista de cartões
  arquivados (os que o usuário já considera dominados, de acesso mais discreto).
- Um cartão só é arquivado quando o usuário edita aquele cartão especificamente e escolhe
  "Arquivar" — nunca como resultado de uma resposta durante o estudo.
- Arquivar é reversível a qualquer momento ("Desarquivar").

Ver `01-fluxos-de-usuario.md` para o detalhamento tela a tela.

## Identidade visual a preservar

A paleta Rosé Pine Dawn do protótipo original deve ser levada para o rebuild como tema base
(ex: via variáveis de tema no frontend):

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

## Escopo do MVP

**Dentro do escopo:**
- Cadastro/login de usuário (multi-tenant — cada usuário só vê seus próprios dados)
- CRUD de matérias
- CRUD de cartões (manual)
- Geração de cartões por IA a partir de texto colado ou PDF, com tela de revisão
- Sessão de estudo simples (virar cartão, avançar)
- Arquivar/desarquivar cartão via tela de edição
- Aba de cartões arquivados por matéria
- BYOK (usuário pode configurar a própria chave de API de IA)

**Fora do escopo do MVP (não implementar sem decisão explícita):**
- Repetição espaçada com datas/algoritmo tipo SM-2 (foi cogitada e descartada — ver histórico)
- Colaboração/compartilhamento de matérias entre usuários
- Grafo de conhecimento / tags cruzadas entre matérias
- Gamificação (streaks, pontos, etc.)
- Microsserviço em Rust (cogitado, descartado por decisão do dono do produto)

## Documentos relacionados

- `01-fluxos-de-usuario.md` — telas e navegação
- `02-modelo-de-dados.md` — schema do banco
- `03-contrato-api.md` — endpoints REST
- `04-arquitetura-tecnica.md` — stack, decisões técnicas, estratégia de IA
