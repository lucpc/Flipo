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

## Matérias

```
GET /api/materias
200 → [{ "id", "nome", "totalAtivos", "totalArquivados" }]

POST /api/materias
Body: { "nome" }
201 → { "id", "nome", "totalAtivos", "totalArquivados" }
// totais sempre 0 — matéria recém-criada ainda não tem cartões. Mesmo DTO do GET, por
// simplicidade (um único formato de matéria na API em vez de um schema reduzido só pro POST).

DELETE /api/materias/{id}
204
```

## Cartões

```
GET /api/materias/{materiaId}/cartoes?arquivado=false
// arquivado é opcional, default false (lista principal). arquivado=true retorna a aba de arquivados.
200 → [{ "id", "pergunta", "resposta", "origem", "arquivado", "ultimaRevisao" }]

POST /api/materias/{materiaId}/cartoes
Body: { "pergunta", "resposta" }
201 → { "id", "pergunta", "resposta", "origem": "manual", "arquivado": false }

PATCH /api/cartoes/{id}
Body: { "pergunta", "resposta" }
200 → cartão atualizado
// edição de conteúdo apenas — não altera o campo arquivado

PATCH /api/cartoes/{id}/arquivar
200 → { "id", "arquivado": true }

PATCH /api/cartoes/{id}/desarquivar
200 → { "id", "arquivado": false }

DELETE /api/cartoes/{id}
204
```

`arquivar`/`desarquivar` são endpoints próprios, separados do `PATCH` genérico de conteúdo —
mantém a edição de texto semanticamente separada da ação de arquivar, o que facilita registrar
essas ações como eventos distintos se um histórico de atividade for adicionado no futuro.

## Geração de cartões por IA

```
POST /api/materias/{materiaId}/cartoes/gerar-ia
Body: { "texto": "...", "quantidade": 10 }
// ou multipart com um PDF anexado no lugar de "texto"
200 → [{ "pergunta", "resposta" }]
// não persiste nada — apenas sugestões para a tela de revisão

POST /api/materias/{materiaId}/cartoes/lote
Body: { "cartoes": [{ "pergunta", "resposta" }] }
// os cartões que o usuário aceitou/editou na tela de revisão
201 → [{ "id", "pergunta", "resposta", "origem": "ia", "arquivado": false }]
```

A separação em duas chamadas (`gerar-ia` sugere, `lote` persiste) é o que garante que a IA nunca
grava nada diretamente no banco — sempre passa pela aprovação explícita do usuário.

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
