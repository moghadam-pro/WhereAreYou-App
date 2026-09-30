# WhereAreYou / DadFinder (بابایاب)

An **event-driven family safety agent for Android**.

WhereAreYou is not intended to be a continuous family tracker. The protected phone remains mostly idle and reacts to explicit safety-related events such as repeated missed calls from trusted contacts, an authenticated SMS command, or a low-battery event. When a safety session is triggered, the app collects the smallest useful set of device status data and replies primarily over SMS.

The project started from a simple real-world problem: a family member was not answering repeated calls, and there was no lightweight way to know the phone's last/current location, battery level, or connectivity state without relying on a continuously running tracker or an internet service.

## Product principles

1. **Event-driven, not continuously tracking.** No permanent GPS tracking loop and no always-running background service.
2. **SMS-first.** Phase 1 must work without mobile data, Wi-Fi, accounts, push notifications, or a central server.
3. **Transparent and consensual.** The app is visible on the protected phone. Trusted contacts are explicitly configured by the phone owner.
4. **Minimal permissions.** Request only permissions required by enabled features.
5. **Multiple trusted contacts.** A protected person can add several family members or caregivers.
6. **Compact one-segment responses.** SMS payloads are deliberately short and contain no map URL. A decoder reconstructs the readable status and map link locally.
7. **Freshness is explicit.** Never claim a location is current when it is not. Every sample carries a timestamp and accuracy.
8. **No mandatory backend.** Internet transport is deliberately deferred. Future online transports must remain provider-agnostic and optional.
9. **No covert surveillance.** No hidden microphone capture, audio recording, secret calls, or invisible tracking.

## Phase 1 scope

### Protect mode — Android

A minimal visible UI allows the protected user to:

- add/remove trusted contacts;
- configure which safety triggers each trusted contact may use;
- review permissions and app readiness;
- see/cancel an active safety session;
- manually send an "I'm OK" / status response;
- optionally enable emergency callback for selected trusted contacts.

Initial triggers:

- N missed calls from one trusted contact within a configurable time window; calls do not need to be consecutive;
- a configurable number of missed calls from trusted contacts;
- an authenticated SMS command from a trusted contact;
- Android low-battery event;
- manual status/check-in from the Protect UI.

Initial response fields:

- location;
- location accuracy;
- sample timestamp;
- battery percentage;
- charging state;
- usable internet state (yes/no), for information only in Phase 1;
- sample/session sequence metadata.

Primary transport: **SMS only**.

### Decoder — platform-neutral

The person checking on the protected phone may use iPhone or another non-Android device. Phase 1 therefore does **not** require a second native mobile app.

Instead, the repository should include a tiny static, offline-capable decoder/PWA that:

- accepts a compact SMS payload by paste/share;
- decodes it entirely on-device/in-browser;
- displays location, accuracy, timestamp, battery, charging and network status;
- generates a map link locally after decoding;
- can combine several samples from one safety session and highlight the best estimate;
- has no server dependency and sends no telemetry.

## Emergency callback

A trusted contact may request an **Emergency Callback** only if the protected user explicitly enabled that capability for that contact.

The implementation must be a normal Android outgoing phone call through the system telecom stack. It must be visible on the protected device and cancellable. A remote request must never activate the microphone directly, record audio, create a hidden call, hide call UI, or bypass Android's normal call indicators.

Recommended default behavior: **Ask first** or a visible countdown before placing the call. Automatic callback, if implemented, must be an explicit opt-in setting and rate-limited.

## Internet transport

Internet delivery is **out of scope for Phase 1** even when connectivity exists. The app may report whether usable internet is available, but it must not depend on any private server, Firebase project, hosted API, or developer-operated infrastructure.

Phase 2 should define a transport adapter interface so users can optionally choose a self-hosted endpoint or a third-party relay without changing the core safety engine. See `docs/ROADMAP.md`.

## Documentation

- [`AGENTS.md`](AGENTS.md) — implementation instructions for Codex/agents
- [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md) — full product requirements
- [`docs/ANDROID_ARCHITECTURE.md`](docs/ANDROID_ARCHITECTURE.md) — Android architecture and event/state model
- [`docs/SMS_PROTOCOL.md`](docs/SMS_PROTOCOL.md) — compact command/response protocol
- [`docs/SECURITY_PRIVACY.md`](docs/SECURITY_PRIVACY.md) — consent, abuse prevention and threat model
- [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) — functional, reliability and device test matrix
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — phased implementation plan
- [`docs/DISCOVERY_NOTES.md`](docs/DISCOVERY_NOTES.md) — design rationale and conversation-derived decisions
- [`docs/DEVIATIONS.md`](docs/DEVIATIONS.md) — Android/build-environment restrictions that forced a deviation from the spec, and why

## License

WhereAreYou is free and open source under the [Apache License 2.0](LICENSE) — free to use,
study, modify, redistribute and self-build, including commercially, with an explicit patent
grant. There is no paid tier, no account, no telemetry and no developer-operated server;
see the "No mandatory backend" and "No covert surveillance" principles above.

Contributions are welcome. Please read [`AGENTS.md`](AGENTS.md) and the documents under
`docs/` first — the core invariants there (event-driven only, no continuous tracking, no
covert behaviour) are product requirements, not stylistic preferences, and a change that
breaks one of them will not be merged.

## Distribution note

SMS and Call Log permissions are highly restricted on Google Play. Builds are distributed
as open-source test builds through [GitHub Releases](../../releases) and can always be
built from source yourself. Play Store publication needs a separate policy review and may
require a materially different permission strategy.

### Installing a release build / راهنمای نصب و رفع خطای نصب

