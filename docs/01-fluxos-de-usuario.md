# Fluxos de usuário

## Mapa de telas (visão geral)

```
Dashboard
  └─ Lista de matérias
       └─ Matéria selecionada  (menu ⋮: Editar matéria · Apagar matéria)
            ├─ Estudar cartões  (ativos)
            ├─ Arquivo          (revisão dos arquivados, com "Desarquivar" por cartão)
            └─ Adicionar cartão
                 ├─ Criação manual ──┐
                 └─ Gerar com IA ────┼─→ Revisão → Salvo na matéria (ativos)
```

## 1. Dashboard

Tela de entrada pós-login. Espaço reservado para métricas futuras (ex: total de matérias,
atividade recente). Conteúdo ainda a definir — sem métricas que cobrem o usuário (nada de
streaks ou "atrasados", ver `00-visao-geral.md`).

## 2. Lista de matérias

- Mostra as matérias do usuário autenticado.
- Cada item exibe o nome da matéria e um indicador do total de cartões **ativos**
  (ex: "6 ativos"). Se não houver nenhum cartão ativo, não mostrar "0 ativos" — mostrar um
  texto neutro tipo "tudo arquivado".
- Botão "Nova matéria".
- Sem limite artificial de quantidade de matérias (o protótipo original tinha `MAX_MATERIAS = 5`
  por limitação de canvas — essa restrição não se aplica mais e deve ser removida).

## 3. Matéria selecionada (menu)

Ao abrir uma matéria, o usuário vê:
- **Estudar cartões** — entrada principal, com o total de ativos (ex: "6 ativos"). Abre a
  sessão de estudo (ver seção 5).
- **Arquivo** — entrada com o mesmo formato, logo abaixo, com o total de arquivados
  (ex: "2 arquivados"). Abre a revisão dos cartões arquivados (ver seção 7). Sem arquivados,
  a entrada fica desabilitada com texto neutro ("nenhum arquivado").
- **Adicionar cartão** — abre a escolha entre manual e IA (ver seção 4).

Ações de gestão da matéria ficam fora do corpo da tela, num menu de três pontos (⋮) no
cabeçalho, para não dar destaque a uma ação destrutiva:
- **Editar matéria** — renomear a matéria.
- **Apagar matéria** — ação destrutiva, separada visualmente das demais no menu, com tela de
  confirmação (padrão já existente no protótipo original: pergunta "sim/não" antes de excluir).

## 4. Adicionar cartão

Ponto de entrada único com dois caminhos:

### 4a. Criação manual
Formulário com campo de pergunta e campo de resposta. Ao confirmar, vai direto para a
tela de Revisão (seção 4c) com um único cartão pré-carregado.

### 4b. Gerar com IA
- Usuário cola um texto ou anexa um PDF.
- Parâmetros opcionais: quantidade de cartões, nível (definição simples vs. aplicação/raciocínio).
- Ao gerar, a IA retorna uma lista de sugestões (pergunta/resposta) — **nada é salvo ainda**.

### 4c. Revisão (tela comum aos dois caminhos)
- Cada cartão sugerido aparece como um item com três ações rápidas: aceitar, editar, descartar.
- Só ao confirmar a revisão os cartões aceitos são persistidos, sempre na lista de **ativos**
  da matéria, com o campo `origem` marcado como `manual` ou `ia` conforme o caso.
- Esta tela reaproveita o mesmo componente de edição de cartão usado em outras partes do app
  (evita duplicar lógica de validação entre os fluxos manual e IA).

## 5. Estudar cartões

- A sessão usa **apenas os cartões ativos** da matéria (arquivados nunca aparecem aqui).
- Interação: tocar no cartão vira ele (pergunta ↔ resposta); um botão "Próximo cartão" avança.
- **Não há avaliação obrigatória por cartão.** O fluxo é passivo — ver e avançar, sem fricção.
- Um ícone discreto (menu de três pontos) no canto do cartão abre a edição inline
  (seção 6) sem sair da sessão de estudo.

## 6. Edição de cartão

Acessível da sessão de estudo e da revisão do Arquivo (ícone discreto nas duas) e de uma
lista de gerenciamento de cartões da matéria (fora do escopo detalhado deste documento, mas
deve existir como tela de apoio).

Painel de edição contém:
- Campo de pergunta (editável)
- Campo de resposta (editável)
- Botão "Salvar" — grava as alterações de conteúdo
- Botão "Arquivar" ou "Desarquivar" (dependendo do estado atual do cartão)
- Botão "Excluir" — remove o cartão permanentemente (idealmente com confirmação)

## 7. Arquivo (revisão dos cartões arquivados)

- Acessado pela entrada "Arquivo" na tela da matéria (seção 3), do mesmo jeito que "Estudar
  cartões".
- Usa apenas os cartões com `arquivado = true` e funciona como a sessão de estudo: tocar vira
  o cartão, "Próximo cartão" avança, sem avaliação obrigatória.
- Cada cartão tem um botão **"Desarquivar este cartão"**, que o devolve para os ativos. O
  cartão sai do Arquivo e a revisão segue para o próximo.
- O ícone discreto de edição (seção 6) também está disponível aqui — é por ele que se exclui
  um cartão arquivado.
- O Arquivo serve para relembrar de vez em quando o que já foi dominado e trazer de volta o que
  foi esquecido; o foco do dia a dia continua sendo "Estudar cartões".

## Decisões de UX que não devem ser reabertas sem justificativa

- Nenhuma tela pede ao usuário para avaliar sua confiança/desempenho em um cartão.
- Arquivar nunca é automático nem sugerido pelo sistema — é sempre uma ação deliberada do
  usuário, disparada a partir da edição do cartão. Desarquivar também é sempre deliberado,
  mas pode ser feito direto na revisão do Arquivo (seção 7), cartão a cartão.
- Ações de gestão da matéria (editar, apagar) ficam no menu ⋮, nunca como botão de destaque.
- "Arquivar" se refere ao **cartão**, nunca à matéria (evitar reintroduzir essa ambiguidade
  em nomes de botão ou de endpoint).
