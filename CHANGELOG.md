# Changelog

All notable changes to TianshangGuard are documented in this file.

## [1.5.0] - 2026-07-26

### Added
- **SMS Model v5**: 100% real data training (FBS + mudou_spam for phishing, mudou_ham for legitimate), 8,848 samples, 30 epochs
- **RiskLevel threshold calibration**: SAFE < 0.30, SUSPICIOUS 0.30–0.59, DANGEROUS ≥ 0.59 (AUC=0.9672 on v5 validation)
- **QuishGuard QR scanner**: Built-in CameraX + ZXing QR code scanner with real-time URL risk analysis
- **Web3Guard**: ENS `.eth`, Unstoppable `.crypto`, SID `.bnb` blockchain domain detection
- **Quick Settings Tile**: One-tap QR scanner access from notification shade
- **SQLCipher database encryption**: Encrypted local storage with Android Keystore protection
- **DNS over HTTPS (DoH)**: Cloudflare DoH with UDP fallback
- **SHA-256 rule integrity verification**: Rule update signature verification
- **BPE tokenizer deployment**: Vocabulary-based subword tokenizer with ByteTokenizer fallback
- **24-dimensional feature extraction + feature-based predictor**
- **Feedback engine + BM25 knowledge base**: User feedback integrated with adaptive detection
- **Camera + FOREGROUND_SERVICE_CAMERA permissions** (Android 14+)
- **Database DAO suspend migration + v1→v4 migration strategy**

### Fixed
- 26 security audit bugs fixed (12 Critical + 14 High) out of 59 identified — **v1.2.2 full code audit (2026-06-28)**, a separate audit from the 2026-08 security audit (20 findings, see below)
- CIPHER_HOOK alignment across all SQLCipher database connections
- SQLCipher "file is not a database" error from test/production hook mismatch
- Database migration engine: replaced `sqlcipher_export()` with read-via-Android-SQLite + write-via-Room-DAOs
- Tamper detection test: `withTimeout(5000)` to prevent SQLCipher native hangs
- Test isolation: UUID-unique database names across tests
- CI/CD: Gradle OOM fixes (heap 4g, per-flavor runners), keystore decode pipeline
- CI/CD: keystore.properties generation on CI (gitignored file missing on runner)
- CI/CD: trailing newline in gradle.properties so heap override works correctly
- CI/CD: printf+tempfile for keystore decode to avoid echo truncation

### Changed
- SMS model retrained from 10 to 30 epochs, switched from synthetic+v4 to v5 real data
- RiskLevel enum thresholds: SAFE 0.50→0.30, SUSPICIOUS 0.90→0.59, DANGEROUS unchanged at 1.0
- `export_and_calibrate.py` supports `CALIBRATE_MODE` env var for SMS calibration
- Japanese mode removed from training script

### Security
- SQLCipher v4.5.4 with AES-GCM key encryption via Android Keystore
- Automatic plaintext-to-encrypted database migration
- All ML inference on-device, zero data upload
- DNS over HTTPS with certificate pinning
- SHA-256 signature verification for rule updates

### Security Remediation (2026-08-28 audit)

Full-source security audit (2026-08-28) — 20 findings (3 Critical / 7 High / 6 Medium / 4 Low), **all remediated in `main`**:

