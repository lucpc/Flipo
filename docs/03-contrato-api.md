# Contrato de API

Convenções: JSON em request/response. Todos os endpoints abaixo de `/api/materias` e
`/api/cartoes` exigem autenticação (`Authorization: Bearer <token>`) e são implicitamente
escopados ao usuário autenticado — o `usuarioId` vem do token, nunca de parâmetro de URL/body,
para impedir que um usuário acesse dados de outro trocando um ID.

## Autenticação

```
POST /api/auth/registro
Body: { "nome", "email", "senha" }
201 → { "id", "nome", "email" }

POST /api/auth/login
Body: { "email", "senha" }
200 → { "token" }
```

## Usuário

```
GET /api/usuarios/me
200 → { "id", "nome", "email" }
// saudação "Olá, {nome}!" em Minhas matérias e inicial do avatar

DELETE /api/usuarios/me
204
// "Excluir conta" (tela Conta): apaga o usuário e, em cascata, matérias, cartões e chaves.
// Exigência das lojas de aplicativos para apps que permitem criar conta.
```

## Matérias

```
GET /api/materias
200 → [{ "id", "nome", "totalAtivos", "totalArquivados" }]

POST /api/materias
Body: { "nome" }
201 → { "id", "nome", "totalAtivos", "totalArquivados" }
// totais sempre 0 — matéria recém-criada ainda não tem cartões. Mesmo DTO do GET, por
// simplicidade (um único formato de matéria na API em vez de um schema reduzido só pro POST).

PATCH /api/materias/{id}
Body: { "nome" }
200 → { "id", "nome", "totalAtivos", "totalArquivados" }
// "Editar matéria" (menu ⋮ da tela da matéria) — por ora só renomeia.

DELETE /api/materias/{id}
204
```

## Cartões

```
GET /api/materias/{materiaId}/cartoes?arquivado=false
// arquivado é opcional, default false (sessão de estudo). arquivado=true alimenta a revisão do Arquivo.
200 → [{ "id", "pergunta", "resposta", "origem", "arquivado" }]
// em ordem de criação (mais antigo primeiro), com o id como desempate. Editar, arquivar ou
// desarquivar não muda a posição do cartão.

POST /api/materias/{materiaId}/cartoes
Body: { "pergunta", "resposta" }
201 → { "id", "pergunta", "resposta", "origem": "manual", "arquivado": false }

PATCH /api/cartoes/{id}
Body: { "pergunta", "resposta" }
200 → { "id", "pergunta", "resposta", "origem", "arquivado" }
// edição de conteúdo apenas — não altera o campo arquivado

PATCH /api/cartoes/{id}/arquivar
200 → { "id", "pergunta", "resposta", "origem", "arquivado": true }

PATCH /api/cartoes/{id}/desarquivar
200 → { "id", "pergunta", "resposta", "origem", "arquivado": false }

DELETE /api/cartoes/{id}
204
```

`arquivar`/`desarquivar` são endpoints próprios, separados do `PATCH` genérico de conteúdo —
mantém a edição de texto semanticamente separada da ação de arquivar, o que facilita registrar
essas ações como eventos distintos se um histórico de atividade for adicionado no futuro. Os três
devolvem o mesmo `CartaoResponse` completo do `GET`/`POST` (em vez de um schema reduzido só pra
`arquivar`/`desarquivar`) pelo mesmo critério já adotado em `POST /api/materias`: um único formato
de recurso na API é mais simples do que um schema mínimo por endpoint.

## Geração de cartões por IA

```
POST /api/materias/{materiaId}/cartoes/gerar-ia
Body: { "texto": "...", "quantidade": 10, "nivel": "definicao" }
// ou multipart com um PDF anexado no lugar de "texto" (ainda não implementado: por ora só JSON)
// texto: obrigatório, não pode ser em branco, no máximo 20 000 caracteres.
// quantidade: opcional, de 1 a 20, padrão 10. nivel: opcional, "definicao" (definição simples,
// padrão) ou "aplicacao" (aplicação/raciocínio). Fora disso → 400.
200 → [{ "pergunta", "resposta" }]
// não persiste nada — apenas sugestões para a tela de revisão. Pode vir com menos sugestões que
// o pedido, nunca com mais.
404 → matéria inexistente ou de outro usuário
502 → { "mensagem" } o gerador falhou ou devolveu sugestões inutilizáveis (lista vazia, pergunta
//     ou resposta em branco). Mensagem genérica, sem detalhe do provedor; dá para tentar de novo.
503 → { "mensagem" } o servidor não tem geração por IA configurada

POST /api/materias/{materiaId}/cartoes/lote
Body: { "cartoes": [{ "pergunta", "resposta" }] }
// os cartões que o usuário aceitou/editou na tela de revisão: de 1 a 20 (o máximo de um
// gerar-ia), cada um com a mesma validação do POST .../cartoes. Fora disso → 400.
201 → [{ "id", "pergunta", "resposta", "origem": "ia", "arquivado": false }]
// na mesma ordem do body
404 → matéria inexistente ou de outro usuário
```

A separação em duas chamadas (`gerar-ia` sugere, `lote` persiste) é o que garante que a IA nunca
grava nada diretamente no banco — sempre passa pela aprovação explícita do usuário. O `gerar-ia`
não grava nada em caso algum, nem quando o gerador falha.

O `lote` é tudo ou nada: grava todos os cartões numa transação só. Se um cartão é inválido (400)
ou o banco recusa algum deles, nenhum fica gravado.

**Origem no `lote`:** o cliente não manda `origem`. Todo cartão do `lote` é gravado com
`origem: "ia"`, inclusive a sugestão que o usuário editou antes de aceitar, e sempre ativo; um
`origem` ou `arquivado` enviado no body é ignorado. O cartão criado à mão não passa pelo `lote`: a
Revisão da criação manual grava pelo `POST .../cartoes`, que marca `origem: "manual"`. Assim o
cliente nunca escolhe a origem, e uma Revisão nunca mistura as duas.

## Chaves de API (BYOK)

```
GET /api/usuarios/me/chaves
200 → [{ "id", "provedor" }]
// nunca devolve a chave em si, só o provedor configurado

POST /api/usuarios/me/chaves
Body: { "provedor", "chave" }
201 → { "id", "provedor" }
// a chave é criptografada antes de persistir

DELETE /api/usuarios/me/chaves/{id}
204
```