Releases are signed **Release APKs** (`com.whereareyou.app`) signed with the project release key (kept out of the repository, see `docs/RELEASING.md`) supporting APK Signature Scheme v1, v2, and v3.

#### ۱. دریافت فایل نصبی (Download APK)
* **از گیت‌هاب (GitHub Releases):** می‌توانید فایل APK را مستقیماً از بخش [GitHub Releases](../../releases) دریافت کنید.

#### ۲. مراحل نصب صحیح روی گوشی و رفع خطای `App not installed`
اگر در هنگام نصب با اخطار Google Play Protect یا پیام **"App not installed"** مواجه شدید، به ترتیب زیر عمل کنید:

1. **حذف نسخه قبلی (Uninstall Previous Version):**
   اگر از قبل نسخه‌ای از برنامه (به‌ویژه ۰٫۱٫۰ که با کلید قدیمی و لو‌رفته امضا شده بود) روی گوشی نصب است، حتماً ابتدا آن را حذف (Uninstall) کنید تا خطای تداخل امضا (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) رخ ندهد.
2. **عبور صحیح از پنجره اخطار Google Play Protect:**
   به دلیل نصب فایل خارج از گوگل‌پلی (Sideload) و داشتن دسترسی‌های حساس پیامک و تماس و موقعیت مکانی، اخطار زیر نمایش داده می‌شود:
   > *"App blocked to protect your device — Play Protect hasn't seen an app from this developer before"*
   
   در این پنجره:
   * ❌ **به هیچ وجه روی دکمه آبی `Got it` (متوجه شدم) کلیک نکنید!** این دکمه پیش‌فرض گوگل برای لغو نصب است و با زدن آن، خطای *"App not installed"* ظاهر می‌شود.
   * ✅ **روی گزینه متنی `Install anyway` (نصب در هر حال) کلیک کنید** تا نصب تکمیل شود.
3. **بررسی قابلیت Auto Blocker در گوشی‌های سامسونگ (Samsung One UI 6+):**
   اگر از گوشی سامسونگ استفاده می‌کنید و برنامه باز هم نصب نشد، موقتاً مسدودکننده خودکار را خاموش کنید:
   * **تنظیمات (Settings) ➔ امنیت و حریم خصوصی (Security and privacy) ➔ مسدودکننده خودکار (Auto Blocker) ➔ خاموش (Off)**
4. **تغییرات فنی انجام‌شده در نسخه Release:**
   * پورت‌های دیباگ بسته شده‌اند (`debuggable = false`) تا توسط اسکنرهای امنیتی بلاک نشود.
   * برنامه با کلید پایدار پروژه (خارج از مخزن) و هر سه طرح امضای استاندارد اندروید (v1 JAR + v2 Full APK + v3) امضا شده است.
   * وابستگی‌های سخت‌افزاری (`telephony` و `location`) اختیاری (`android:required="false"`) شده‌اند تا ناسازگاری دستگاهی ایجاد نشود.
   * حجم برنامه بهینه شده و به **۷.۹ مگابایت** کاهش یافته است.

## Recent Updates (Phase 1A/1B+ Readiness Milestone)

- **Runtime Permissions & Interactive Readiness Flow**:
  - Declared all required platform permissions in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml): SMS (`RECEIVE_SMS`, `SEND_SMS`), Call Log & Phone State (`READ_CALL_LOG`, `READ_PHONE_STATE`), Fine & Background Location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`), and Notifications (`POST_NOTIFICATIONS`).
  - Implemented automatic runtime permission prompt on app launch whenever core safety permissions are missing.
  - Upgraded [`PermissionsScreen`](app/src/main/kotlin/com/whereareyou/app/permissions/PermissionsScreen.kt) to live-evaluate permission states, provide per-permission and batch "Grant" actions, and offer direct access to App Settings.
  - Added a reactive readiness banner to [`HomeScreen`](app/src/main/kotlin/com/whereareyou/app/ui/HomeScreen.kt) with real-time status badges and instant grant triggers.
- **Bilingual Localization (English & Persian)**:
  - English default (`res/values/strings.xml`): Application name is **DadFinder** with complete English explanations.
  - Persian locale (`res/values-fa/strings.xml`): Application name is **بابایاب** with fully localized UI and permission rationales.
- **Modern Adaptive Launcher Icons**:
  - Generated standard Android Adaptive Icons (`mipmap-anydpi-v26`) with a smooth blue gradient background and safe-zone centered character foreground.
  - Generated legacy and circular mipmap icons across all standard densities (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`).

## Status

**Phase 1A/1B implemented with active permission readiness.**

- `:core` — pure Kotlin/JVM domain layer: trusted-contact + capability model, missed-call
  trigger rules (per-contact and aggregate), the `TriggerEngine`, SMS command
  authentication/replay/rate-limiting, the status + command SMS protocol codec, and the
  `SafetySession` state machine. 120 unit tests, all passing.
- `:app` — Android/Kotlin/Jetpack Compose UI: Protect home screen, trusted-contact
  CRUD with per-contact capability toggles, Room + DataStore persistence, dynamic permission
  readiness and interactive runtime permission management. Builds to an installable debug APK
  (`com.whereareyou.app.debug`, minSdk 26, targetSdk 35) via `./gradlew :app:assembleDebug`.
- `tools/decoder/` — offline static decoder/PWA, dependency-free, with its own test suite
  sharing vectors with `:core`'s protocol tests.

Start by reading `AGENTS.md` and the documents under `docs/` before writing production code.
Phase 1C+ (real SMS/call-log/telecom/background-location integration) will wire platform event
receivers into the tested `TriggerEngine`.
