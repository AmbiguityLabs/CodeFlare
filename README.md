# CodeFlare

Android IDE for writing, testing and deploying Cloudflare Workers from your phone.
Built with Kotlin and Jetpack Compose.

- Application ID: `com.ambiguitylabs.codeflare` (minSdk 24, arm64 for the bundled Deno binary)
- Code editor with syntax highlighting, snippets, formatting and navigation
- Cloudflare Worker deploy and GitHub integration
- Local Deno-based format and lint (uses a bundled `libdeno.so`)

## Build

Requirements: JDK 17, Android SDK, Gradle 9.3.1.

```bash
echo "APP_ENV=production" > .env     # never commit .env
gradle assembleRelease
```

Release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` (key alias: `upload`).

## CI (GitHub Actions)

- Push to `main` builds a signed APK and publishes it to Releases (keeps the newest 10).
- A parallel, non-blocking job runs unit tests and lint.
- Set these repository secrets for a permanent signing key (otherwise a temporary key is used on every build and updates will not install over older versions): `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`.
- Never commit keystores or `.env`.
