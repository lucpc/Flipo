# Fluxos de usuário

## Mapa de telas (visão geral)

```
Dashboard
  └─ Lista de matérias
       └─ Matéria selecionada
            ├─ Estudar cartões
            └─ Adicionar cartão
                 ├─ Criação manual ──┐
                 └─ Gerar com IA ────┼─→ Revisão → Salvo na matéria (ativos)
```

## 1. Dashboard

Tela de entrada pós-login. Espaço reservado para métricas futuras (ex: total de matérias,
atividade recente). Não bloqueante para o MVP — pode ser simples no início.

## 2. Lista de matérias

- Mostra as matérias do usuário autenticado.
- Cada item exibe o nome da matéria e um indicador do total de cartões **ativos**
  (ex: "6 ativos"). Se não houver nenhum cartão ativo, não mostrar "0 ativos" — mostrar um
  texto neutro tipo "tudo arquivado".
- Botão "Nova matéria".
- Sem limite artificial de quantidade de matérias (o protótipo original tinha `MAX_MATERIAS = 5`
  por limitação de canvas — essa restrição não se aplica mais e deve ser removida).

## 3. Matéria selecionada (menu)

Ao abrir uma matéria, o usuário vê três ações:
- **Estudar cartões** — abre a sessão de estudo (ver seção 5).
- **Adicionar cartão** — abre a escolha entre manual e IA (ver seção 4).
- **Apagar matéria** — ação destrutiva, com tela de confirmação (padrão já existente no
  protótipo original: pergunta "sim/não" antes de excluir).

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

Acessível de dois lugares: da sessão de estudo (ícone discreto) e de uma lista de gerenciamento
de cartões da matéria (fora do escopo detalhado deste documento, mas deve existir como tela
de apoio).

Painel de edição contém:
- Campo de pergunta (editável)
- Campo de resposta (editável)
- Botão "Salvar" — grava as alterações de conteúdo
- Botão "Arquivar" ou "Desarquivar" (dependendo do estado atual do cartão)
- Botão "Excluir" — remove o cartão permanentemente (idealmente com confirmação)

## 7. Aba de cartões arquivados

- Dentro da tela da matéria, uma aba separada ("Arquivados") ao lado da lista principal.
- Lista os cartões com `arquivado = true`.
- Cada item tem duas ações rápidas: desarquivar (volta para ativos) e excluir.
- Acesso discreto de propósito — não é uma tela de destaque, é consulta opcional.

## Decisões de UX que não devem ser reabertas sem justificativa

- Nenhuma tela pede ao usuário para avaliar sua confiança/desempenho em um cartão.
- Arquivar nunca é automático nem sugerido pelo sistema — é sempre uma ação deliberada do
  usuário, disparada a partir da edição do cartão.
- "Arquivar" se refere ao **cartão**, nunca à matéria (evitar reintroduzir essa ambiguidade
  em nomes de botão ou de endpoint).
