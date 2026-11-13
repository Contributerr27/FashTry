# Contributing

Thanks for your interest in FashTry.

## Before you start

- Search existing issues before opening a new one.
- For larger changes, open an issue first so the approach can be discussed.
- Never commit real credentials, customer data, database dumps or payment certificates.

## Development setup

See the Quick start section in [README.md](README.md). In short:

```bash
docker compose up -d
mvn spring-boot:run
cd web && npm install && npm run dev
```

## Pull requests

1. Fork the repository and create a topic branch.
2. Keep changes focused; avoid unrelated refactors.
3. Match the existing code style (Java: 4 spaces, constructor injection; Vue: Composition API).
4. Describe what changed and how you tested it.
5. Update the README or configuration templates when you add or rename settings.

## Commit messages

Use short imperative subjects, for example:

```text
Add retry policy for Gemini timeouts
Fix credit refund when a task fails early
```

## Security

Do not open public issues for security problems. Follow [SECURITY.md](SECURITY.md).
