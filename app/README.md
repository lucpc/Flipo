# Flipo — app Flutter

App do Flipo para Android, iOS e web (ver `docs/04-arquitetura-tecnica.md`).

```bash
flutter pub get
flutter analyze
flutter test
flutter run -d chrome
```

A URL do backend vem de `--dart-define=API_URL=...` (padrão `http://localhost:8080`). No emulador
Android, o localhost da máquina é `10.0.2.2`:

```bash
flutter run --dart-define=API_URL=http://10.0.2.2:8080
```

Para rodar contra o backend local, na raiz do repositório:

```bash
make up                                 # Postgres do docker-compose.yml
(cd backend && ./mvnw spring-boot:run)  # API em http://localhost:8080
(cd app && flutter run -d chrome)       # outro terminal
```

O app chama a API direto, sem proxy. No navegador isso é uma requisição de outra origem: o
backend aceita `http://localhost` em qualquer porta (`flipo.cors.allowed-origin-patterns`), o que
cobre a porta aleatória do `flutter run -d chrome`.

- Tema Rosé Pine Dawn: `lib/theme.dart`. Os widgets usam `Theme.of(context)`, nunca cores fixas.
- Coluna centralizada em telas largas: `lib/widgets/coluna_central.dart`.
- Client da API (JWT em `flutter_secure_storage`): `lib/api/api_client.dart`.
- Sessão em `lib/main.dart`: sem token salvo o app abre no login; "Sair" ou um 401 voltam ao
  login, fechando as telas abertas por cima. Login e Criar conta: `lib/telas/autenticacao.dart`.
