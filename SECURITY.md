# Security Policy

## Reporting a vulnerability

Please do not open a public issue for security problems. Report them privately to the maintainers
(for example through GitHub's "Report a vulnerability" advisory feature) and include:

- A description of the issue and its impact
- Steps to reproduce
- Affected version or commit
- Any suggested fix

You can expect an acknowledgement within a reasonable time and a coordinated disclosure once a fix
is available.

## Secrets

This repository must never contain credentials. Configuration is supplied through environment
variables or the git-ignored `application-pro.yml`.

Ignored by default:

- `application-pro.yml` / `application-local.yml`
- `.env` and `.env.*.local`
- `src/main/resources/WEB-INF/` (WeChat Pay merchant key, platform certificate, Alipay public key)
- `*.pem`, `*.p12`, `*.key`, `*.jks`, `*.keystore`

If you believe a credential was exposed, rotate it immediately at the provider. Removing the file
from the latest commit is not enough — git history and forks retain it.
