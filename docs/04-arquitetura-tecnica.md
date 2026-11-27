# Arquitetura técnica

## Stack

| Camada | Escolha | Motivo |
|---|---|---|
| Backend | Java 21 + Spring Boot | Alinhado com a experiência prévia do desenvolvedor (Java/Spring) |
| Persistência | Spring Data JPA + Hibernate | Mapeamento direto das entidades de `02-modelo-de-dados.md` |
| Autenticação | Spring Security + JWT | Protege endpoints; escopa dados por usuário via token |
| Validação | Bean Validation (`@NotBlank`, `@Size`, etc.) nos DTOs | |
| Banco de dados | PostgreSQL | Relacional, modela bem `Usuario → Materia → Cartao` |
| Hospedagem do banco | Neon ou Railway (Postgres gerenciado) | Free tier, sem manter servidor |
| App (frontend) | Flutter (Dart), um único código para Android, iOS e web | Publicar nas lojas de aplicativos atrai mais usuários; um código só evita implementar cada tela duas vezes |
| Deploy backend | Render ou Railway (Docker) | Deploy direto do GitHub |
| Distribuição do app | Google Play e App Store; versão web (build web do Flutter) em Vercel ou Netlify | O build e a publicação para iOS exigem macOS — feitos em CI na nuvem (runner macOS do GitHub Actions ou Codemagic), já que o desenvolvimento é em Linux |
| Testes do app | `flutter test` (testes de widget) | Priorizar a lógica de interação: sessão de estudo, Arquivo, tela de revisão |
| Testes backend | JUnit 5 + Mockito | Priorizar testes da camada de serviço (regras de arquivar/gerar-ia) |
| Migrations | Flyway | Versionadas, em vez de `ddl-auto: update` em produção |

O app Flutter substituiu um primeiro frontend em React + Vite + TypeScript, aposentado quando o
produto passou a mirar as lojas de aplicativos. O backend não mudou: a API REST com JWT atende o
app em qualquer plataforma.

**Rust foi cogitado e descartado** para um microsserviço de pré-processamento de texto — decisão
do dono do produto, não reabrir sem justificativa nova.

## Estratégia de IA

### Abstração de geração de cartões

Toda chamada de geração de cartões passa por uma interface, nunca é chamada diretamente pelos
controllers ou serviços de negócio:

```java
public interface GeradorDeCartoes {
    List<CartaoSugerido> gerar(String texto, int quantidade, NivelCartao nivel);
}
```

Isso permite trocar a implementação por configuração/perfil Spring, sem alterar nenhuma outra
parte do sistema.

### Fase de concepção (API estabelecida)

Para validar o fluxo completo (colar texto → gerar → revisar → salvar) rapidamente, sem perder
tempo depurando saída malformada de modelo pequeno, a primeira implementação usa uma API já
estabelecida, em camada barata:

- **Anthropic** (ex: Claude Haiku) e/ou **Gemini** (ex: Gemini Flash) — saída estruturada em JSON
  confiável, latência baixa. Custo por chamada é irrelevante no volume de desenvolvimento.

`GeradorAnthropic` (e, se usado, `GeradorGemini`) `implements GeradorDeCartoes` — chamada sempre
feita a partir do backend (nunca do cliente, para não expor a chave), solicitando saída
estruturada em JSON. Essa mesma implementação cobre também a cota padrão em produção — não há uma
implementação de "fase de produção" separada a escrever depois.

**Como ligar o `GeradorAnthropic`:** definir variáveis de ambiente no processo do backend.

| Variável | Obrigatória | Padrão | Efeito |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | sim, para ligar | vazia | Com valor, o `GeradorAnthropic` é registrado. Vazia ou ausente, não há gerador e o `gerar-ia` responde 503 |
| `ANTHROPIC_MODEL` | não | `claude-haiku-5-5` | Id do modelo usado na geração |