- **C-01** — Keyless SHA-256 rule "signature" (zero authenticity) → **Ed25519 public-key verification** (embedded public key, canonical sorted payload, timestamp + replay protection)
- **C-02** — DoH forced plaintext-UDP downgrade → **fail-closed** multi-endpoint DoH (Cloudflare + AliDNS), SERVFAIL on total failure, DNS response validation (ID + QR + RCODE + question-section byte match)
- **C-03** — SQLCipher silent plaintext fallback → **fail-closed** volatile in-memory Room DB + UI warning (`secureStorageAvailable`)
- **H-01** — SMS analysis failed open when the SMS model was missing → gated on `ModelType.SMS` readiness, falls back to the rule engine
- **H-02** — VPN single-threaded DNS + `runBlocking` DoS → bounded async resolution pool (8) + per-source token-bucket rate limit + global in-flight cap (32) + single writer lock
- **H-03** — Rule updates never refreshed the in-memory Bloom filter → `LocalDnsEngine.reloadFilter()` wired into `RuleUpdateInteractor`; fixed `SettingsViewModel.checkRuleUpdates()` to call the interactor
- **H-04** — Keystore key not hardware-bound + passphrase in SharedPreferences → **StrongBox** (TEE fallback) + `derivePassphrase()` (no SharedPreferences middle layer) + WAL checkpoint + secure erase on migration
- **H-05** — Single static cert pin → per-host **multi-SPKI pin set** (raw.githubusercontent.com / api.github.com), verified against the live chain; DoH dual-pin
- **H-06** — Release unminified/unshrunk with lint off → **R8 minify + resource shrink + release lint** enabled, `proguard-rules.pro`
- **H-07** — CI supply chain (tag-pinned actions, default perms, keystore in runner) → actions pinned to full commit SHAs, least-privilege `permissions`, keystore decoded outside the repo + `chmod 600` + always cleanup
- **M-01** — Screen-share detection bypassable → UID→package ownership verification + API 34+ MediaProjection active-session signal (additive)
- **M-02** — Web3 detection matched only `endsWith` → registrable-domain parsing (`legit.com.evil.eth` → `evil.eth`)
- **M-03** — Homograph "one character blocks everything" → brand-aware `assess()`: block only brand-similar + ≥2 confusable chars; non-brand IDN domains → needs-confirmation
- **M-04** — Feedback whitelist MD5 collision + global alert limiter → **SHA-256** hash; **DANGEROUS exempt** from global 5s limiter (per-key cooldown retained); SMS cooldown key SHA-256
- **M-05** — Training pipeline unsafe deserialization + unauthenticated server → `torch.load(weights_only=True)`; `training_server.py` binds 127.0.0.1 + optional `GUARD_TRAIN_TOKEN` auth
- **M-06** — Gradle wrapper no SHA-256 + backup config leak → wrapper `distributionSha256Sum`; backup rules exclude `guard_db_prefs.xml`
- **L-01** — Plaintext 30s DNS keepalive → **DoH-only** keepalive (no plaintext UDP probe)
- **L-02** — All visited domains retained → user toggle (default on) + opportunistic 30-day purge
- **L-03** — Dead `PhishTankApi` → removed
- **L-04** — `normalizeUrl` global `replace("http://","https://")` bug → URI-component normalization

See also `SECURITY.md` for the full audit write-up and historical audit records (AGENTS.md §10.1).

---

## [1.4.2] - 2026-07-03

### Added
- Manual test report generation
- SMS test set (40 cases, Chinese + English)
- Training report v1.4.2

### Fixed
- SQLCipher native crash on startup (`loadLibs()` fix)
- Various model calibration issues

---

## [1.4.1] - 2026-06-30

### Fixed
- Battery optimization for Huawei, Xiaomi, OPPO, vivo, Meizu, Samsung, Honor
- Brand-specific battery/autostart settings
- Bug fixes from v1.4.0 alpha testing

---

## [1.4.0] - 2026-06-20

### Added
- Feature-based prediction pipeline (24-dim feature extraction + prediction)
- BM25 knowledge base retrieval for anti-fraud education
- Feedback engine with n-gram token matching
- Threshold calibrator with momentum-based adaptation

---

## [1.3.2] - 2026-06-15

### Fixed
- ONNX model loading timeout handling
- MlEngineWithFallback resource cleanup
- PerformanceTracer thread safety (ConcurrentHashMap)

---

## [1.3.1] - 2026-06-10

### Fixed
- BPE tokenizer ByteTokenizer fallback logic
- ONNX inference session resource leaks
- DNS cache thread safety

---

## [1.3.0-alpha] - 2026-06-01

### Added
- SQLCipher database encryption (encrypted database provider)
- DNS over HTTPS client with UDP fallback
- SHA-256 rule update integrity verification
- BPE subword tokenizer

---

## [1.2.2] - 2026-05-28

### Fixed
- 59 security audit bugs identified and triaged — **v1.2.2 full code audit (2026-06-28)**; 26 fixed (P0/P1), 33 P2 deferred
- BertTokenizer byte truncation fix
- ONNX resource leak fix
- RiskLevel.toScore() midpoint correction
- VPN handlerThread lifecycle fix
- DNS cache synchronization
- IPv6 response offset fix
- Homograph detection deduplication
- UI thread safety improvements
- SmsViewModel IO scheduler fix
- SettingsScreen toggle deduplication

---

## [1.2.1] - 2026-05-15

### Added
- Homograph detection improvements (Cyrillic, Greek, Fullwidth, Armenian)
- Adaptive Bloom Filter for DNS blacklist
- Levenshtein distance based domain similarity detection (BK-tree)

### Fixed
- DNS engine memory optimization
- Alert cooldown rate limiting

---

## [1.2.0] - 2026-05-01

### Added
- Behavior monitoring: screen sharing + banking app detection
- Tiered alert system (silent / banner / dialog / fullscreen)
- CooldownManager for alert rate limiting
- RemoteConfigProvider for app configuration

---

## [1.1.0-chinese] - 2026-04-15

### Added
- Chinese UI flavor with Chinese SMS model
- Initial ONNX Runtime integration
- Basic DNS phishing domain blocking
- Bloom Filter based blacklist
- First open-source release
