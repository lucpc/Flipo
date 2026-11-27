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
// só JSON. O PDF não vem aqui: o app extrai o texto antes, pelo POST /api/pdf/extrair-texto
// (abaixo), e o usuário revisa e corta esse texto no campo antes de gerar.
// texto: obrigatório, não pode ser em branco, no máximo 20 000 caracteres (contados como
// unidades UTF-16, como o String.length() do Java: um emoji conta 2).
// quantidade: opcional, inteiro de 1 a 20, padrão 10. nivel: opcional, "definicao" (definição
// simples, padrão) ou "aplicacao" (aplicação/raciocínio).
// A validação vale para o valor já convertido do JSON: texto em branco ou longo demais,
// quantidade fora de 1 a 20 e nivel com outro texto → 400. A conversão do JSON é tolerante com
// formas não canônicas (ex: "quantidade": "5" ou 1.9, "nivel": 1), que podem ser aceitas
// convertidas em vez de dar 400. O app manda sempre a forma canônica: número inteiro e
// "definicao"/"aplicacao".
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

```
POST /api/pdf/extrair-texto
Body: multipart/form-data com o campo "arquivo" (um PDF)
// Só extrai o texto: não gera cartões, não grava nada e não guarda o arquivo (é lido em memória
// ou em arquivo temporário e descartado ao fim da requisição). Exige login, como o resto da API.
// O arquivo é reconhecido pelo conteúdo (assinatura "%PDF-"), não pelo nome nem pelo
// Content-Type enviado.
200 → { "texto", "paginas", "truncado" }
// texto: o texto do PDF na ordem das páginas, com espaços repetidos reduzidos a um e quebras de
// linha preservadas entre parágrafos, sem espaços nas pontas. No máximo 100 000 caracteres
// (unidades UTF-16): se o PDF tiver mais, vem o começo e "truncado": true. Também vem
// "truncado": true, com o texto lido até ali, quando a extração para num dos limites de trabalho
// do servidor (volume descomprimido ou tempo) depois de já ter extraído algum texto. O texto pode
// passar dos 20 000 do gerar-ia: quem corta é o usuário, no campo do app, antes de gerar.
// paginas: o número de páginas do PDF.
400 → { "mensagem" } sem o campo "arquivo", arquivo vazio, arquivo que não é PDF, PDF corrompido
//     ou protegido por senha, ou PDF pesado demais para extrair (passa dos limites de trabalho
//     antes de sair qualquer texto). A mensagem é escrita para o usuário final e diz qual é o
//     caso; o cliente pode mostrá-la como veio.
413 → { "mensagem" } arquivo acima de 10 MB (10 × 1024 × 1024 bytes)
//     O cliente deve conferir o tamanho antes de enviar: com um arquivo muito maior (acima de
//     ~32 MB, o max-swallow-size do servidor), o servidor responde 413 cedo e fecha a conexão,
//     e um cliente que ainda está enviando vê só um erro de rede.
422 → { "mensagem" } PDF sem texto selecionável (ex: digitalizado, só com imagens): não há o que
//     extrair; o usuário pode colar o texto à mão
503 → { "mensagem" } servidor ocupado: o número de extrações simultâneas é limitado, e esta
//     esperou demais por uma vaga. Dá para tentar de novo.
```

**Limites da extração de PDF.** O PDF vem de qualquer usuário cadastrado, e um arquivo pequeno
pode ser montado para descomprimir para gigabytes. A extração tem tetos de volume descomprimido,
de tempo e de extrações simultâneas, mas ainda roda no mesmo processo do backend, e há caminhos
internos do PDFBox (índice do arquivo, tabelas de caracteres) que esses tetos não cobrem. Isolar a
extração num processo separado é pré-requisito para publicar o backend com cadastro aberto
([#75](https://github.com/lucpc/Flipo/issues/75), que bloqueia a #46).

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
