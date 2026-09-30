# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) · Versioning: [SemVer 2.0.0](https://semver.org/) — see `docs/RELEASING.md`.

## [Unreleased]

## [0.1.1]

### Security
- Release signing key is no longer stored in the repository and has no default password. **The previous (0.1.0) key was public and is retired; 0.1.1+ is signed with a new key, so 0.1.0 must be uninstalled before installing.**
- SMS auth code is compared in constant time; repeated wrong codes now lock a contact out (10 per hour).

### Fixed
- Aggregate missed-call rule: an answered call now resets only that contact's misses instead of every contact's.
- Missed-call counters no longer grow without bound.
- Phone numbers: `0098…` international prefix and country-code-without-`+` are normalized correctly; ambiguous foreign bare digits are rejected.

### Changed
- Rule state (missed-call counters, replay guard, rate limits) can be persisted and restored, so it survives process death.
- Release build is minified/shrunk; Room schemas are exported for reviewed migrations.
- Dependencies moved to a Gradle version catalog; version comes from `VERSION_NAME` in `gradle.properties`.
- CI split into `ci.yml` (tests + build on every push/PR) and tag-triggered `release.yml`.

## [0.1.0]

- Initial beta: Protect UI, trusted contacts, permission readiness, `:core` domain layer, static decoder.

[Unreleased]: https://github.com/moghadam-pro/WhereAreYou-App/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/moghadam-pro/WhereAreYou-App/compare/v0.1.0-beta.28...v0.1.1
[0.1.0]: https://github.com/moghadam-pro/WhereAreYou-App/releases/tag/v0.1.0-beta.28
