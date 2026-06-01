# Security Policy

## Reporting a vulnerability

If you find a security issue, please **do not open a public issue**. Instead, email
the maintainer at **rrbrambley@gmail.com** with a description and steps to reproduce.
You'll get an acknowledgement, and fixes for confirmed issues will be released as soon
as is practical.

## Secrets and credentials

This repository is designed so that **no secrets are ever committed**:

- **AWS credentials** are read from the default AWS credential chain
  (`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` env vars, or `~/.aws/credentials`).
  They are never read from the repo, `gradle.properties`, or any tracked file.
- **Per-developer config** (your Google OAuth Web client ID, S3 bucket, region, and
  CloudFront URL) belongs in your user-global `~/.gradle/gradle.properties` or in
  environment variables — never in the committed project `gradle.properties`.
- **Web config** lives in `webApp/.env`, which is gitignored. Only the placeholder
  `webApp/.env.example` is committed.

The OAuth **Web client ID** and the **CloudFront domain** are public by design (they
are sent to browsers and bundled into client apps), so they are safe to expose. Treat
the OAuth **client secret** (if you ever create one) and all **AWS secret keys** as
secrets and keep them out of the repo.

If you fork this project, configure your **own** AWS resources and OAuth client — do
not reuse another deployment's identifiers.
