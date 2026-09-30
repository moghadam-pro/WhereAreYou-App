# Releasing and versioning

Binding for **every** contributor and coding agent (Codex, Claude, Antigravity, humans).

## Versioning — SemVer 2.0.0

`MAJOR.MINOR.PATCH[-prerelease]`, stored **only** in `VERSION_NAME` in `gradle.properties`.

- `versionName` = `VERSION_NAME`.
- `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH` (computed in `app/build.gradle.kts`; e.g. 0.1.1 → 101). It only ever increases; never edit it by hand and never use CI run numbers.
- While `MAJOR` is 0 the app is a beta/pre-release. `1.0.0` requires the Phase 1H exit criteria (`docs/ROADMAP.md`).
- PATCH: bug fix, no behaviour change. MINOR: new feature or SMS-protocol-compatible change. MAJOR: incompatible change (e.g. SMS protocol version bump, data-migration break).
- Changing the SMS protocol version field or the Room schema always needs an explicit note in `CHANGELOG.md`. A Room version bump also needs a `Migration` and the exported `app/schemas/*.json` committed. Never use `fallbackToDestructiveMigration`.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`, `build:`, `ci:`; `!` or `BREAKING CHANGE:` for major. Keep commits small.

## Cutting a release

1. Move the `## [Unreleased]` entries in `CHANGELOG.md` under a new `## [X.Y.Z]` heading (and update the compare links at the bottom).
2. Set `VERSION_NAME=X.Y.Z` in `gradle.properties`.
3. Run `./gradlew :core:test :app:lintRelease :app:assembleRelease` and `npm test` in `tools/decoder`.
4. Commit: `chore(release): vX.Y.Z`.
5. `git tag vX.Y.Z && git push origin main vX.Y.Z`. The `release.yml` workflow verifies tag == `VERSION_NAME`, builds the signed APK, attaches `DadFinder-X.Y.Z.apk` + `.sha256`, and uses the changelog section as release notes. Versions containing `-` or starting with `0.` are marked pre-release.

Never re-tag or overwrite a published version; publish a new PATCH instead.

## Signing secrets — never commit

- Local: `keystore/release.jks` + `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) — both are gitignored.
- CI: repository secrets `RELEASE_KEYSTORE_BASE64` (`base64 -w0 keystore/release.jks`), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- There are no default passwords. Without secrets the release build is **unsigned**, and the release workflow fails rather than publishing.
- Back the keystore up somewhere private: losing it means users must uninstall to update.
- If a key ever leaks, retire it, generate a new one, and say so in `CHANGELOG.md` (users must reinstall).