As variáveis alimentam `flipo.ia.anthropic.api-key` e `flipo.ia.anthropic.modelo`
(`application.properties`). A chave é lida só dessa propriedade: o cliente do SDK não procura
credenciais no ambiente por conta própria. Ela não aparece em log, resposta nem mensagem de erro.
Os testes fixam a chave vazia (`src/test/resources/config/application.properties`), então rodam
sem chamar a API mesmo com `ANTHROPIC_API_KEY` exportada.

A chamada usa saída estruturada (JSON schema derivado do record de resposta), timeout de 60 s e
uma retentativa (o SDK repete também em timeout), então o pior caso do `gerar-ia` é ~2 min antes do
502. O log do SDK fica desligado (`ANTHROPIC_LOG` é ignorado), porque em `debug` ele registraria o
texto do usuário; as falhas são registradas pelo gerador com mensagem sem chave nem texto. Recusa do modelo, corte por `max_tokens`, JSON fora do formato
ou erro HTTP viram 502 no `gerar-ia`. As instruções fixas vão no `system`; nível, quantidade e
texto vão na mensagem do usuário, com o texto entre `<texto>` e `</texto>`.

### Redução de custo (depois de validado, opcional)

Uma vez o fluxo validado, avaliar mover a cota padrão (não-BYOK) para uma opção gratuita ou de
baixíssimo custo, se o volume de uso justificar:

- **Ollama + modelo local** (Llama 3.1/3.2, Mistral, Gemma 2) — roda na própria máquina, custo
  zero, mas exige hospedar o modelo e tem qualidade/confiabilidade de JSON inferiores a uma API
  estabelecida.
- **Groq API** — serve modelos open-source (Llama, Mixtral) com camada gratuita generosa e baixa
  latência, sem exigir hardware local.

Modelos menores são menos confiáveis para devolver JSON bem formado — se essa fase for adotada, a
camada de parsing precisa ficar tolerante a erro (ex: nova tentativa em caso de JSON malformado).
Isso não é necessário enquanto a cota padrão usa Anthropic/Gemini.

### BYOK (bring your own key)

Para não deixar o custo de produção recair sobre o desenvolvedor indefinidamente, o app suporta
que cada usuário configure sua própria chave de API (tabela `ChaveApi`, ver
`02-modelo-de-dados.md`). Quando uma chave de usuário está configurada, ela é usada nas chamadas
daquele usuário; caso contrário, cai para a cota padrão (Anthropic/Gemini em camada barata, ou o
gerador de baixo custo da fase de redução de custo, se essa fase já tiver sido adotada).

Chaves nunca são armazenadas em texto puro — sempre criptografadas antes de persistir, e nunca
retornadas pela API (ver contrato em `03-contrato-api.md`, endpoint `GET /usuarios/me/chaves`
devolve só o provedor, nunca a chave).

## Segurança

- JWT em todos os endpoints exceto `/api/auth/*`.
- Toda consulta a `Materia`/`Cartao` filtra implicitamente por `usuario_id` extraído do token —
  nunca confiar em um ID de matéria/cartão vindo do path sem checar que pertence ao usuário
  autenticado.
- Chaves de API de terceiros criptografadas em repouso.
- Senha armazenada como hash (ex: BCrypt via Spring Security), nunca em texto puro.
- No app, o JWT fica em armazenamento seguro do sistema (`flutter_secure_storage`: Keychain no
  iOS, Keystore no Android), nunca em preferências comuns.
- Excluir a conta apaga de fato todos os dados do usuário, inclusive as chaves de API.
- CORS liberado só para as origens da configuração (`flipo.cors.allowed-origin-patterns`, variável
  de ambiente `CORS_ALLOWED_ORIGIN_PATTERNS`): `http://localhost:[*]` em desenvolvimento (`[*]`
  aceita qualquer porta, mas não origens parecidas como `http://localhost:1@evil.com`) e, em
  produção, o domínio exato do deploy web do app. Nunca um curinga como `https://*.vercel.app`,
  que aceitaria qualquer site hospedado na Vercel. Sem `allowCredentials`, porque o JWT vai no
  cabeçalho.
