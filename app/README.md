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

- Tema Rosé Pine Dawn: `lib/theme.dart`. Os widgets usam `Theme.of(context)`, nunca cores fixas.
- Coluna centralizada em telas largas: `lib/widgets/coluna_central.dart`.
- Client da API (JWT em `flutter_secure_storage`): `lib/api/api_client.dart`.
